import http from "node:http";
import { randomBytes, randomUUID } from "node:crypto";
import { readFile, writeFile, mkdir } from "node:fs/promises";
import { dirname } from "node:path";
import { WebSocketServer } from "ws";
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StreamableHTTPServerTransport } from "@modelcontextprotocol/sdk/server/streamableHttp.js";
import {
  constantTimeEqual,
  isFresh,
  validateInspectionSnapshot,
  verifyDeviceProof
} from "./security.js";

const PORT = Number(process.env.PORT || 8080);
const PUBLIC_HOST = process.env.PUBLIC_HOST || "";
const MCP_TOKEN = process.env.FRIDAY_MCP_TOKEN || "";
const ADMIN_TOKEN = process.env.FRIDAY_ADMIN_TOKEN || "";
const STATE_PATH = process.env.FRIDAY_STATE_PATH || "/data/state.json";
const MAX_BODY_BYTES = 32 * 1024;
const DEVICE_AUTH_WINDOW_MS = 60_000;
const SNAPSHOT_TIMEOUT_MS = 10_000;

if (process.env.NODE_ENV === "production") {
  if (!PUBLIC_HOST || !MCP_TOKEN || MCP_TOKEN.length < 32 ||
      !ADMIN_TOKEN || ADMIN_TOKEN.length < 32) {
    throw new Error("Production requires PUBLIC_HOST and independent 32+ character MCP/admin tokens.");
  }
}

const state = await loadState();
const connectedDevices = new Map();
const pendingSnapshots = new Map();

async function loadState() {
  try {
    const parsed = JSON.parse(await readFile(STATE_PATH, "utf8"));
    return {
      devices: parsed.devices && typeof parsed.devices === "object" ? parsed.devices : {},
      pairing: parsed.pairing ?? null,
      consumedPairingCodes: Array.isArray(parsed.consumedPairingCodes) ? parsed.consumedPairingCodes : []
    };
  } catch {
    return { devices: {}, pairing: null, consumedPairingCodes: [] };
  }
}

let persistChain = Promise.resolve();
function persistState() {
  persistChain = persistChain.then(async () => {
    await mkdir(dirname(STATE_PATH), { recursive: true });
    const temp = STATE_PATH + ".tmp";
    await writeFile(temp, JSON.stringify(state), { mode: 0o600 });
    const { rename } = await import("node:fs/promises");
    await rename(temp, STATE_PATH);
  });
  return persistChain;
}

function bearerMatches(request, expected) {
  const header = request.headers.authorization || "";
  const match = /^Bearer ([A-Za-z0-9._~-]{32,512})$/.exec(header);
  return !!match && constantTimeEqual(match[1], expected);
}

function hostAllowed(request) {
  if (!PUBLIC_HOST) return process.env.NODE_ENV !== "production";
  return request.headers.host?.toLowerCase() === PUBLIC_HOST.toLowerCase();
}

function sendJson(response, status, value) {
  const body = JSON.stringify(value);
  response.writeHead(status, {
    "content-type": "application/json; charset=utf-8",
    "content-length": Buffer.byteLength(body),
    "cache-control": "no-store",
    "x-content-type-options": "nosniff"
  });
  response.end(body);
}

async function readJson(request) {
  const chunks = [];
  let size = 0;
  for await (const chunk of request) {
    size += chunk.length;
    if (size > MAX_BODY_BYTES) throw new Error("request_too_large");
    chunks.push(chunk);
  }
  return JSON.parse(Buffer.concat(chunks).toString("utf8"));
}

function adminOnly(request, response) {
  if (!ADMIN_TOKEN || !bearerMatches(request, ADMIN_TOKEN)) {
    sendJson(response, 401, { error: "unauthorized" });
    return false;
  }
  return true;
}

