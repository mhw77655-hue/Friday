/**
 * Friday's provider-neutral REA evidence converter.
 *
 * Converts raw upstream REA results into a Friday-native, provenance-linked
 * assimilation packet. The packet is evidence for Friday to interpret; it is
 * never an instruction stream and cannot authorize execution or promotion.
 *
 * This layer deliberately does not assume Android/APK output. The same
 * contract accepts website, repository, desktop, binary, firmware, runtime,
 * and future REA result types.
 */
import { createHash } from "node:crypto";

const SCHEMA = "friday.rea-assimilation/v1";
const GROUP_KIND = new Map([
  ["findings", "finding"],
  ["observations", "observation"],
  ["facts", "fact"],
  ["hypotheses", "hypothesis"],
  ["limitations", "limitation"],
  ["warnings", "warning"],
  ["errors", "error"],
  ["recommendations", "recommendation"],
  ["capabilities", "capability"],
  ["risks", "risk"],
]);
const TEXT_KEYS = ["claim", "statement", "finding", "description", "summary", "text", "message", "title"];

function stableValue(value) {
  if (Array.isArray(value)) return value.map(stableValue);
  if (value && typeof value === "object") {
    return Object.fromEntries(
      Object.keys(value).sort().map((key) => [key, stableValue(value[key])]),
    );
  }
  return value;
}

function stableJson(value) {
  return JSON.stringify(stableValue(value));
}

function sha256(value) {
  return createHash("sha256").update(value).digest("hex");
}

function textOf(value) {
  if (typeof value === "string") return value.trim();
  if (!value || typeof value !== "object") return "";
  for (const key of TEXT_KEYS) {
    if (typeof value[key] === "string" && value[key].trim()) return value[key].trim();
  }
  return "";
}

function confidenceOf(value, kind) {
  const raw = value && typeof value === "object"
    ? value.confidence ?? value.probability ?? value.score
    : undefined;
  if (typeof raw === "number" && Number.isFinite(raw)) {
    return Math.max(0, Math.min(1, raw > 1 && raw <= 100 ? raw / 100 : raw));
  }
  if (typeof raw === "string") {
    const match = raw.trim().match(/^(\d+(?:\.\d+)?)\s*%?$/);
    if (match) {
      const parsed = Number(match[1]);
      return Math.max(0, Math.min(1, raw.includes("%") || parsed > 1 ? parsed / 100 : parsed));
    }
  }
  return ({
    fact: 0.7,
    observation: 0.55,
    finding: 0.45,
    hypothesis: 0.3,
    limitation: 0.8,
    warning: 0.7,
    error: 0.7,
    risk: 0.45,
    capability: 0.35,
    recommendation: 0.25,
    unclassified: 0.15,
  })[kind] ?? 0.25;
}

function collectEvidenceIds(value, found = new Set(), depth = 0) {
  if (depth > 24 || found.size >= 500) return [...found];
  if (typeof value === "string") {
    for (const match of value.matchAll(/\bev_[0-9a-f]{64}\b/g)) found.add(match[0]);
    return [...found];
  }
  if (Array.isArray(value)) {
    for (const child of value) collectEvidenceIds(child, found, depth + 1);
    return [...found];
  }
  if (!value || typeof value !== "object") return [...found];
  for (const [key, child] of Object.entries(value)) {
    if ((key === "evidence_id" || key === "evidenceId" || key === "id") &&
        typeof child === "string" && /^ev_[0-9a-f]{64}$/.test(child)) {
      found.add(child);
    }
    collectEvidenceIds(child, found, depth + 1);
  }
  return [...found];
}

function explicitEvidenceRefs(value) {
  if (!value || typeof value !== "object") return [];
  const refs = value.evidence_ids ?? value.evidenceIds ?? value.evidence_refs ?? value.evidenceRefs;
  if (!Array.isArray(refs)) return [];
  return refs.filter((item) => typeof item === "string" && item.length <= 256);
}

function normalizeKind(value, fallback) {
  const candidate = String(
    value && typeof value === "object" ? value.kind ?? value.type ?? value.category ?? fallback : fallback,
  ).toLowerCase().replace(/[\s-]+/g, "_");
  if (candidate.includes("limit")) return "limitation";
  if (candidate.includes("hypoth")) return "hypothesis";
  if (candidate.includes("observ")) return "observation";
  if (candidate.includes("fact")) return "fact";
  if (candidate.includes("warn")) return "warning";
  if (candidate.includes("error") || candidate.includes("failure")) return "error";
  if (candidate.includes("risk")) return "risk";
  if (candidate.includes("capabil")) return "capability";
  if (candidate.includes("recommend") || candidate.includes("suggest")) return "recommendation";
  if (candidate.includes("finding") || candidate.includes("result")) return "finding";
  return "unclassified";
}

