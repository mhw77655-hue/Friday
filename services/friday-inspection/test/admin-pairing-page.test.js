import test from "node:test";
import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { createServer } from "node:net";
import { mkdtemp, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { fileURLToPath } from "node:url";
import { setTimeout as delay } from "node:timers/promises";

const serviceRoot = fileURLToPath(new URL("..", import.meta.url));

async function freePort() {
  const server = createServer();
  await new Promise((resolve, reject) => {
    server.once("error", reject);
    server.listen(0, "127.0.0.1", resolve);
  });
  const address = server.address();
  const port = address.port;
  await new Promise((resolve, reject) => server.close(error => error ? reject(error) : resolve()));
  return port;
}

async function waitForHealth(url, child) {
  for (let attempt = 0; attempt < 60; attempt += 1) {
    if (child.exitCode !== null) throw new Error("Inspection service exited before becoming healthy.");
    try {
      const response = await fetch(url);
      if (response.ok) return;
    } catch {
      // The child is still starting.
    }
    await delay(50);
  }
  throw new Error("Inspection service did not become healthy in time.");
}

test("owner setup pages keep the owner allowlist private and enforce server-side authorization", async t => {
  const port = await freePort();
  const directory = await mkdtemp(join(tmpdir(), "friday-owner-setup-"));
  const child = spawn(process.execPath, ["src/server.js"], {
    cwd: serviceRoot,
    env: {
      ...process.env,
      NODE_ENV: "test",
      PORT: String(port),
      PUBLIC_HOST: "",
      SUPABASE_URL: "https://example.supabase.co",
      SUPABASE_PUBLISHABLE_KEY: "test-publishable-key",
      FRIDAY_OWNER_EMAIL: "owner@example.com",
      FRIDAY_STATE_PATH: join(directory, "state.json")
    },
    stdio: ["ignore", "ignore", "pipe"]
  });

  t.after(async () => {
    child.kill("SIGTERM");
    await rm(directory, { recursive: true, force: true });
  });

  const base = `http://127.0.0.1:${port}`;
  await waitForHealth(base + "/health", child);

  await t.test("OAuth consent page does not publish the configured owner email", async () => {
    const response = await fetch(base + "/oauth/consent");
    assert.equal(response.status, 200);
    const html = await response.text();
    assert.doesNotMatch(html, /owner@example\.com/i);
    assert.doesNotMatch(html, /__OWNER_EMAIL__/);
    assert.match(html, /\/owner\/status/);
  });

  await t.test("owner pairing page is served without exposing the owner email", async () => {
    const response = await fetch(base + "/admin/pairing");
    assert.equal(response.status, 200);
    const html = await response.text();
    assert.doesNotMatch(html, /owner@example\.com/i);
    assert.match(html, /Generate one-time pairing code/i);
    assert.match(html, /\/admin\/pairing-code/);
  });

  await t.test("owner status and pairing-code endpoints reject unauthenticated callers", async () => {
    const status = await fetch(base + "/owner/status");
    assert.equal(status.status, 401);
    assert.equal((await status.json()).error, "oauth_required");

    const pairing = await fetch(base + "/admin/pairing-code", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: "{}"
    });
    assert.equal(pairing.status, 401);
    assert.equal((await pairing.json()).error, "oauth_required");
  });
});