function publicDeviceStatus(deviceId) {
  const registration = state.devices[deviceId];
  const connection = connectedDevices.get(deviceId);
  if (!registration || registration.revoked || !connection) {
    return { connected: false, enrolled: !!registration && !registration.revoked, revoked: !!registration?.revoked };
  }
  const snapshot = connection.latestSnapshot;
  return {
    connected: connection.socket.readyState === 1,
    enrolled: true,
    revoked: false,
    last_seen_at_epoch_ms: connection.lastSeenAt,
    snapshot_fresh: !!snapshot && isFresh(snapshot.captured_at_epoch_ms),
    source_revision: snapshot?.source_revision ?? null,
    app_version_name: snapshot?.app_version_name ?? null,
    snapshot_age_ms: snapshot ? Math.max(0, Date.now() - snapshot.captured_at_epoch_ms) : null
  };
}

function requestSnapshot(deviceId) {
  const connection = connectedDevices.get(deviceId);
  if (!connection || connection.socket.readyState !== 1) {
    throw new Error("Friday device is offline or inspection is not enabled.");
  }
  const requestId = randomUUID();
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => {
      pendingSnapshots.delete(requestId);
      reject(new Error("Friday did not return a live snapshot before timeout."));
    }, SNAPSHOT_TIMEOUT_MS);
    pendingSnapshots.set(requestId, {
      deviceId,
      resolve: snapshot => { clearTimeout(timer); resolve(snapshot); },
      reject: error => { clearTimeout(timer); reject(error); }
    });
    connection.socket.send(JSON.stringify({ type: "snapshot_request", requestId }));
  });
}

function createMcpServer() {
  const mcp = new McpServer({ name: "friday-live-inspection", version: "1.0.0" });
  mcp.registerTool("friday_live_status", {
    description: "Read whether the enrolled Friday phone is currently connected and whether its last allowlisted snapshot is fresh. This tool never operates the phone.",
    inputSchema: {}
  }, async () => {
    const deviceId = Object.keys(state.devices)[0];
    const result = deviceId ? (state.devices[deviceId].revoked
      ? { connected: false, enrolled: false, revoked: true }
      : publicDeviceStatus(deviceId)) : {
      connected: false, enrolled: false, revoked: false
    };
    return { content: [{ type: "text", text: JSON.stringify(result) }] };
  });

  mcp.registerTool("friday_inspection_snapshot", {
    description: "Request a fresh, privacy-minimised, read-only snapshot from the currently enrolled Friday phone. Fails if the phone is offline, the session is revoked, or fresh evidence is unavailable.",
    inputSchema: {}
  }, async () => {
    const deviceId = Object.keys(state.devices).find(id => !state.devices[id].revoked);
    if (!deviceId) return {
      isError: true,
      content: [{ type: "text", text: "No enrolled Friday device is available." }]
    };
    try {
      const snapshot = await requestSnapshot(deviceId);
      return { content: [{ type: "text", text: JSON.stringify(snapshot) }] };
    } catch (error) {
      return {
        isError: true,
        content: [{ type: "text", text: error instanceof Error ? error.message : "Live inspection unavailable." }]
      };
    }
  });
  return mcp;
}

