import { createPublicKey, timingSafeEqual, verify } from "node:crypto";

export const SNAPSHOT_SCHEMA = "friday-live-inspection-v1";
export const MAX_SNAPSHOT_AGE_MS = 90_000;
export const ALLOWED_STATUSES = new Set([
  "ready", "starting", "idle", "permission_required", "unavailable", "unknown"
]);

const SNAPSHOT_KEYS = [
  "schema", "captured_at_epoch_ms", "device_capture_at_epoch_ms",
  "source_revision", "app_version_name", "app_version_code", "android_api",
  "network", "battery", "shizuku", "microphone_permission",
  "speech_recognition", "voice_runtime", "model_runtime",
  "available_memory_bytes", "available_storage_bytes", "event_count",
  "failure_count", "privacy"
].sort();

const PRIVACY_KEYS = [
  "conversation_content_included", "transcripts_or_audio_included",
  "screen_or_notification_content_included", "personal_files_or_memory_included",
  "credentials_or_tokens_included", "raw_events_or_failure_messages_included"
].sort();

export function constantTimeEqual(left, right) {
  if (typeof left !== "string" || typeof right !== "string") return false;
  const a = Buffer.from(left, "utf8");
  const b = Buffer.from(right, "utf8");
  return a.length === b.length && timingSafeEqual(a, b);
}

export function verifyDeviceProof(publicKeyDerBase64, message, signatureBase64) {
  try {
    const publicKey = createPublicKey({
      key: Buffer.from(publicKeyDerBase64, "base64"),
      format: "der",
      type: "spki"
    });
    if (publicKey.asymmetricKeyType !== "ec" ||
        publicKey.asymmetricKeyDetails?.namedCurve !== "prime256v1") return false;
    return verify(
      "sha256",
      Buffer.from(message, "utf8"),
      publicKey,
      Buffer.from(signatureBase64, "base64")
    );
  } catch {
    return false;
  }
}

export function validateInspectionSnapshot(input, now = Date.now()) {
  if (!input || typeof input !== "object" || Array.isArray(input)) {
    throw new TypeError("Inspection snapshot must be an object.");
  }
  const keys = Object.keys(input).sort();
  if (JSON.stringify(keys) !== JSON.stringify(SNAPSHOT_KEYS)) {
    throw new TypeError("Inspection snapshot contains missing or unapproved fields.");
  }
  if (input.schema !== SNAPSHOT_SCHEMA) throw new TypeError("Unsupported inspection snapshot schema.");

  const timestamp = finiteInteger(input.captured_at_epoch_ms, "captured_at_epoch_ms");
  const deviceTimestamp = finiteInteger(input.device_capture_at_epoch_ms, "device_capture_at_epoch_ms");
  if (timestamp <= 0 || deviceTimestamp <= 0 || timestamp > now + 30_000 || deviceTimestamp > now + 30_000) {
    throw new TypeError("Inspection snapshot timestamp is invalid.");
  }
  if (now - timestamp > MAX_SNAPSHOT_AGE_MS || now - deviceTimestamp > MAX_SNAPSHOT_AGE_MS) {
    throw new TypeError("Inspection snapshot is stale.");
  }

  const revision = input.source_revision;
  if (typeof revision !== "string" || !(revision === "unknown" || /^[0-9a-f]{40}$/.test(revision))) {
    throw new TypeError("Inspection source revision is invalid.");
  }
  const version = input.app_version_name;
  if (typeof version !== "string" || !(version === "unknown" || /^[A-Za-z0-9.+_-]{1,40}$/.test(version))) {
    throw new TypeError("Inspection app version is invalid.");
  }

  for (const field of [
    "network", "battery", "shizuku", "microphone_permission",
    "speech_recognition", "voice_runtime", "model_runtime"
  ]) {
    if (!ALLOWED_STATUSES.has(input[field])) throw new TypeError("Inspection status is invalid: " + field);
  }

  const numeric = {};
  for (const field of ["app_version_code", "android_api", "available_memory_bytes",
    "available_storage_bytes", "event_count", "failure_count"]) {
    numeric[field] = finiteInteger(input[field], field);
    if (numeric[field] < -1) throw new TypeError("Inspection numeric field is invalid: " + field);
  }

  if (!input.privacy || typeof input.privacy !== "object" || Array.isArray(input.privacy) ||
      JSON.stringify(Object.keys(input.privacy).sort()) !== JSON.stringify(PRIVACY_KEYS) ||
      PRIVACY_KEYS.some(key => input.privacy[key] !== false)) {
    throw new TypeError("Inspection privacy declaration is invalid.");
  }

  return Object.freeze({
    schema: SNAPSHOT_SCHEMA,
    captured_at_epoch_ms: timestamp,
    device_capture_at_epoch_ms: deviceTimestamp,
    source_revision: revision,
    app_version_name: version,
    app_version_code: numeric.app_version_code,
    android_api: numeric.android_api,
    network: input.network,
    battery: input.battery,
    shizuku: input.shizuku,
    microphone_permission: input.microphone_permission,
    speech_recognition: input.speech_recognition,
    voice_runtime: input.voice_runtime,
    model_runtime: input.model_runtime,
    available_memory_bytes: numeric.available_memory_bytes,
    available_storage_bytes: numeric.available_storage_bytes,
    event_count: numeric.event_count,
    failure_count: numeric.failure_count,
    privacy: Object.freeze(Object.fromEntries(PRIVACY_KEYS.map(key => [key, false])))
  });
}

function finiteInteger(value, field) {
  if (!Number.isSafeInteger(value)) throw new TypeError("Inspection field is not a safe integer: " + field);
  return value;
}

export function isFresh(timestamp, now = Date.now()) {
  return Number.isSafeInteger(timestamp) && timestamp > 0 &&
    timestamp <= now + 30_000 && now - timestamp <= MAX_SNAPSHOT_AGE_MS;
}


export function validateOwnerClaims(claims, { issuer, resource, ownerEmail, now = Date.now(), requireClientId = true }) {
  if (!claims || typeof claims !== "object" || Array.isArray(claims)) {
    throw new TypeError("token_claims_invalid");
  }
  if (claims.iss !== issuer) throw new TypeError("token_issuer_invalid");
  const audiences = Array.isArray(claims.aud) ? claims.aud : [claims.aud];
  if (!audiences.includes(resource)) throw new TypeError("token_audience_invalid");
  const nowSeconds = Math.floor(now / 1000);
  if (!Number.isSafeInteger(claims.exp) || claims.exp <= nowSeconds) {
    throw new TypeError("token_expired");
  }
  if (!Number.isSafeInteger(claims.iat) || claims.iat <= 0 || claims.iat > nowSeconds + 30) {
    throw new TypeError("token_issued_at_invalid");
  }
  const validClient = !requireClientId ||
    (typeof claims.client_id === "string" && claims.client_id.length > 0);
  if (typeof claims.sub !== "string" || claims.sub.length === 0 ||
      !validClient || claims.role !== "authenticated" || claims.is_anonymous === true) {
    throw new TypeError("token_subject_or_client_invalid");
  }
  if (!ownerEmail || typeof claims.email !== "string" ||
      claims.email.toLowerCase() !== ownerEmail.toLowerCase()) {
    throw new TypeError("owner_access_denied");
  }
  return claims;
}
