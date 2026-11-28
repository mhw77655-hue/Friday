import { createHash } from "node:crypto";

function digest(value) {
  return createHash("sha256").update(JSON.stringify(value)).digest("hex").slice(0, 32);
}

function captureForStep(records, stepId) {
  return records.find((record) =>
    record.operation === "capture_browser_scenario" &&
    (record.normalized_result?.steps ?? []).some((step) => step.step_id === stepId)
  );
}

function assetFor(records, filename) {
  const inspection = records.find((record) => record.operation === "inspect_web_page");
  const urls = inspection?.normalized_result?.metadata?.dom_urls ?? [];
  const match = urls.find((entry) => typeof entry.url === "string" && entry.url.includes(filename));
  return match ? { inspection, url: match.url } : null;
}

/** Translate public Neomas memory UX evidence into Friday-native, review-only proposals. */
export function adaptNeomasMemoryUi({ target, evidenceRecords = [] }) {
  if (target?.kind !== "website") return null;
  let url;
  try { url = new URL(target.locator); } catch { return null; }
  if (!["neomas.app", "www.neomas.app"].includes(url.hostname)) return null;

  const specs = [
    {
      id: "resurface",
      stepId: "load-daily-resurface",
      asset: "01-resurface-past-self",
      candidateId: "friday_memory_resurface_review",
      title: "Memory resurfacing review",
      statement: "The reference presents older memories as a focused resurfacing card with context, progress and explicit review actions.",
      design: "Single-item focus, temporal/source context, progress indicator and deliberate review actions.",
      mapping: {
        read_model: ["MemoryGraphStore.query", "MemoryNode.salience", "ExperienceRecord"],
        friday_implementation: "Project high-salience existing memories into a review card; preserve source and temporal context.",
        safeguards: ["Viewing is read-only.", "Do not claim a scheduled daily cadence until a durable scheduler exists.", "Only explicit owner actions may alter memory state."],
      },
      verification: [
        "Every displayed item links to its source and temporal context.",
        "Opening and dismissing the view does not mutate stored memory.",
        "Selection is deterministic from existing memory data and can be tested after restart.",
      ],
    },
    {
      id: "tension",
      stepId: "load-tension-card",
      asset: "02b-resurface-tension-card",
      candidateId: "friday_memory_tension_review",
      title: "Memory change and tension review",
      statement: "The reference juxtaposes two notes, labels their relationship and offers explicit reconcile/later actions.",
      design: "Two-column comparison with a concise relationship explanation and explicit reconciliation actions.",
      mapping: {
        read_model: ["MemoryGraphStore.getHistory", "MemoryConflictResolver", "MemoryCorrection"],
        friday_implementation: "Initially show Friday's bi-temporal supersession history; only label semantic contradictions when the conflict resolver supplies evidence.",
        safeguards: ["A changed value is not automatically a contradiction.", "Never silently overwrite a memory.", "Preserve correction and provenance history."],
      },
      verification: [
        "Previous and current values remain traceable.",
        "A correction is reversible and preserves provenance.",
        "Semantic contradiction labels require a verified conflict result.",
      ],
    },
    {
      id: "source-index",
      stepId: "load-atomized-source-index",
      asset: "03-atomized-source-index",
      candidateId: "friday_memory_source_index",
      title: "Source and concept index",
      statement: "The reference exposes source properties, concepts and linked references around an individual knowledge item.",
      design: "Structured source details, concept chips and navigable related references.",
      mapping: {
        read_model: ["MemoryNode.source", "JsonlProvenanceLedger", "MemoryGraphStore"],
        friday_implementation: "Build source detail and backlinks over Friday's existing provenance and graph relationships.",
        safeguards: ["Do not fabricate missing source metadata.", "Preserve original source identity and provenance.", "Do not create a parallel source database."],
      },
      verification: [
        "Every source label resolves to a retained provenance record where available.",
        "Related concepts and links resolve to actual graph records.",
        "Missing provenance is shown explicitly rather than guessed.",
      ],
    },
  ];

  const findings = [];
  const capabilityCandidates = [];
  const limitations = [
    "This adapter is based on public Neomas website assets and screenshots, not authenticated use of the native macOS application.",
    "The tension reference demonstrates a review interaction; Friday's first implementation must not equate ordinary supersession with semantic contradiction.",
    "The proposals are Friday-native mappings, not copied Neomas code or proprietary implementation details.",
  ];

  for (const spec of specs) {
    const capture = captureForStep(evidenceRecords, spec.stepId);
    const asset = assetFor(evidenceRecords, spec.asset);
    if (!capture || !asset) continue;
    const refs = [...new Set([capture.evidence_id, asset.inspection.evidence_id].filter(Boolean))];
    // Screenshot/DOM co-observation supports a useful design hypothesis,
    // not a near-certain claim about hidden product behavior.
    const confidence = 0.65;
    const provenance = {
      source: "rea-adaptation-adapter",
      adapter: "neomas-memory-ui",
      adapter_version: 1,
      target: {
        kind: target.kind,
        locator: url.origin + url.pathname,
        revision: target.revision ?? null,
      },
      evidence_refs: refs,
    };
    findings.push({
      id: "finding_neomas_memory_" + spec.id,
      kind: "ui_pattern",
      statement: spec.statement,
      confidence,
      evidence_refs: refs,
      provenance,
      review_state: "unreviewed",
      executable: false,
    });
    capabilityCandidates.push({
      candidate_id: spec.candidateId,
      name: spec.title,
      category: "memory-experience",
      description: spec.statement,
      status: "proposed",
      confidence,
      reference_asset: asset.url,
      design_requirements: spec.design,
      friday_mapping: spec.mapping,
      verification_requirements: spec.verification,
      evidence_refs: refs,
      provenance,
      requires_owner_review: true,
      execution_allowed: false,
    });
  }

  if (capabilityCandidates.length === 0) return null;
  const sourceEvidenceRefs = [...new Set(capabilityCandidates.flatMap((candidate) => candidate.evidence_refs))].sort();
  const adaptationPlan = {
    plan_id: "friday_adaptation_" + digest({ target: target.locator, sourceEvidenceRefs }),
    target_capability: "memory-experience-ui",
    target_platform: "android",
    status: "pending_review",
    owner_review_required: true,
    execution_allowed: false,
    proposal_ids: capabilityCandidates.map((candidate) => candidate.candidate_id),
    visual_blueprint: {
      theme: "dark / AMOLED",
      layout: "persistent navigation with a focused central knowledge workspace",
      patterns: capabilityCandidates.map((candidate) => ({
        candidate_id: candidate.candidate_id,
        name: candidate.name,
        design_requirements: candidate.design_requirements,
        reference_asset: candidate.reference_asset,
      })),
    },
    integration_contracts: [
      "Extend Friday's existing SQLite graph and provenance substrate; do not create a second memory store.",
      "Preserve temporal history, corrections and source provenance.",
      "Treat external REA findings as untrusted evidence until independently verified.",
    ],
    non_goals: [
      "Do not clone Neomas source code, assets or proprietary internals.",
      "Do not silently mutate memories during browsing or resurfacing.",
      "Do not claim semantic contradiction detection from supersession history alone.",
    ],
    verification_requirements: [
      "Build and test the Android feature against Friday's real memory store.",
      "Verify source traceability and restart continuity.",
      "Verify read-only interactions do not alter stored memory.",
      "Independently review any proposed mutation or new capability before promotion.",
    ],
    source_target: {
      kind: target.kind,
      locator: url.origin + url.pathname,
      revision: target.revision ?? null,
    },
    source_evidence_refs: sourceEvidenceRefs,
    source_evidence_digest: digest({ target: target.locator, revision: target.revision ?? null, sourceEvidenceRefs }),
    adapter: { name: "neomas-memory-ui", version: 1 },
    limitations,
  };

  return { findings, capability_candidates: capabilityCandidates, adaptation_plan: adaptationPlan, limitations };
}