const httpServer = http.createServer(async (request, response) => {
  try {
    const url = new URL(request.url || "/", "https://" + (request.headers.host || "localhost"));
    if (url.pathname !== "/health" && !hostAllowed(request)) {
      return sendJson(response, 421, { error: "host_not_allowed" });
    }

    if (request.method === "GET" && url.pathname === "/health") {
      return sendJson(response, 200, { service: "friday-live-inspection", status: "ok" });
    }

    if (request.method === "POST" && url.pathname === "/admin/pairing-code") {
      if (!adminOnly(request, response)) return;
      const code = randomBytes(18).toString("base64url");
      state.pairing = { code, expiresAt: Date.now() + 10 * 60_000, consumed: false };
      await persistState();
      return sendJson(response, 200, { pairing_code: code, expires_at_epoch_ms: state.pairing.expiresAt });
    }

    if (request.method === "POST" && url.pathname === "/device/enroll") {
      const body = await readJson(request);
      const pairing = state.pairing;
      if (!pairing || pairing.consumed || pairing.expiresAt < Date.now() ||
          !constantTimeEqual(String(body.pairingCode || ""), pairing.code)) {
        return sendJson(response, 403, { error: "pairing_code_invalid_or_expired" });
      }
      const deviceId = String(body.deviceId || "");
      const publicKey = String(body.publicKeyDerBase64 || "");
      if (!/^[0-9a-f-]{36}$/i.test(deviceId) || publicKey.length > 4096) {
        return sendJson(response, 400, { error: "invalid_device_identity" });
      }
      try {
        const { createPublicKey } = await import("node:crypto");
        const parsed = createPublicKey({ key: Buffer.from(publicKey, "base64"), format: "der", type: "spki" });
        if (parsed.asymmetricKeyType !== "ec" ||
            parsed.asymmetricKeyDetails?.namedCurve !== "prime256v1") throw new Error("wrong_key");
      } catch {
        return sendJson(response, 400, { error: "invalid_device_public_key" });
      }
      if (state.devices[deviceId] && !state.devices[deviceId].revoked) {
        return sendJson(response, 409, { error: "device_already_enrolled" });
      }
      state.devices[deviceId] = { publicKeyDerBase64: publicKey, enrolledAt: Date.now(), revoked: false };
      pairing.consumed = true;
      await persistState();
      return sendJson(response, 201, { device_id: deviceId, enrolled: true });
    }

    if (request.method === "POST" && url.pathname === "/device/revoke") {
      const body = await readJson(request);
      const deviceId = String(body.deviceId || "");
      const timestamp = Number(body.timestamp);
      const registration = state.devices[deviceId];
      if (!registration || registration.revoked || !Number.isSafeInteger(timestamp) ||
          Math.abs(Date.now() - timestamp) > DEVICE_AUTH_WINDOW_MS) {
        return sendJson(response, 403, { error: "device_revoke_not_authorized" });
      }
      const signed = "friday-inspection-revoke-v1\\n" + deviceId + "\\n" + timestamp;
      if (!verifyDeviceProof(registration.publicKeyDerBase64, signed, String(body.signature || ""))) {
        return sendJson(response, 403, { error: "device_revoke_proof_invalid" });
      }
      registration.revoked = true;
      registration.revokedAt = Date.now();
      const connection = connectedDevices.get(deviceId);
      if (connection) {
        connection.socket.send(JSON.stringify({ type: "revoked" }));
        connection.socket.close(4001, "revoked");
        connectedDevices.delete(deviceId);
      }
      await persistState();
      return sendJson(response, 200, { device_id: deviceId, revoked: true });
    }
    if (request.method === "POST" && url.pathname === "/admin/revoke") {
      if (!adminOnly(request, response)) return;
      const body = await readJson(request);
      const deviceId = String(body.deviceId || "");
      if (!state.devices[deviceId]) return sendJson(response, 404, { error: "device_not_found" });
      state.devices[deviceId].revoked = true;
      state.devices[deviceId].revokedAt = Date.now();
      const connection = connectedDevices.get(deviceId);
      if (connection) {
        connection.socket.close(4001, "revoked");
        connectedDevices.delete(deviceId);
      }
      await persistState();
      return sendJson(response, 200, { device_id: deviceId, revoked: true });
    }

    if (request.method === "POST" && url.pathname === "/mcp") {
      if (!MCP_TOKEN || !bearerMatches(request, MCP_TOKEN)) {
        response.setHeader("www-authenticate", 'Bearer realm="Friday inspection"');
        return sendJson(response, 401, { error: "unauthorized" });
      }
      const body = await readJson(request);
      const mcp = createMcpServer();
      const transport = new StreamableHTTPServerTransport({ sessionIdGenerator: undefined });
      await mcp.connect(transport);
      response.on("close", () => { void transport.close(); void mcp.close(); });
      return await transport.handleRequest(request, response, body);
    }

    if (request.method === "GET" && url.pathname === "/mcp") {
      return sendJson(response, 405, { error: "method_not_allowed" });
    }
    return sendJson(response, 404, { error: "not_found" });
  } catch (error) {
    const status = error instanceof SyntaxError ? 400 :
      error?.message === "request_too_large" ? 413 : 500;
    return sendJson(response, status, { error: status === 500 ? "internal_error" : "invalid_request" });
  }
});

