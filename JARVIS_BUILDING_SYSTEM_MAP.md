# JARVIS Building System — Component Map

## Component Relationships

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         BUILDING SYSTEM (building/)                         │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐    ┌──────────┐  │
│  │   Genome     │    │  Capability  │    │   Mutant     │    │Evolution │  │
│  │   System     │    │   System     │    │Environment   │    │ Engine   │  │
│  └──────┬───────┘    └──────┬───────┘    └──────┬───────┘    └────┬─────┘  │
│         │                   │                   │                 │        │
│         ▼                   ▼                   ▼                 ▼        │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │                    DEVELOPMENT ORCHESTRATOR                          │  │
│  │  (DevelopmentOrchestrator → SessionManager → DevelopmentSession)     │  │
│  └──────────────────────────────────────────────────────────────────────┘  │
│         │                   │                   │                 │        │
│         ▼                   ▼                   ▼                 ▼        │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐    ┌──────────┐  │
│  │   Journal &  │    │   Adapters   │    │Self-Dev      │    │ Building │  │
│  │   Lineage    │    │  (8 types)   │    │  Pipeline    │    │  System  │  │
│  └──────────────┘    └──────────────┘    └──────────────┘    └────┬─────┘  │
│                                                                     │        │
│                           ┌─────────────────────────────────────────┘        │
│                           ▼                                                  │
│              ┌────────────────────────┐                                     │
│              │   BuildingSystem       │                                     │
│              │   (Facade + Capability)│                                     │
│              └───────────┬────────────┘                                     │
│                          │                                                  │
│                          ▼                                                  │
│         ┌────────────────────────────────┐                                 │
│         │   COGNITIVE FABRIC INTEGRATION │                                 │
│         │   (BuildingSystemCapability)   │                                 │
│         └────────────────────────────────┘                                 │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
          │                    │                    │                    │
          ▼                    ▼                    ▼                    ▼
┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐
│  EXISTING       │ │  EXISTING       │ │  EXISTING       │ │  EXISTING       │
│  COGNITIVE      │ │  MEMORY         │ │  RESOURCE       │ │  FAILURE        │
│  ENGINE         │ │  CONTINUITY     │ │  GOVERNOR       │ │  SURFACE        │
│  (Planning,     │ │  (WorkingMem,   │ │  (ResourceSandbox│ │  (Logging,      │
│   Execution)    │ │   EpisodicMem)  │ │   ← ResourceGov)│ │   Recovery)     │
└─────────────────┘ └─────────────────┘ └─────────────────┘ └─────────────────┘
```

---

## Data Flow: Capability Gap → Promotion

```
USER REQUEST / CAPABILITY GAP
         │
         ▼
    ┌─────────────────────────────────────┐
    │ DevelopmentOrchestrator.execute()   │
    └─────────────────────────────────────┘
         │
         ▼
    ┌─────────────────────────────────────┐
    │ SessionManager.create_session()     │
    │ DevelopmentSession (state=INIT)     │
    └─────────────────────────────────────┘
         │
         ▼
    ┌─────────────────────────────────────┐
    │ RESEARCH PHASE                      │
    │ ResearchAdapter.search()            │
    │ OperationJournal.log_operation()    │
    └─────────────────────────────────────┘
         │
         ▼
    ┌─────────────────────────────────────┐
    │ DESIGN PHASE                        │
    │ SpecSynthesizer.synthesize()        │
    │ → Genome                            │
    │ GenomeArchive.put()                 │
    │ LineageTracker.create_lineage()     │
    └─────────────────────────────────────┘
         │
         ▼
    ┌─────────────────────────────────────┐
    │ ENVIRONMENT PHASE                   │
    │ EnvironmentFactory.create_workspace()│
    │ MutantEnvironment.create()          │
    │   → MutantInstance (workspace,      │
    │     manifest, status)               │
    └─────────────────────────────────────┘
         │
         ▼
    ┌─────────────────────────────────────┐
    │ WORKSPACE PHASE                     │
    │ RepositoryAdapter.clone()           │
    │ Workspace.repo_path()               │
    └─────────────────────────────────────┘
         │
         ▼
    ┌─────────────────────────────────────┐
    │ IMPLEMENT PHASE                     │
    │ CandidateGenerator.generate()       │
    │ → CandidateImplementation (files)   │
    │ Workspace writes source files       │
    └─────────────────────────────────────┘
         │
         ▼
    ┌─────────────────────────────────────┐
    │ BUILD PHASE                         │
    │ BuildAdapter.detect()               │
    │ BuildAdapter.prepare()              │
    │ BuildAdapter.build()                │
    │ ResourceSandbox.enforce()           │
    │ ArtifactManager.store()             │
    └─────────────────────────────────────┘
         │
         ▼
    ┌─────────────────────────────────────┐
    │ TEST PHASE                          │
    │ CandidateTestRunner.run()           │
    │ MutantEnvironment.test()            │
    │ Test results → Candidate.test_results│
    │ Observation (TEST_RESULT)           │
    └─────────────────────────────────────┘
         │
         ▼
    ┌─────────────────────────────────────┐
    │ BENCHMARK PHASE                     │
    │ BenchmarkAdapter.prepare_workload() │
    │ BenchmarkAdapter.execute()          │
    │ Results → Candidate.benchmark_results│
    │ Observation (BENCHMARK_RESULT)      │
    └─────────────────────────────────────┘
         │
         ▼
    ┌─────────────────────────────────────┐
    │ OBSERVE PHASE                       │
    │ RuntimeAdapter.start_process()      │
    │ RuntimeAdapter.health()             │
    │ RuntimeAdapter.logs()               │
    │ Observation (RUNTIME_BEHAVIOR)      │
    └─────────────────────────────────────┘
         │
         ▼
    ┌─────────────────────────────────────┐
    │ EVALUATE PHASE                      │
    │ FitnessModel.evaluate()             │
    │ PromotionGate.evaluate()            │
    │ GateDecision (PROMOTE/REJECT/...)   │
    │ Evidence.create()                   │
    └─────────────────────────────────────┘
         │
         ▼
    ┌─────────────────────────────────────┐
    │ PROMOTE PHASE (if approved)         │
    │ PromotionController.promote()       │
    │   1. GenomeArchive.put(HEALTHY)     │
    │   2. GenomeRegistry.register()      │
    │   3. CapabilityRegistry.register()  │
    │   4. FailureSurface.log(INFO)       │
    │   5. PromotionReceipt               │
    └─────────────────────────────────────┘
         │
         ▼
    ┌─────────────────────────────────────┐
    │ COMPLETE                            │
    │ DevelopmentSession.complete()       │
    │ LineageTracker.put()                │
    │ OperationJournal finalize           │
    └─────────────────────────────────────┘
```

---

## Lifecycle Flows

### 1. Development Session Lifecycle (12 States)

```
INITIALIZING
    │
    ▼
RESEARCHING ──(research complete)──▶ DESIGNING
    ▲                                    │
    │                                    ▼
    │                            ENVIRONMENT_PREPARING
    │                                    │
    │                                    ▼
    │                            WORKSPACE_PREPARING
    │                                    │
    │                                    ▼
    │                            IMPLEMENTING
    │                                    │
    │                                    ▼
    │                            BUILDING
    │                                    │
    │                                    ▼
    │                            TESTING
    │                                    │
    │                                    ▼
    │                            BENCHMARKING
    │                                    │
    │                                    ▼
    │                            OBSERVING
    │                                    │
    │                                    ▼
    │                            EVALUATING
    │                                    │
    │              ┌─────────────────────┴─────────────────────┐
    │              ▼                                           ▼
    │       PROMOTING                                     COMPLETED
    │              │                                           │
    │              ▼                                           │
    │       COMPLETED                                          │
    │                                                         │
    └─────────────────────────────────────────────────────────┘
              │
              ▼
        (rollback) ──▶ ROLLED_BACK
```

### 2. Genome Lifecycle

```
CREATE (GenomeBuilder)
    │
    ▼