function normalizeProvenance(value, sourcePath, source) {
  const item = value && typeof value === "object" ? value : {};
  const provenance = {
    source: "rea",
    tool: source.tool,
    path: sourcePath,
  };
  for (const key of ["file", "path", "source_path", "symbol", "class_name", "method_name", "line", "start_line", "end_line"]) {
    if (item[key] !== undefined && (typeof item[key] === "string" || Number.isInteger(item[key]))) {
      provenance[key] = item[key];
    }
  }
  return provenance;
}

function findingFrom(value, fallbackKind, sourcePath, source, targetKey) {
  const statement = textOf(value);
  if (!statement) return null;
  const kind = normalizeKind(value, fallbackKind);
  const evidenceRefs = [...new Set([
    ...explicitEvidenceRefs(value),
    ...collectEvidenceIds(value),
  ])].sort();
  const raw = stableJson(value);
  const id = "rea_" + sha256(targetKey + "\n" + sourcePath + "\n" + raw).slice(0, 32);
  const item = value && typeof value === "object" ? value : {};
  return {
    id,
    kind,
    statement: statement.slice(0, 8000),
    confidence: confidenceOf(value, kind),
    evidence_refs: evidenceRefs,
    provenance: normalizeProvenance(value, sourcePath, source),
    review_state: "unreviewed",
    executable: false,
    raw_sha256: sha256(raw),
  };
}

function collectFindings(value, sourcePath, source, targetKey, output, depth = 0) {
  if (depth > 24 || output.length >= 500) return;
  if (Array.isArray(value)) {
    value.forEach((child, index) =>
      collectFindings(child, sourcePath + "[" + index + "]", source, targetKey, output, depth + 1));
    return;
  }
  if (!value || typeof value !== "object") return;

  for (const [key, child] of Object.entries(value)) {
    const kind = GROUP_KIND.get(key.toLowerCase());
    if (kind) {
      const items = Array.isArray(child) ? child : [child];
      for (let index = 0; index < items.length && output.length < 500; index += 1) {
        const finding = findingFrom(items[index], kind, sourcePath + "." + key + "[" + index + "]", source, targetKey);
        if (finding) output.push(finding);
      }
      continue;
    }
    if (child && typeof child === "object") {
      collectFindings(child, sourcePath + "." + key, source, targetKey, output, depth + 1);
    }
  }
}

function fallbackTextFindings(result, source, targetKey) {
  const blocks = Array.isArray(result?.content) ? result.content : [];
  const findings = [];
  for (let index = 0; index < blocks.length; index += 1) {
    const block = blocks[index];
    if (block?.type !== "text" || typeof block.text !== "string" || !block.text.trim()) continue;
    let parsed;
    try { parsed = JSON.parse(block.text); } catch { parsed = null; }
    if (parsed && typeof parsed === "object") {
      collectFindings(parsed, "result.content[" + index + "].json", source, targetKey, findings);
    } else {
      const finding = findingFrom(
        { text: block.text, kind: "unclassified" },
        "unclassified",
        "result.content[" + index + "].text",
        source,
        targetKey,
      );
      if (finding) findings.push(finding);
    }
  }
  return findings;
}

function validateTarget(target) {
  if (!target || typeof target !== "object") throw new TypeError("target is required.");
  if (typeof target.kind !== "string" || !target.kind.trim()) {
    throw new TypeError("target.kind must identify the REA target type.");
  }
  if (typeof target.locator !== "string" || !target.locator.trim()) {
    throw new TypeError("target.locator must identify the inspected target.");
  }
  if (target.sha256 !== undefined && !/^[0-9a-f]{64}$/i.test(target.sha256)) {
    throw new TypeError("target.sha256 must be a 64-character SHA-256 digest.");
  }
}