const websocketServer = new WebSocketServer({ noServer: true, maxPayload: 16 * 1024 });
httpServer.on("upgrade", (request, socket, head) => {
  if (!hostAllowed(request) || new URL(request.url || "/", "https://" + (request.headers.host || "localhost")).pathname !== "/device") {
    socket.destroy();
    return;
  }
  websocketServer.handleUpgrade(request, socket, head, ws => websocketServer.emit("connection", ws, request));
});

websocketServer.on("connection", socket => {
  const nonce = randomBytes(32).toString("base64url");
  socket.send(JSON.stringify({ type: "challenge", nonce, serverTime: Date.now() }));
  let authenticatedDeviceId = null;
  const authTimer = setTimeout(() => socket.close(4003, "authentication_timeout"), 15_000);

  socket.on("message", async raw => {
    let message;
    try { message = JSON.parse(raw.toString("utf8")); } catch { socket.close(4002, "invalid_message"); return; }

    if (!authenticatedDeviceId) {
      if (message.type !== "authenticate") { socket.close(4003, "authentication_required"); return; }
      const deviceId = String(message.deviceId || "");
      const timestamp = Number(message.timestamp);
      const registration = state.devices[deviceId];
      if (!registration || registration.revoked || !Number.isSafeInteger(timestamp) ||
          Math.abs(Date.now() - timestamp) > DEVICE_AUTH_WINDOW_MS) {
        socket.close(4003, "device_not_authorized");
        return;
      }
      const signed = "friday-inspection-v1\n" + nonce + "\n" + deviceId + "\n" + timestamp;
      if (!verifyDeviceProof(registration.publicKeyDerBase64, signed, String(message.signature || ""))) {
        socket.close(4003, "device_proof_invalid");
        return;
      }
      clearTimeout(authTimer);
      authenticatedDeviceId = deviceId;
      const old = connectedDevices.get(deviceId);
      if (old) old.socket.close(4004, "replaced_by_new_session");
      connectedDevices.set(deviceId, { socket, lastSeenAt: Date.now(), latestSnapshot: null });
      socket.send(JSON.stringify({ type: "authenticated", deviceId }));
      return;
    }

    const connection = connectedDevices.get(authenticatedDeviceId);
    if (!connection || connection.socket !== socket) return;
    connection.lastSeenAt = Date.now();

    if (message.type === "revoke") {
      const registration = state.devices[authenticatedDeviceId];
      if (registration) {
        registration.revoked = true;
        registration.revokedAt = Date.now();
        await persistState();
      }
      socket.send(JSON.stringify({ type: "revoked" }));
      socket.close(4001, "revoked");
      connectedDevices.delete(authenticatedDeviceId);
      return;
    }

    if (message.type === "snapshot" && typeof message.requestId === "string") {
      const pending = pendingSnapshots.get(message.requestId);
      if (!pending || pending.deviceId !== authenticatedDeviceId) return;
      try {
        const snapshot = validateInspectionSnapshot(message.snapshot);
        connection.latestSnapshot = snapshot;
        pendingSnapshots.delete(message.requestId);
        pending.resolve(snapshot);
      } catch {
        pendingSnapshots.delete(message.requestId);
        pending.reject(new Error("Friday returned invalid or stale inspection evidence."));
      }
    }
  });

  socket.on("close", () => {
    clearTimeout(authTimer);
    if (authenticatedDeviceId) {
      const current = connectedDevices.get(authenticatedDeviceId);
      const ownedSession = current?.socket === socket;
      if (ownedSession) {
        connectedDevices.delete(authenticatedDeviceId);
        for (const [requestId, pending] of pendingSnapshots) {
          if (pending.deviceId === authenticatedDeviceId) {
            pendingSnapshots.delete(requestId);
            pending.reject(new Error("Friday inspection session disconnected."));
          }
        }
      }
    }
  });
});

httpServer.listen(PORT, "0.0.0.0", () => {
  console.log("Friday live-inspection service listening on port " + PORT);
});