GENOME (UNKNOWN health)
    │
    ├──▶ MUTATE (MutationEngine) ──▶ CHILD GENOME (UNKNOWN)
    │                                     │
    │                                     ▼
    │                              GENERATE (CandidateGenerator)
    │                                     │
    │                                     ▼
    │                              BUILD → TEST → BENCHMARK
    │                                     │
    │                                     ▼
    │                              EVALUATE (FitnessModel)
    │                                     │
    │              ┌──────────────────────┴──────────────────────┐
    │              ▼                                             ▼
    │        PROMOTE                                         REJECT
    │              │                                             │
    │              ▼                                             ▼
    │        ARCHIVE (HEALTHY)                            ARCHIVE (REJECTED)
    │              │                                             │
    │              ▼                                             │
    │        REGISTRY (current-best)                            │
    │              │                                             │
    │              ▼                                             │
    │        CALLABLE (SpecMicroSystem)                         │
    │              │                                             │
    └──────────────┴────────────────────────────────────────────┘
                          │
                          ▼
                  (later rollback?)
                          │
                          ▼
                  ROLLBACK CONTROLLER
                          │
                          ▼
                  ARCHIVE (REJECTED promoted)
                  REGISTRY (restore previous)
                  FAILURE SURFACE (WARNING)
```

### 3. Candidate Lineage Flow

```
ROOT GENOME (genesis)
    │
    ├── Mutation 1 (CAPABILITY_ADD)
    │       │
    │       ├── Evaluation 1 (fitness=0.6, gate=REJECT)
    │       │
    │       └── Mutation 2 (RESOURCE_ADJUST)
    │               │
    │               ├── Evaluation 2 (fitness=0.8, gate=PROMOTE)
    │               │       │
    │               │       └── PROMOTION (receipt=promo-1)
    │               │
    │               └── Mutation 3 (TEST_ADD)
    │                       │
    │                       ├── Evaluation 3 (fitness=0.85, gate=PROMOTE)
    │                       │       │
    │                       │       └── PROMOTION (receipt=promo-2)
    │                       │
    │                       └── ROLLBACK (reason="regression")
    │                               │
    │                               └── ROLLBACK_HISTORY entry
    │
    └── Mutation 1b (LATENCY_ADJUST)
            │
            ├── Evaluation 1b (fitness=0.75, gate=NEEDS_HUMAN_REVIEW)
            │
            └── (human approves)
                    │
                    └── PROMOTION (receipt=promo-3)
```

---

## Ownership Boundaries

| Component | Owns | Does Not Own |
|-----------|------|--------------|
| **Genome** | Specification, lineage, health state | Execution, artifacts |
| **GenomeRegistry** | Current-best pointers by capability | Archive storage, mutant envs |
| **GenomeArchive** | All genomes ever created (append-only) | Promotion decisions |
| **CapabilityRegistry** | Specs and running instances | Genome health, build process |
| **MutantEnvironment** | Workspace, process lifecycle, artifacts | Genome registry, promotion |
| **ResourceSandbox** | Limit enforcement on a process | What the process does |
| **EvolutionEngine** | Generation loop, candidate pipeline | Capability fabric, session mgmt |
| **PromotionGate** | Promotion verdict based on evidence | Executing promotion |
| **PromotionController** | Atomic promotion transaction | Fitness evaluation |
| **RollbackController** | Atomic rollback transaction | Fitness evaluation |
| **DevelopmentSession** | Session state, metadata, workspace | Genome, candidate evaluation |
| **OperationJournal** | Append-only operation/observation/evidence log | Decision making |
| **LineageTracker** | Persistent candidate genealogy | Genome content |
| **Adapters** | External system communication | Building System logic |
| **SelfDevelopmentPipeline** | Self-modification experiment flow | Production promotion |

---

## Adapter Registry Structure

```
AdapterRegistry
├── build: Dict[str, BuildAdapter]
│   ├── "gradle" → GradleBuildAdapter
│   ├── "python" → PythonBuildAdapter
│   └── "shell" → ShellBuildAdapter
│
├── repository: Dict[str, RepositoryAdapter]
│   ├── "git" → GitRepositoryAdapter
│   └── "null" → NullRepositoryAdapter
│
├── runtime: Dict[str, RuntimeAdapter]
│   ├── "process" → ProcessRuntimeAdapter
│   └── "service" → ServiceRuntimeAdapter
│
├── environment: Dict[str, EnvironmentAdapter]
│   ├── "filesystem" → FilesystemEnvironmentAdapter
│   └── "process" → ProcessEnvironmentAdapter
│
├── deployment: Dict[str, DeploymentAdapter]
│   ├── "local" → LocalDeploymentAdapter
│   └── "null" → NullDeploymentAdapter
│
├── model: Dict[str, ModelAdapter]
│   ├── "llama.cpp" → LlamaCppModelAdapter
│   └── "null" → NullModelAdapter
│
├── benchmark: Dict[str, BenchmarkAdapter]
│   ├── "latency" → LatencyBenchmarkAdapter
│   └── "throughput" → ThroughputBenchmarkAdapter
│
└── research: Dict[str, ResearchAdapter]
    ├── "universal" → UniversalResearchAdapter
    └── "null" → NullResearchAdapter
