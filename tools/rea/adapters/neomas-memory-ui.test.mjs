import assert from "node:assert/strict";
import test from "node:test";
import { adaptNeomasMemoryUi } from "./neomas-memory-ui.mjs";

const id = (letter) => "ev_" + letter.repeat(64);

function records() {
  return [
    { evidence_id: id("b"), operation: "capture_browser_scenario", normalized_result: { steps: [{ step_id: "load-daily-resurface" }] }, limitations: [] },
    { evidence_id: id("c"), operation: "capture_browser_scenario", normalized_result: { steps: [{ step_id: "load-tension-card" }] }, limitations: [] },
    { evidence_id: id("d"), operation: "capture_browser_scenario", normalized_result: { steps: [{ step_id: "load-atomized-source-index" }] }, limitations: [] },
    {
      evidence_id: id("a"),
      operation: "inspect_web_page",
      normalized_result: {
        metadata: { dom_urls: [
          { url: "https://neomas.app/assets/screens/e5/01-resurface-past-self-dark-w1440.webp" },
          { url: "https://neomas.app/assets/screens/e5/02b-resurface-tension-card-dark-w1440.webp" },
          { url: "https://neomas.app/assets/screens/e5/03-atomized-source-index-dark-w1440.webp" },
        ] },
      },
      limitations: [],
    },
  ];
}

test("translates Neomas visual evidence into three Friday-native review proposals", () => {
  const result = adaptNeomasMemoryUi({
    target: { kind: "website", locator: "https://neomas.app/", revision: "capture:2026-11-28T12:00:00.000Z" },
    evidenceRecords: records(),
  });
  assert.equal(result.findings.length, 3);
  assert.equal(result.capability_candidates.length, 3);
  assert.equal(result.adaptation_plan.execution_allowed, false);
  assert.equal(result.adaptation_plan.owner_review_required, true);
  assert.equal(result.adaptation_plan.target_platform, "android");
  assert.ok(result.capability_candidates.every((candidate) => candidate.execution_allowed === false));
  assert.ok(result.capability_candidates.every((candidate) => candidate.evidence_refs.length === 2));
  assert.ok(result.findings.every((finding) => finding.confidence === 0.65));
  assert.ok(result.findings.every((finding) =>
    finding.provenance?.source === "rea-adaptation-adapter" &&
    finding.provenance?.target?.revision === "capture:2026-11-28T12:00:00.000Z"
  ));
  assert.ok(result.capability_candidates.every((candidate) =>
    candidate.provenance?.adapter === "neomas-memory-ui"
  ));
  assert.equal(result.adaptation_plan.adapter.version, 1);
  assert.equal(result.adaptation_plan.source_target.revision, "capture:2026-11-28T12:00:00.000Z");
  assert.match(result.adaptation_plan.source_evidence_digest, /^[0-9a-f]{32}$/);
});

test("does not infer proposals without matching screenshot and DOM evidence", () => {
  const result = adaptNeomasMemoryUi({
    target: { kind: "website", locator: "https://neomas.app/" },
    evidenceRecords: records().slice(0, 2),
  });
  assert.equal(result, null);
});

test("does not apply the Neomas adapter to unrelated websites", () => {
  assert.equal(adaptNeomasMemoryUi({
    target: { kind: "website", locator: "https://example.com/" },
    evidenceRecords: records(),
  }), null);
});