export function convertReaResult({
  target,
  rea = {},
  tool = "unknown",
  result,
  capturedAt = new Date().toISOString(),
}) {
  validateTarget(target);
  if (result === undefined) throw new TypeError("result is required.");
  const targetIdentity = {
    kind: target.kind.trim(),
    locator: target.locator.trim(),
    revision: target.revision ?? null,
    sha256: target.sha256?.toLowerCase() ?? null,
  };
  const targetKey = sha256(stableJson(targetIdentity));
  const source = { tool: String(tool).slice(0, 256) };
  const rawJson = stableJson(result);
  const findings = [];
  collectFindings(result?.structuredContent ?? result?.structured_content ?? result,
    "result.structuredContent", source, targetKey, findings);
  if (findings.length === 0) {
    findings.push(...fallbackTextFindings(result, source, targetKey));
  }
  const uniqueFindings = [...new Map(findings.map((finding) => [finding.id, finding])).values()];
  const evidenceRefs = [...new Set([
    ...collectEvidenceIds(result),
    ...uniqueFindings.flatMap((finding) => finding.evidence_refs),
  ])].sort();
  const capabilities = uniqueFindings
    .filter((finding) => finding.kind === "capability")
    .map((finding) => ({
      candidate_id: "cap_" + sha256(finding.id).slice(0, 24),
      title: finding.statement.slice(0, 160),
      description: finding.statement,
      evidence_refs: finding.evidence_refs,
      status: "proposed",
      requires_owner_policy_check: true,
      requires_independent_verification: true,
      execution_allowed: false,
    }));
  const rawSha256 = sha256(rawJson);
  const packetCore = {
    schema: SCHEMA,
    target: {
      ...targetIdentity,
      display_name: target.display_name ?? null,
    },
    source: {
      provider: "rea",
      package: rea.package ?? "rea-agents",
      version: rea.version ?? null,
      integrity: rea.integrity ?? null,
      server: rea.server ?? null,
      protocol_version: rea.protocol_version ?? rea.protocolVersion ?? null,
      tool: source.tool,
      captured_at: capturedAt,
      raw_result_sha256: rawSha256,
    },
    status: result?.isError ? "error" : uniqueFindings.length ? "ready_for_review" : "empty",
    findings: uniqueFindings,
    evidence_refs: evidenceRefs,
    limitations: uniqueFindings.filter((finding) => finding.kind === "limitation"),
    capability_candidates: capabilities,
    assimilation: {
      state: "pending_review",
      accepted_finding_ids: [],
      rejected_finding_ids: [],
      execution_allowed: false,
    },
    trust: {
      source_content: "untrusted",
      model_or_tool_output_is_authority: false,
      executable_instructions: false,
      promotion_requires: ["owner_policy", "isolated_test", "independent_verification"],
    },
    raw_result: result,
  };
  return {
    packet_id: "rea_packet_" + sha256(stableJson(packetCore)).slice(0, 32),
    ...packetCore,
  };
}

export function mergeReaPackets(packets) {
  if (!Array.isArray(packets) || packets.length === 0) {
    throw new TypeError("At least one REA assimilation packet is required.");
  }
  const first = packets[0];
  if (!first || first.schema !== SCHEMA) throw new TypeError("Unsupported REA assimilation packet schema.");
  const identity = stableJson(first.target);
  for (const packet of packets) {
    if (packet?.schema !== SCHEMA || stableJson(packet.target) !== identity) {
      throw new TypeError("REA packets can only be merged for the same exact target identity.");
    }
  }
  const findings = [...new Map(packets.flatMap((packet) => packet.findings ?? [])
    .map((finding) => [finding.id, finding])).values()];
  const evidenceRefs = [...new Set(packets.flatMap((packet) => packet.evidence_refs ?? []))].sort();
  const capabilityCandidates = [...new Map(packets.flatMap((packet) => packet.capability_candidates ?? [])
    .map((candidate) => [candidate.candidate_id, candidate])).values()];
  const sources = packets.map((packet) => packet.source);
  const packetCore = {
    schema: SCHEMA,
    target: first.target,
    source: { provider: "rea", tool: "merged-investigation", sources },
    status: packets.some((packet) => packet.status === "error")
      ? "partial_error"
      : findings.length ? "ready_for_review" : "empty",
    findings,
    evidence_refs: evidenceRefs,
    limitations: findings.filter((finding) => finding.kind === "limitation"),
    capability_candidates: capabilityCandidates,
    assimilation: {
      state: "pending_review",
      accepted_finding_ids: [],
      rejected_finding_ids: [],
      execution_allowed: false,
    },
    trust: {
      source_content: "untrusted",
      model_or_tool_output_is_authority: false,
      executable_instructions: false,
      promotion_requires: ["owner_policy", "isolated_test", "independent_verification"],
    },
    raw_results: packets.map((packet) => packet.raw_result),
  };
  return {
    packet_id: "rea_packet_" + sha256(stableJson(packetCore)).slice(0, 32),
    ...packetCore,
  };
}

export const REA_ASSIMILATION_SCHEMA = SCHEMA;