```

---

## Integration with Existing JARVIS Systems

### Cognitive Engine → Building System

```
CognitiveEngine
    │
    ├── fill_capability_gap() ──▶ BuildingSystem.fill_capability_gap()
    ├── run_self_development() ──▶ BuildingSystem.run_self_development_experiment()
    ├── evolve_capability() ──▶ BuildingSystem.evolve_capability()
    └── get_status() ──▶ BuildingSystem.get_system_status()

    │
    ▼
BuildingSystemCapability (exposes to CapabilityFabric)
    │
    ├── CapabilitySpec:
    │   name: "building_system"
    │   provides: ["fill_capability_gap", "run_self_development",
    │              "evolve_capability", "get_system_status"]
    │   requires: []
    │
    └── execute(operation, params) ──▶ BuildingSystem methods
```

### Classifier Enhancement

```
enhanced_classify(text)
    │
    ├── "build", "create", "implement" ──▶ "build_capability"
    ├── "improve yourself", "self develop" ──▶ "self_develop"
    ├── "evolve", "optimize" ──▶ "evolve"
    └── "build status" ──▶ "build_status"
```

### Decision Gate Enhancement

```
enhanced_is_tier2(action)
    │
    ├── PROTECTED_PATTERNS (core, body, memory)
    └── BUILDING_PROTECTED (building/orchestrator.py,
                             building/self_development.py,
                             building/genome.py,
                             building/evolution.py,
                             core/building_integration.py)
```

### Memory Continuity Integration

```
OperationJournal (JSONL)
    │
    ├── Same format as existing logs/
    ├── session_id links to DevelopmentSession
    ├── candidate_id links to CandidateLineage
    └── genome_id links to GenomeArchive
```

### Resource Governor Integration

```
ResourceSandbox (per mutant environment)
    │
    ├── max_memory_mb ← Genome.resource_budget.max_memory_mb
    ├── max_cpu_percent ← Genome.resource_budget.max_cpu_percent
    ├── max_duration_seconds ← Genome.resource_budget.max_duration_seconds
    └── enforce() on subprocess ← ProcessLocalBackend
```

### Failure Surface Integration

```
PromotionController.promote()
    │
    └── failure_surface.report(INFO, "promotion", details)

RollbackController.rollback()
    │
    └── failure_surface.report(WARNING, "rollback", details)

Observation(type=FAILURE)
    │
    └── failure_surface.report(ERROR, "observation", details)
```

---

## File Ownership Map

```
/mnt/sdcard/jarvis-repo/
├── building/
│   ├── __init__.py                    # All public exports
│   ├── genome.py                      # Genome, Builder, Registry, Archive
│   ├── capability.py                  # CapabilitySpec, Instance, Registry
│   ├── mutant.py                      # MutantEnvironment, Sandbox, Artifacts
│   ├── evolution.py                   # Engine, Mutation, Gate, Controllers
│   ├── session.py                     # Session, Target, Workspace, Orchestrator
│   ├── journal.py                     # Journal, Observation, Evidence, Lineage
│   ├── orchestrator.py                # BuildingSystem facade
│   ├── self_development.py            # Self-dev pipeline
│   └── adapters/
│       ├── __init__.py                # Adapter exports
│       ├── base.py                    # Abstract interfaces
│       ├── build.py                   # Gradle, Python, Shell
│       ├── repository.py              # Git, Null
│       ├── runtime.py                 # Process, Service
│       ├── environment.py             # Filesystem, Process
│       ├── deployment.py              # Local, Null
│       ├── model.py                   # LlamaCpp, Null
│       ├── benchmark.py               # Latency, Throughput
│       └── research.py                # Universal, Null
│
├── core/
│   └── building_integration.py        # Cognitive Engine integration
│
├── tests/
│   └── test_building_system.py        # All 16 verification tests
│
├── MILESTONE_REPORT_jarvis_building_system.md
└── JARVIS_BUILDING_SYSTEM_MAP.md      # This file
```