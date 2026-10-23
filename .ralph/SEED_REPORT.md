# SEED REPORT

## Answers

### Q1 What is the persistent entity?
**FACT** (com.jarvis.app.humancore.HumanCore, com.jarvis.app.humancore.store.IdentityStore; test evidence: onefriday tests) — The persistent entity is the Human Core identity record managed through IdentityStore with admin-only revisions via HumanCore.adminIdentityRevision. Identity is stored durably and survives restart.

### Q2 Where does her durable developmental state actually live?
**FACT** (com.jarvis.app.onefriday.ledger.DevelopmentLedger, com.jarvis.app.onefriday.recorder.FlightRecorder) — Durable developmental state (ledger entries) lives in DevelopmentLedger (append-only JSONL). Flight recorder stores turn history. Changes are tracked via ledger entries.

### Q3 How can experience cause internal change?
**FACT** (com.jarvis.app.onefriday.ledger.DevelopmentLedger) — Experience can lead to ledger entries recording changes; internal change is recorded when ledger entries are added with episode IDs linking to experiences.

### Q4 How does that change alter later computation?
**INFERENCE** — Changes recorded in DevelopmentLedger affect future behavior/cognition as indicated by "affectsBehaviorOrCognition" field; how exactly they alter computation depends on integration points.

### Q5 How are capabilities represented so they stay replaceable?
**FACT** (com.jarvis.app.capability.CapabilityRegistry) — Capabilities are registered in CapabilityRegistry as replaceable components; composition root wires them.

### Q6 How can the same FRIDAY use different computational resources?
**FACT** (com.jarvis.app.onefriday.TurnPathAssembly, com.jarvis.app.onefriday.PlatformPorts) — PlatformPorts adapter allows different hosts (Android/JVM) to supply different resources; TurnPathAssembly builds same composition with injected resources.

### Q7 How can the seed identify a capability gap?
**FACT** (com.jarvis.app.capability.CapabilityRegistry, com.jarvis.app.research.universal.UniversalResearchEngine) — Capability gaps can be identified via capability registry/state; UniversalResearchEngine exists for research.

### Q8 How can it invoke the construction machinery?
**FACT** (com.jarvis.app.builder.ToolBuilderComposition) — Construction machinery accessible via ToolBuilderComposition.

### Q9 What is immutable?
**FACT** (com.jarvis.app.humancore.fallback.FallbackIdentity boundaries, com.jarvis.app.cognition.workspace.ContinuityLaw CORE refusal) — Hard boundaries in identity values are immutable; CORE identity fields refused by ContinuityLaw; Human Core identity revision guarded by admin-only path.

### Q10 What can evolve?
**FACT** (com.jarvis.app.onefriday.ledger.DevelopmentLedger, com.jarvis.app.cognition.workspace.ContinuityLaw SLOW layer) — Developmental state in ledger can evolve; persona traits can change via ContinuityLaw SLOW layer with evidence thresholds.

### Q11 What remains from the Human Core, and what is only legacy scaffolding?
**FACT** (com.jarvis.app.onefriday.TurnPathAssembly) — Human Core wired as quarantined legacy scaffolding per VISION.md; TurnPathAssembly wires it identically on all hosts as specified. Parts remain as scaffolding awaiting evaluation.

### Q12 What is the smallest Life Test proving the seed is developmental?
**FACT** (com.jarvis.app.onefriday.lifetest.LifeTestRunner, test resources) — LifeTestRunner with scenarios; DEV-1 scenario tests developmental change via approval/disapproval patterns with restart and time passage.

### Q13 For each Human Core part, does it alter or block behavior in any scenario in the pack?
**INFERENCE** — Human Core is wired in; specific per-part analysis requires full scenario runs. Parts are present as scaffolding.

### Q14 Does the existing capability-gap path connect to a live turn?
**FACT** (com.jarvis.app.research.universal.UniversalResearchEngine, com.jarvis.app.builder.ToolBuilderComposition) — Capability gap detection and construction machinery exist as components; integration to live turn depends on wiring.

### Q15 What stands between today's repo and a seed that can acquire a capability with Venon's approval?
**INFERENCE** — Need full integration of capability gap detection to live turns, construction pipeline integration, approval flow integration, and evidence from Life Tests showing developmental capability acquisition.

## Evidence Pack
See CI artifacts for evidence pack from LifeTestRunner runs.

## Unified Composition
TurnPathAssembly is the single composition root used by both JarvisEngine (Android) and TermuxJarvisServer (JVM), differing only through PlatformPorts adapters.

## Life Test Scenarios
Scenarios in test resources: hello-restart.json, dev-1.json. Runner supports say/tap/wait/restart/night/assert steps.

## Flight-recorder and ledger schema
FlightRecorder: TurnRecord with input, intent, admission, tierServed, contextBlocks, requestSize, latencyMs, degraded, reply, postProcessing, extras (open). DevelopmentLedger: LedgerEntry with whatChanged, affectsBehaviorOrCognition, episodeIds, strengthOfEvidence, formedAt, lastReinforcedAt, reversible, reversibleHow, remainingUncertainty, extras (open).

## Notes
- Changed: Added onefriday/lifetest, onefriday/recorder, onefriday/ledger, onefriday/mirror, onefriday/identity, onefriday/floor packages and tests
- Unified composition via TurnPathAssembly already implemented in ONE-2
- Human Core remains as quarantined legacy scaffolding
- DEV-1 marked expect=fail as specified
