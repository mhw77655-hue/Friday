# Friday

Friday is an Android-hosted independent agent. Its identity, continuity and task lifecycle belong to Friday; models, tools and cloud services are replaceable capabilities. The Android application is the product, while the repository contains its source, engineering system, verification, release pipeline and research.

**Canonical product line:** `main`  
**Android application ID:** `ai.friday.assistant`  
**Android source:** `mobile/app`

## Start here

- [Agent and contributor instructions](AGENTS.md) — mandatory engineering rules for humans and coding agents.
- [Canonical delivery plan](.ralph/FRIDAY_DELIVERY_PLAN.md) — sole active delivery authority.
- [Friday vision](.ralph/VISION.md) and [owner intent](.ralph/OWNER_WORDS.md) — product direction and provenance.
- [Repository facts](REPO_FACTS.md) — build identity and verification commands.
- [Repository structure and migration policy](docs/FRIDAY_REPOSITORY_STRUCTURE_AND_MIGRATION.md) — safe, incremental structural changes.

## Project map

| Path | Purpose |
|---|---|
| `mobile/app/` | Android application source, tests and assets |
| `services/` | Supporting cloud services and integrations |
| `docs/` | Architecture, engineering decisions, acceptance records and research |
| `.github/workflows/` | Build, test, release and controlled automation |
| `.ralph/` | Active delivery plan, vision and development governance |
| `formal/` | Lean 4 abstract contracts and formal assurance |
| `manifest/` | Workspace and protected-path governance |
| `vault/` | Historical Jarvis/Friday research archive; not active product instructions |

## Build and verify

```bash
./gradlew :mobile:app:assembleDebug
./gradlew :mobile:app:testDebugUnitTest
```

A successful build or JVM test is not proof of emulator instrumentation or physical-device behavior. Follow [AGENTS.md](AGENTS.md) and report each evidence level separately.

## Core architecture

- [Friday REA system architecture](docs/FRIDAY_REA_SYSTEM_ARCHITECTURE.md)
- [Local station and cloud development architecture](docs/FRIDAY_LOCAL_STATION_AND_CLOUD_DEVELOPMENT_ARCHITECTURE.md)
- [Execution and evolution governance](docs/FRIDAY_EXECUTION_AND_EVOLUTION_GOVERNANCE.md)
- [Continuous presence and self-development](docs/FRIDAY_CONTINUOUS_PRESENCE_AND_SELF_DEVELOPMENT.md)
- [Self-governance and repository truth](docs/FRIDAY_SELF_GOVERNANCE_AND_REPOSITORY_TRUTH.md)

## Current engineering work

- [Live inspection bridge contract](docs/FRIDAY_LIVE_INSPECTION_BRIDGE.md)
- [Live inspection deployment status](docs/FRIDAY_LIVE_INSPECTION_DEPLOYMENT_STATUS.md)
- [Live inspection work log](docs/FRIDAY_LIVE_INSPECTION_WORK_LOG.md)
- [Installed APK repair progress](docs/FRIDAY_REPAIR_PROGRESS.md)

The live inspection bridge is temporary bootstrap infrastructure. The long-term goal is for Friday's advanced REA capability to own diagnosis, repair, verification and evolution.

## Releases and signing

See [release signing setup](docs/FRIDAY_RELEASE_SIGNING_SETUP.md). Never commit signing keys, credentials, private device data or owner conversation archives.
