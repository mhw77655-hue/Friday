import test from "node:test";
import assert from "node:assert/strict";
import { generateKeyPairSync, sign } from "node:crypto";
import {
  constantTimeEqual,
  isFresh,
  validateInspectionSnapshot,
  verifyDeviceProof,
  validateOwnerClaims
} from "../src/security.js";

const now = 1_800_000_000_000;
const valid = () => ({
  schema: "friday-live-inspection-v1",
  captured_at_epoch_ms: now,
  device_capture_at_epoch_ms: now,
  source_revision: "a".repeat(40),
  app_version_name: "1.2.3",
  app_version_code: 123,
  android_api: 35,
  network: "ready",
  battery: "unknown",
  shizuku: "ready",
  microphone_permission: "ready",
  speech_recognition: "unknown",
  voice_runtime: "ready",
  model_runtime: "unavailable",
  available_memory_bytes: 1024,
  available_storage_bytes: 2048,
  event_count: 3,
  failure_count: 1,
  privacy: {
    conversation_content_included: false,
    transcripts_or_audio_included: false,
    screen_or_notification_content_included: false,
    personal_files_or_memory_included: false,
    credentials_or_tokens_included: false,
    raw_events_or_failure_messages_included: false
  }
});

test("accepts only the exact allowlisted fresh inspection projection", () => {
  const result = validateInspectionSnapshot(valid(), now);
  assert.equal(result.schema, "friday-live-inspection-v1");
  assert.equal(result.source_revision, "a".repeat(40));
  assert.equal(result.failure_count, 1);
  assert.equal(Object.keys(result).length, 19);
  assert.equal(result.privacy.credentials_or_tokens_included, false);
});

test("rejects unknown fields rather than forwarding arbitrary device data", () => {
  const input = { ...valid(), conversation: "private transcript" };
  assert.throws(() => validateInspectionSnapshot(input, now), /unapproved fields/);
});

test("rejects stale snapshots and future timestamps", () => {
  assert.throws(() => validateInspectionSnapshot({
    ...valid(), captured_at_epoch_ms: now - 91_000, device_capture_at_epoch_ms: now - 91_000
  }, now), /stale/);
  assert.throws(() => validateInspectionSnapshot({
    ...valid(), captured_at_epoch_ms: now + 31_000
  }, now), /timestamp/);
});

test("rejects non-closed status values and unsafe revision strings", () => {
  assert.throws(() => validateInspectionSnapshot({ ...valid(), network: "connected to private SSID" }, now), /status/);
  assert.throws(() => validateInspectionSnapshot({ ...valid(), source_revision: "main\nBearer token" }, now), /revision/);
});

test("requires every privacy exclusion to be explicitly false", () => {
  const input = valid();
  input.privacy.conversation_content_included = true;
  assert.throws(() => validateInspectionSnapshot(input, now), /privacy declaration/);
});

test("compares bearer credentials without early character comparison", () => {
  assert.equal(constantTimeEqual("a".repeat(40), "a".repeat(40)), true);
  assert.equal(constantTimeEqual("a".repeat(40), "b".repeat(40)), false);
  assert.equal(constantTimeEqual("short", "longer"), false);
});

test("verifies a P-256 device signature bound to nonce, identity and timestamp", () => {
  const { publicKey, privateKey } = generateKeyPairSync("ec", { namedCurve: "prime256v1" });
  const message = "friday-inspection-v1\nnonce\ndevice-id\n123";
  const signature = sign("sha256", Buffer.from(message), privateKey).toString("base64");
  const publicKeyDerBase64 = publicKey.export({ format: "der", type: "spki" }).toString("base64");
  assert.equal(verifyDeviceProof(publicKeyDerBase64, message, signature), true);
  assert.equal(verifyDeviceProof(publicKeyDerBase64, message + "x", signature), false);
});

test("reports freshness only within the bounded evidence window", () => {
  assert.equal(isFresh(now - 90_000, now), true);
  assert.equal(isFresh(now - 90_001, now), false);
  assert.equal(isFresh(now + 31_000, now), false);
});


test("accepts only owner OAuth JWT claims bound to the Friday MCP resource", () => {
  const claims = {
    iss: "https://example.supabase.co/auth/v1",
    aud: "https://friday.example/mcp",
    exp: Math.floor(now / 1000) + 300,
    iat: Math.floor(now / 1000),
    sub: "owner-user-id",
    client_id: "registered-mcp-client",
    role: "authenticated",
    is_anonymous: false,
    email: "owner@example.com"
  };
  assert.equal(validateOwnerClaims(claims, {
    issuer: "https://example.supabase.co/auth/v1",
    resource: "https://friday.example/mcp",
    ownerEmail: "OWNER@example.com",
    now
  }), claims);
});

test("rejects OAuth tokens not bound to the exact Friday MCP audience", () => {
  const claims = {
    iss: "https://example.supabase.co/auth/v1",
    aud: "authenticated",
    exp: Math.floor(now / 1000) + 300,
    iat: Math.floor(now / 1000),
    sub: "owner-user-id",
    client_id: "registered-mcp-client",
    role: "authenticated",
    is_anonymous: false,
    email: "owner@example.com"
  };
  assert.throws(() => validateOwnerClaims(claims, {
    issuer: "https://example.supabase.co/auth/v1",
    resource: "https://friday.example/mcp",
    ownerEmail: "owner@example.com",
    now
  }), /token_audience_invalid/);
});

test("rejects wrong issuer, expired token, missing OAuth client and non-owner", () => {
  const base = {
    iss: "https://example.supabase.co/auth/v1",
    aud: "https://friday.example/mcp",
    exp: Math.floor(now / 1000) + 300,
    iat: Math.floor(now / 1000),
    sub: "owner-user-id",
    client_id: "registered-mcp-client",
    role: "authenticated",
    is_anonymous: false,
    email: "owner@example.com"
  };
  const options = { issuer: base.iss, resource: base.aud, ownerEmail: "owner@example.com", now };
  assert.throws(() => validateOwnerClaims({ ...base, iss: "https://attacker.example" }, options), /token_issuer_invalid/);
  assert.throws(() => validateOwnerClaims({ ...base, exp: Math.floor(now / 1000) }, options), /token_expired/);
  assert.throws(() => validateOwnerClaims({ ...base, client_id: "" }, options), /token_subject_or_client_invalid/);
  assert.throws(() => validateOwnerClaims({ ...base, email: "other@example.com" }, options), /owner_access_denied/);
});
