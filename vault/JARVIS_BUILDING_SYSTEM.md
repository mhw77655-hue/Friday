JARVIS — MASTER BUILD, CONTINUITY & MECHANISM SPECIFICATION

«AUTHORITY: This document is an architectural execution specification for the Jarvis project.

EXECUTION RULE: Claude Code must read this entire file before modifying the repository and then execute the roadmap contained here.

IMPORTANT: This document is not a generic AI-agent specification. It defines Jarvis as an integrated, offline-first cognitive organism and defines the development system that will eventually allow Jarvis to construct, test, deploy, improve, and safely evolve itself.»

---

0. FUNDAMENTAL IDENTITY

Jarvis is not an LLM application.

Jarvis is an offline autonomous operating organism whose intelligence is distributed across:

MODEL
+
COGNITION
+
MEMORY
+
WORLD MODEL
+
SELF MODEL
+
USER MODEL
+
PERCEPTION
+
NERVOUS SYSTEM
+
CAPABILITY FABRIC
+
EXECUTION
+
RESEARCH
+
BUILDING
+
EXPERIMENTATION
+
OBSERVATION
+
LEARNING
+
EVOLUTION

The LLM is an intelligence component, not Jarvis itself.

The system must remain useful even when the underlying model changes.

Jarvis must therefore remain:

- model-agnostic
- mechanism-oriented
- modular
- offline-first
- resource-aware
- evidence-driven
- environment-isolated
- capable of future self-development

---

1. ORGANISM ARCHITECTURE

JARVIS
                           │
          ┌────────────────┼────────────────┐
          │                │                │
        SELF             BRAIN            BODY
          │                │                │
          │                │                │
     SelfModel        Cognition       Perception
     Identity         Reasoning       Voice
     Values           Planning        Vision
     Preferences      Attention       UI
     Boundaries       Intent           Device
          │                │                │
          └────────────────┼────────────────┘
                           │
                    NERVOUS SYSTEM
                           │
             ┌─────────────┼─────────────┐
             │             │             │
           SIGNAL        STATE         EVENTS
             │             │             │
             └─────────────┼─────────────┘
                           │
                    CAPABILITY FABRIC
                           │
            ┌──────────────┼──────────────┐
            │              │              │
          TOOLS          SKILLS         ACTIONS
            │              │              │
            └──────────────┼──────────────┘
                           │
                       EXECUTION
                           │
                      OBSERVATION
                           │
                      EXPERIENCE
                           │
                       LEARNING
                           │
                       EVOLUTION

The organism is deliberately not a giant monolithic agent loop.

Every major organ must own a specific responsibility.

Avoid creating one component that silently becomes responsible for cognition, memory, execution, research, capabilities, and self-modification simultaneously.

---

2. COGNITIVE SYSTEM

The cognitive substrate is the central structured state of Jarvis.

COGNITION
│
├── CognitiveState
├── WorkingMemory
├── AttentionEngine
├── IntentInference
├── CognitiveContextBuilder
├── SelfModel
├── UserModel
└── WorldModel

The completed cognitive stack already contains the foundations for:

- internal state
- working memory
- attention
- intent interpretation
- goal formation
- planning
- execution
- immune/resilience
- capability resolution
- memory continuity
- Self/User/World modeling

New systems must consume these mechanisms rather than bypass them.

---

3. COGNITIVE STATE

"CognitiveState" is runtime cognitive state, not a Markdown personality document.

It must represent, where applicable:

- current intent
- inferred intent
- intent confidence
- ambiguity
- current goal
- active subgoals
- active memories
- attention state
- uncertainty
- resource state
- capability state
- self state
- user state
- world state
- current plan
- current decision
- current execution status
- recent outcome

The cognitive state remains the central structured representation of "what is happening internally."

Do not create a parallel long-lived cognitive state inside the Building System.

---

4. WORKING MEMORY

Working memory is deliberately bounded.

INPUT
  ↓
RELEVANCE
  ↓
ATTENTION
  ↓
ACTIVATION
  ↓
WORKING MEMORY
  ↓
REASONING

Working-memory items may include:

- content
- source
- timestamp
- priority
- relevance
- recency
- goal alignment
- uncertainty
- activation
- decay
- confidence
- relationships

Core rule:

«Jarvis must not solve context problems by continuously dumping more information into the model.»

Instead:

WORLD / MEMORY
       ↓
RETRIEVAL
       ↓
RANKING
       ↓
ATTENTION
       ↓
BOUNDED CONTEXT
       ↓
MODEL

This principle applies to the entire architecture.

---

5. ATTENTION

Attention is a resource allocation mechanism.

Jarvis should constantly answer:

- What matters now?
- What matters next?
- What can wait?
- What is irrelevant?
- What is uncertain?
- What requires human input?

Conceptually:

ATTENTION
                       │
       ┌───────────────┼────────────────┐
       │               │                │
   GOAL RELEVANCE   URGENCY         UNCERTAINTY
       │               │                │
       └───────────────┼────────────────┘
                       │
                    RANKING
                       │
                ACTIVE CONTEXT

Attention should therefore determine what enters working memory and model context.

---

6. INTENT AND REFERENCE UNDERSTANDING

Jarvis must eventually understand:

- implicit intent
- ambiguity
- shorthand
- conversational references
- "this"
- "that"
- "it"
- task intent
- constraints
- desired outcome
- expected outcome

Do not reduce all understanding to keyword matching forever.

The architecture must allow progressively stronger local reasoning mechanisms without changing the surrounding cognitive system.

---

7. GOALS, PLANS AND DECISIONS

The planning architecture is:

GOAL
 │
 ├── SUBGOAL
 │    ├── TASK
 │    ├── DEPENDENCY
 │    └── CONDITION
 │
 └── PLAN GRAPH

Plans must be inspectable and mutable.

Decision flow:

UNDERSTANDING
      ↓
OPTIONS
      ↓
EVALUATION
      ↓
DECISION
      ↓
ACTION

Important decisions should retain:

- decision
- reason
- evidence
- confidence
- alternatives
- constraints
- risk
- resource state
- capability state

Decision memory becomes valuable future development knowledge.

---

8. MEMORY ORGANISM

Jarvis memory is a multi-layer organism, not one database.

MEMORY
│
├── Immediate / Working Memory
├── Session Memory
├── Episodic Memory
├── Semantic Memory
├── Procedural Memory
├── User Memory
├── Self Memory
├── World Knowledge
├── Experience Memory
├── Failure Memory
├── Decision Memory
├── Research Memory
├── Builder Memory
├── Evolution Memory
└── Knowledge Graph / Galaxy

The Galaxy / Knowledge Graph is not simply another memory database.

It represents relationships.

Example relationships:

ENTITY
  │
  ├── relates_to
  ├── caused
  ├── contradicts
  ├── supports
  ├── depends_on
  ├── learned_from
  ├── used_by
  ├── replaced_by
  └── evolved_into

Do not create another parallel graph database if existing memory/knowledge structures can represent the relationship.

---

9. MEMORY CONTINUITY

Memory follows:

experience
→ candidate memory
→ evaluate
→ promote/reject/defer
→ consolidate
→ reinforce
→ supersede
→ archive

No silent rewriting of the past.

When facts change:

OLD FACT
→ SUPERSEDED
→ NEW FACT

When evidence conflicts:

FACT A
+
FACT B
→
STRUCTURED CONFLICT

Every meaningful memory should retain provenance.

Memory must distinguish:

- current truth
- historical truth
- unknown
- unresolved conflict
- stale information

Cognition must request bounded reconstruction rather than dumping the full memory store.

---

10. SELF MODEL

Jarvis must know:

- what I am
- what I can do
- what I cannot do
- what resources I have
- what state I am in
- what I am currently doing
- what I am trying to accomplish
- what I believe
- what I know
- what I do not know
- what changed
- what capabilities are unavailable
- what environments are available
- what constraints exist

The SelfModel must remain distinct from "CognitiveState".

"CognitiveState" is current runtime state.

"SelfModel" is the persistent structured model of Jarvis himself.

---

11. USER MODEL

Jarvis learns operationally useful information about the user.

Examples:

- communication style
- desired response depth
- workflow patterns
- recurring goals
- common shorthand
- preferred tools
- preferred confirmation behavior
- project priorities
- successful interpretations
- failed interpretations
- known preferences

Do not turn the UserModel into a raw transcript archive.

Every learned preference should retain:

- source
- evidence
- confidence
- recency
- confirmation state
- supersession/history where appropriate

---

12. WORLD MODEL

The WorldModel represents the structured world Jarvis currently knows.

Possible entities include:

- people
- devices
- applications
- files
- folders
- projects
- tasks
- capabilities
- environments
- models
- organizations
- locations
- concepts
- artifacts
- services

Relationships may include:

- OWNS
- USES
- CONTAINS
- DEPENDS_ON
- PART_OF
- LOCATED_IN
- CREATED_BY
- CONTROLLED_BY
- RUNS_ON
- AVAILABLE_IN
- REQUIRES
- PRODUCES
- DERIVED_FROM
- RELATED_TO

World state must retain history rather than silently rewriting the past.

---

13. MODEL SYSTEM

The underlying model is replaceable infrastructure.

MODEL MANAGER
│
├── Model Registry
├── Model Metadata
├── Quantization
├── Runtime
├── Context Limits
├── Resource Requirements
├── Capabilities
├── Performance
├── Quality
├── Fallback Chain
└── Routing

Long-term model flow:

TASK
 ↓
MODEL ROUTER
 ↓
BEST AVAILABLE MODEL
 ↓
EXECUTION
 ↓
RESULT
 ↓
PERFORMANCE MEMORY

The system must never define:

Jarvis = one model

The model is an organ.

Jarvis is the organism.

---

14. LFM AS UNIVERSAL RESEARCH SEED

LFM is not merely a model to download.

LFM should be treated as a universal research seed for efficient intelligence architecture.

Mechanisms to study include:

- adaptive compute
- efficient local inference
- low-memory execution
- model specialization
- small-model routing
- task-specific models
- retrieval specialization
- encoder specialization
- edge deployment
- compute-per-task optimization

The principle is:

«Jarvis should optimize the computation architecture around intelligence, not merely search for a bigger model.»

This means:

TASK
 ↓
ESTIMATE COMPLEXITY
 ↓
CHOOSE CHEAPEST SUFFICIENT MECHANISM
 ↓
EXECUTE
 ↓
OBSERVE
 ↓
LEARN

Possible mechanisms:

deterministic algorithm
retrieval
tiny model
small model
standard model
deep reasoning model
specialized model
bounded worker
research

Do not hardcode a specific vendor.

---

15. RESOURCE GOVERNOR

RAM, CPU, storage, context, model residency, worker count, and environment count are first-class resources.

RESOURCE GOVERNOR
│
├── RAM
├── CPU
├── Storage
├── Context
├── Model Residency
├── Worker Count
├── Environment Count
└── Thermal / Device Pressure

Jarvis must be able to decide:

- load a smaller model
- load a larger model
- retrieve instead of generate
- use a deterministic mechanism
- create a worker
- defer a task
- compress context
- evict memory
- hibernate a model
- reduce concurrency
- reduce environment count

Do not create another resource governor.

---

16. NERVOUS SYSTEM

The nervous system is not the brain.

It connects the organism.

PERCEPTION
    ↓
SIGNAL
    ↓
NERVES
    ↓
STATE UPDATE
    ↓
COGNITION
    ↓
DECISION
    ↓
COMMAND
    ↓
EXECUTION
    ↓
OBSERVATION
    ↓
NERVES

It carries:

- events
- signals
- state transitions
- health signals
- interrupts
- observations
- capability availability
- resource pressure
- failures
- recovery signals

All major organs should communicate through the existing nervous-system/event architecture.

Do not create another event bus.

---

17. IMMUNE / RESILIENCE SYSTEM

A subsystem failure must not automatically kill Jarvis.

IMMUNE / RECOVERY
                         │
       ┌─────────────────┼─────────────────┐
       │                 │                 │
   DETECTION          CONTAINMENT       RECOVERY
       │                 │                 │
   anomaly             isolate          restart
   failure             degrade          repair
   timeout             quarantine       rollback
   corruption          disable          restore
       │                 │                 │
       └─────────────────┼─────────────────┘
                         │
                     LEARNING

Core principle:

«A failure in a subsystem is an event to investigate, not automatically a reason to kill Jarvis.»

---

18. FAILURE SURFACE

Every important operation must have an observable failure boundary.

REQUEST
 ↓
VALIDATION
 ↓
EXECUTION
 ↓
OBSERVATION
 ↓
FAILURE SURFACE
 ↓
DIAGNOSIS

Failure must become structured evidence.

Not:

"it crashed"

but:

- operation
- environment
- state
- inputs
- resources
- dependency
- error
- trace
- artifact
- expected behavior
- actual behavior
- probable cause
- confidence

Use the existing FailureSurface and recovery architecture.

Do not create a parallel failure system.

---

19. RECOVERY CONTROLLER

Recovery should operate progressively:

DETECT
 ↓
CLASSIFY
 ↓
CONTAIN
 ↓
RETRY
 ↓
DEGRADE
 ↓
REPAIR
 ↓
ROLLBACK
 ↓
ESCALATE

Not every failure deserves the same response.

---

20. CAPABILITY FABRIC

The Capability Fabric is the bridge between cognition and action.

CAPABILITY REGISTRY
       ↓
CAPABILITY
       ↓
VALIDATION
       ↓
INVOKER
       ↓
EXECUTION
       ↓
OBSERVATION

Development operations must use the existing Capability Fabric.

Do not create a second tool registry.

Examples:

- READ_FILE
- WRITE_FILE
- PATCH_FILE
- SEARCH_CODE
- QUERY_GRAPH
- RUN_COMMAND
- BUILD_PROJECT
- START_PROCESS
- STOP_PROCESS
- CREATE_ENVIRONMENT
- DESTROY_ENVIRONMENT
- RUN_BENCHMARK
- DEPLOY
- ROLLBACK
- INSPECT_FAILURE

---

21. EXECUTION ARCHITECTURE

Development and capability operations should flow through the existing execution path where applicable:

PlanGraph
→ ActionRequest
→ Capability
→ CapabilityInvoker
→ result

Do not bypass:

- immune layer
- permissions
- capability lifecycle
- resource policy
- failure surface

The Building System is an extension of the existing execution organism.

It is not a replacement for it.

---

22. UNIVERSAL RESEARCH

Universal Research is a major Jarvis organ.

For:

«"I want X."»

the pipeline eventually becomes:

REQUEST
 ↓
UNDERSTAND
 ↓
DECOMPOSE
 ↓
RESEARCH
 ↓
SOURCE COLLECTION
 ↓
EVIDENCE EXTRACTION
 ↓
MECHANISM IDENTIFICATION
 ↓
COMPARISON
 ↓
DESIGN

The critical rule:

«Jarvis does not copy an external system merely because it works.»

Instead:

EXTERNAL SYSTEM
      ↓
STUDY
      ↓
MECHANISMS
      ↓
PRINCIPLES
      ↓
ABSTRACTIONS
      ↓
JARVIS DESIGN
      ↓
JARVIS IMPLEMENTATION

External products and research sources are inputs.

They are not automatically Jarvis architecture.

---

23. MECHANISM EXTRACTION

Every serious research result should be reducible to:

SOURCE
→ MECHANISM
→ WHY IT WORKS
→ STRENGTHS
→ LIMITATIONS
→ ALTERNATIVES
→ GENERALIZATION
→ JARVIS-NATIVE DESIGN

This mechanism is central to Jarvis's eventual invention capability.

---

24. BUILDER

Builder is not merely "write code."

It is:

IDEA
 ↓
SPECIFICATION
 ↓
RESEARCH
 ↓
ARCHITECTURE
 ↓
PLAN
 ↓
WORKSPACE
 ↓
IMPLEMENTATION
 ↓
BUILD
 ↓
EXECUTION
 ↓
OBSERVATION
 ↓
REPAIR
 ↓
BENCHMARK

Builder must eventually operate on:

- code
- algorithms
- applications
- services
- capabilities
- models
- workflows
- memory strategies
- architecture
- deployment systems

---

25. MUTATED ENVIRONMENT

This is one of the most important architectural distinctions.

The Mutated Environment is not just a sandbox.

It is a disposable alternate reality.

LIVE JARVIS
     │
     ▼
 SNAPSHOT
     │
     ▼
 MUTATED REALITY
     │
     ├── CHANGE
     ├── INSTALL
     ├── MODIFY
     ├── BUILD
     ├── RUN
     ├── BREAK
     ├── REPAIR
     ├── BENCHMARK
     └── COMPARE
             │
             ▼
         OBSERVATION
             │
             ▼
           EVIDENCE

The environment can die.

Jarvis does not.

---

26. MUTATED ENVIRONMENT SIGNAL LINK

The environment itself does not contain the real nervous system.

Instead:

LIVE JARVIS
                         │
                    NERVOUS SYSTEM
                         │
                  SIGNAL / OBSERVER
                         │
                         ▼
              ┌────────────────────┐
              │ MUTATED ENVIRONMENT│
              │                    │
              │ alternate reality  │
              │ code               │
              │ runtime            │
              │ dependencies       │
              │ test data          │
              └────────────────────┘
                         │
                      RESULTS
                         │
                         ▼
                     OBSERVER
                         │
                         ▼
                    LIVE JARVIS

The experimental environment's representation of a modified Jarvis is an experimental subject / candidate system, not the actual nervous system.

This distinction must be preserved.

---

27. ENVIRONMENT IDENTITY AND LINEAGE

Every environment requires:

- environmentId
- parentEnvironmentId
- baseSnapshot
- specification
- version
- creation time
- lifecycle
- resource profile
- purpose
- owner operation
- lineage
- artifact set
- current candidate state

Example:

LIVE
 └── experiment-001
      ├── mutation-A
      ├── mutation-B
      └── mutation-C

Jarvis must know which candidate came from which parent.

---

28. ENVIRONMENT LIFECYCLE

Use an explicit lifecycle:

SPECIFIED
→ CREATED
→ PREPARED
→ BUILDING
→ READY
→ RUNNING
→ OBSERVING
→ MUTATING
→ BENCHMARKING
→ VALIDATING
→ PROMOTABLE
→ PROMOTED

Failure/cancellation:

FAILED
ABANDONED
DESTROYING
DESTROYED

No environment may remain accidentally half-alive.

---

29. SNAPSHOT / FORK

Support:

LIVE
→ SNAPSHOT
→ CANDIDATE ENVIRONMENT
→ MUTATION
→ CHILD CANDIDATE

Candidates must be forkable.

This allows independent evaluation of:

- algorithm A
- algorithm B
- algorithm C
- architecture A
- architecture B
- configuration A
- configuration B

Do not build a Git replacement.

Use Git/source-control where appropriate.

Environment management owns runtime reality.

---

30. DEVELOPMENT WORKSPACE

A Workspace represents a development project inside an environment.

It must know:

- root path
- repository
- branch/snapshot
- environment
- active build
- dependencies
- artifacts
- current task
- current candidate version

---

31. REPOSITORY INTELLIGENCE

The Building System must understand repositories before modifying them.

Support:

- file discovery
- symbol discovery
- dependency discovery
- reference discovery
- architecture discovery
- package discovery
- impact analysis
- relevant-file retrieval
- project structure
- build-system detection

Graphify can accelerate this.

Do not make Graphify the only source of repository truth.

There must remain a fallback inspection mechanism.

Do not load the entire repository into model context.

---

32. CONTEXT ECONOMICS FOR DEVELOPMENT

The builder should use:

TASK
 ↓
REPOSITORY RETRIEVAL
 ↓
RELEVANT FILES
 ↓
RELEVANT SYMBOLS
 ↓
RELEVANT MEMORY
 ↓
CURRENT ENVIRONMENT STATE
 ↓
CONCISE CONTEXT

Never continuously send:

- the complete repository
- entire memory
- all environment state
- full historical logs

to the model.

---

33. DEVELOPMENT KERNEL

The Building System needs a persistent Development Kernel.

It maintains:

- development objective
- repository target
- environment
- active plan
- active workers
- pending operations
- important decisions
- blockers
- evidence
- candidate state
- prior attempts
- lessons

Kernel state remains outside active model context.

The model receives only a bounded projection.

---

34. DEVELOPMENT SESSION

A DevelopmentSession represents one complete development objective.

It contains:

- session ID
- objective
- project
- workspace
- environment
- plan
- workers
- changes
- builds
- failures
- repairs
- benchmarks
- candidate
- promotion state
- lessons

The session must be recoverable after interruption where practical.

---

35. DEVELOPMENT LINEAGE

Jarvis must eventually know:

- why code changed
- which task caused it
- which research finding caused it
- which algorithm was selected
- which candidate produced it
- which benchmark approved it
- which promotion created the live version

This lineage becomes essential for future self-evolution.

---

36. CHANGE INTELLIGENCE

Every meaningful modification must have provenance.

At minimum:

- changeId
- environmentId
- workspaceId
- taskId
- file
- symbol where known
- operation
- reason
- parent change
- resulting state

This should let Jarvis answer:

"What changed?"

"Why did it change?"

"What caused it?"

"What depends on it?"

"What happened afterward?"

---

37. CHANGE SETS

Related modifications should be grouped into a ChangeSet.

A ChangeSet contains:

- changeSetId
- objective
- files
- operations
- environment
- parent version
- authoring worker
- evidence
- status

ChangeSet becomes a unit for:

- review
- benchmark
- promotion
- rollback

---

38. CANDIDATE SYSTEM

Every meaningful experimental result becomes a Candidate.

Candidate
│
├── Candidate ID
├── Parent
├── Lineage
├── Source Snapshot
├── Changes
├── Environment
├── Artifacts
├── Evidence
├── Benchmarks
├── Failures
├── Resource Cost
├── Compatibility
├── Confidence
└── Promotion Status

Possible states:

PROPOSED
→ BUILDING
→ RUNNING
→ EVALUATED
→ PROMOTABLE
→ PROMOTED

or:

REJECTED
FAILED
ABANDONED

---

39. CANDIDATE GENEALOGY

Example:

CURRENT
 ├── Candidate-A
 │    ├── Candidate-A1
 │    └── Candidate-A2
 │
 └── Candidate-B
      └── Candidate-B1

Jarvis must determine:

- parent
- descendants
- modifications
- benchmark differences
- why a candidate was selected
- why a candidate was rejected

---

40. EXPERIMENT SPECIFICATION

Create a declarative ExperimentSpecification defining:

- objective
- target
- base state
- candidate changes
- environment requirements
- resource limits
- workload
- success criteria
- benchmark criteria
- timeout
- iteration limits
- retention policy

The experiment must be describable independently of active model context.

---

41. OPERATION JOURNAL

Create persistent operation history.

Record major operations such as:

- environment creation
- mutation
- file modification
- command execution
- build
- repair
- benchmark
- deployment
- promotion
- rollback
- destruction

Do not store huge raw logs forever.

Store structured metadata plus retained evidence references.

---

42. EVIDENCE MODEL

Evidence may represent:

- command output
- compiler error
- runtime observation
- benchmark result
- resource measurement
- deployment health
- model output
- research finding
- comparison result

Important development decisions must be traceable to evidence.

---

43. OBSERVATION MODEL

Observations are structured, not only raw logs.

Observation may contain:

- timestamp
- source
- environment
- process
- metric
- value
- severity
- context
- evidence reference

Observation may feed:

- diagnosis
- benchmarking
- memory
- self-diagnostics
- future improvement

---

44. OPERATION CORRELATION

Important activity must be connectable:

SESSION
→ TASK
→ OPERATION
→ ENVIRONMENT
→ WORKSPACE
→ CHANGE
→ COMMAND
→ BUILD
→ EVIDENCE
→ CANDIDATE
→ PROMOTION

The model should not have to reconstruct this from prose.

---

45. DEVELOPMENT WORKERS

Workers are bounded temporary execution units.

Possible roles:

- Research Worker
- Repository Worker
- Implementation Worker
- Review Worker
- Benchmark Worker
- Diagnosis Worker
- Deployment Worker
- Observation Worker

Workers are not permanent personalities.

The central Jarvis remains the orchestrator.

---

46. WORKER CONTRACT

Every worker receives:

- task ID
- objective
- inputs
- allowed capabilities
- environment/workspace
- resource budget
- deadline
- expected output

Every worker returns:

- status
- result
- evidence
- changes
- blockers
- confidence
- recommended next action

Workers do not automatically create more uncontrolled workers.

---

47. WORKER MEMORY

Workers must not receive:

- complete repository
- complete memory
- complete world model
- complete task history

Each receives only what it needs.

This is central to RAM and context efficiency.

---

48. WORKER ORCHESTRATION

The Development Orchestrator owns:

- worker creation
- task assignment
- context allocation
- resource allocation
- cancellation
- result collection
- conflict handling
- worker termination

---

49. PARALLELIZATION

When the existing PlanGraph determines tasks are independent:

A ─────→ result
B ─────→ result
C ─────→ result

they may run concurrently.

When dependencies exist:

A → B → C

they remain sequential.

Reuse existing planning/dependency architecture.

---

50. WORKER CONFLICT

If workers disagree:

1. identify conflict
2. preserve both results
3. compare evidence
4. request additional analysis if necessary
5. select or synthesize
6. record the decision

Worker disagreement is evidence, not chaos.

---

51. RESEARCH WORKER

Produces structured research:

- question
- sources
- findings
- mechanisms
- limitations
- alternatives
- confidence
- recommendation

Research Worker should not directly modify Jarvis.

---

52. REPOSITORY WORKER

Answers:

- where is X implemented?
- what depends on X?
- what files matter?
- what architecture is involved?
- what could this change affect?

Prefer Graphify/repository intelligence over repeated full scans.

---

53. IMPLEMENTATION WORKER

Implementation Worker may modify only its assigned environment/workspace.

Returns:

- changes
- files affected
- build status
- blockers
- evidence

It cannot promote its own work.

---

54. REVIEW WORKER

Review Worker evaluates:

- requested behavior
- architecture
- compatibility
- unintended changes
- resource impact
- candidate quality

Output becomes candidate evidence.

---

55. BENCHMARK WORKER

Benchmark Worker runs the defined workload against candidates using identical measurement rules.

It must not alter candidate implementation merely to improve scores.

---

56. DEPLOYMENT WORKER

Deployment Worker handles deployment mechanics.

It does not decide whether promotion should happen.

Promotion remains a separate policy decision.

---

57. WORKER CANCELLATION

Workers must be cancellable.

Cancellation must:

- stop execution
- release resources
- preserve useful evidence
- update task state
- avoid orphan processes

---

58. DEADLINE / BUDGET CONTROL

Every DevelopmentSession may have:

- time budget
- compute budget
- memory budget
- worker budget
- mutation budget
- repair iteration budget

When budget is exhausted:

report:

«Insufficient budget to complete.»

Do not enter endless loops.

---

59. IDEMPOTENCY

Retryable operations should be idempotent where practical.

Examples:

- environment initialization
- dependency installation
- artifact registration
- state transitions
- deployment preparation

Repeated execution must not corrupt development state.

---

60. CONCURRENCY CONTROL

Prevent multiple workers from silently changing the same workspace.

Use:

- ownership
- locks
- change sets
- branch/candidate isolation
- merge/reconciliation

Prefer independent candidate environments for competing implementations.

---

61. MERGE / SYNTHESIS

If candidate A and candidate B contain useful mechanisms:

Candidate A
+
Candidate B
→
Candidate C

Candidate C must be created in a new mutated environment.

Never merge experiments directly into LIVE.

---

62. ALGORITHM LAB

Provide an abstraction for algorithm experiments.

An AlgorithmExperiment should define:

- algorithm identity
- implementation
- input workload
- expected behavior
- metrics
- baseline
- candidate
- results

This becomes the foundation for future Algorithm Discovery.

---

63. BASELINE SYSTEM

Every benchmarkable candidate needs a baseline where appropriate.

BASELINE
vs
CANDIDATE

Use the same workload and resource conditions.

Baseline must be preserved for future regression detection.

---

64. REGRESSION PROTECTION

Evaluate more than the requested feature.

Track:

- baseline behavior
- candidate behavior
- regression signals

A faster candidate that damages important behavior is not automatically an improvement.

---

65. PROMOTION DECISION

Promotion combines evidence.

Consider:

- correctness
- required behavior
- benchmark improvement
- resource cost
- reliability
- compatibility
- regression risk
- confidence
- reversibility

Possible outcomes:

PROMOTE
REJECT
NEEDS_MORE_EVIDENCE

---

66. PROMOTION RECORD

Every promotion creates:

- candidate
- previous live version
- new live version
- evidence
- decision
- reason
- timestamp
- promotion policy
- rollback target

This becomes part of future self-understanding.

---

67. DEPLOYMENT

Deployment flow:

artifact
→ package
→ stage
→ deploy
→ observe
→ confirm

Deployment is separate from promotion.

Do not allow deployment to silently promote a candidate.

---

68. DEPLOYMENT ESCALATION

Explicit lifecycle:

EXPERIMENT
→ CANDIDATE
→ STAGED
→ LIVE

Promotion is deliberate.

---

69. ROLLBACK

Rollback:

LIVE
→ PREVIOUS LIVE

must preserve evidence.

Do not erase failed deployment history.

---

70. DEPLOYMENT OBSERVATION

After deployment:

deploy
→ observe
→ compare against expected behavior

If health deteriorates:

observe
→ diagnose
→ decide
→ rollback or repair

Do not mutate LIVE automatically without explicit policy.

---

71. ARTIFACT MANAGEMENT

Artifacts may include:

- binaries
- APKs
- libraries
- model artifacts
- configurations
- packages
- data

Artifacts have:

- artifactId
- environmentId
- source commit/version
- build identity
- dependencies
- checksum where applicable
- creation metadata

Possible lifecycle:

CREATED
→ VERIFIED
→ CANDIDATE
→ STAGED
→ LIVE
→ ARCHIVED
→ DEPRECATED

Artifact lifetime must remain independent from environment lifetime.

---

72. ENVIRONMENT RETENTION

Support policies such as:

- DESTROY_RUNTIME_KEEP_EVIDENCE
- KEEP_ENVIRONMENT
- KEEP_ARTIFACTS_ONLY
- KEEP_FULL_SNAPSHOT
- DESTROY_ALL_NONESSENTIAL_STATE

The default should avoid wasting storage.

---

73. ENVIRONMENT ADAPTER

Create an EnvironmentAdapter concept supporting:

- create
- prepare
- snapshot
- fork
- start
- stop
- inspect
- destroy

The first implementation should use the safest practical mechanism available to the repository.

Do not force a specific technology unless required.

The adapter must remain replaceable.

---

74. RUNTIME ADAPTER

RuntimeAdapter supports:

- process lifecycle
- service lifecycle
- health
- logs
- resource state
- crash detection

Possible runtimes:

- local process
- container
- service
- Android
- remote runtime

Do not assume only one runtime.

---

75. REPOSITORY ADAPTER

RepositoryAdapter supports:

- inspect
- status
- branch/snapshot
- diff
- history
- relevant-file retrieval
- change application

Git may be the first implementation.

Do not leak Git semantics into every domain model.

---

76. BUILD ADAPTER

BuildAdapter should expose a normalized interface for:

- detect
- prepare
- build
- clean
- package
- artifact discovery
- diagnostics

Support multiple project types.

---

77. DEPLOYMENT ADAPTER

DeploymentAdapter supports:

- prepare
- deploy
- start
- stop
- status
- health
- rollback

It does not decide promotion.

---

78. MODEL ADAPTER

ModelAdapter supports:

- discover
- load
- invoke
- stream where available
- cancel
- unload
- health
- resource estimate

The Building System may select different models for different tasks.

---

79. BENCHMARK ADAPTER

BenchmarkAdapter supports:

- workload preparation
- execution
- measurement
- metric collection
- normalization
- baseline comparison

The benchmark mechanism must remain independent of the candidate implementation.

---

80. RESEARCH ADAPTER

ResearchAdapter connects the Building System to Universal Research.

Supports:

- question
- research
- extraction
- comparison
- structured findings

Do not duplicate Universal Research.

---

81. CANDIDATE EVALUATION

Evaluation should consider:

- correctness
- functional requirements
- latency
- resource efficiency
- reliability
- compatibility
- regression risk
- evidence quality
- confidence
- reversibility

Evaluation should output:

- PROMOTE
- REJECT
- NEEDS_MORE_EVIDENCE

---

82. REPRODUCIBILITY

Every experiment should record:

- source version
- environment specification
- dependency versions
- configuration
- model used
- worker configuration
- algorithm candidate
- workload
- resource policy

Jarvis should be able to reconstruct a previous experiment.

---

83. OPERATION RECOVERY

If Jarvis stops during development:

On restart:

1. discover persistent sessions
2. identify incomplete operations
3. inspect environments
4. inspect processes
5. reconcile state
6. mark uncertain operations
7. resume, retry, rollback, or destroy safely

Never infer success from incomplete state.

---

84. ORPHAN CLEANUP

Detect and clean:

- orphan processes
- orphan workers
- orphan environments
- temporary files
- stale locks
- expired artifacts
- abandoned sessions

Preserve evidence needed for learning before destruction.

---

85. STATE RECONCILIATION

Recorded state and real state may diverge.

Examples:

RECORDED: RUNNING
ACTUAL: process dead

RECORDED: DESTROYED
ACTUAL: environment exists

RECORDED: BUILDING
ACTUAL: build process gone

Detect and safely reconcile.

---

86. DEVELOPMENT MEMORY

Use existing Jarvis memory.

Development events should become structured experiences such as:

- objective
- repository state
- selected approach
- research findings
- implementation decisions
- failed approaches
- successful repairs
- benchmark outcomes
- deployment outcomes
- rollback events
- lessons

Do not permanently store every command output.

Store future-value information.

---

87. DEVELOPMENT KNOWLEDGE GRAPH

Integrate development knowledge into the existing Galaxy/Knowledge Graph.

Represent:

PROJECT
→ contains
COMPONENT

COMPONENT
→ depends_on
COMPONENT

TASK
→ modified
COMPONENT

RESEARCH
→ influenced
DECISION

DECISION
→ produced
CANDIDATE

CANDIDATE
→ tested_in
ENVIRONMENT

CANDIDATE
→ produced
ARTIFACT

BENCHMARK
→ evaluated
CANDIDATE

PROMOTION
→ promoted
CANDIDATE

ROLLBACK
→ reverted
PROMOTION

Do not create another graph system.

---

88. LESSON EXTRACTION

After completed development:

experience
→ extract lesson
→ determine reuse potential
→ store

Examples:

- build system required X
- failure pattern Y usually means Z
- algorithm A outperforms B under memory pressure
- library X conflicts with Y
- repair strategy Z succeeded under condition Q

These lessons become reusable engineering knowledge.

---

89. DESIGN MEMORY

Important architecture decisions should become durable memory.

Record:

DECISION
→ ALTERNATIVES
→ SELECTED MECHANISM
→ REASON
→ EVIDENCE
→ CONSEQUENCES

This prevents Jarvis from rediscovering the same architecture repeatedly.

---

90. OPERATIONAL MEMORY

The Building System should remember not only facts, but useful engineering behavior.

Example:

Problem
→ attempted solution
→ result
→ successful solution
→ context in which it worked

This is separate from raw logs.

It is reusable operational knowledge.

---

91. LEARNING

Learning should occur at multiple levels:

EXPERIENCE
│
├── FACT LEARNING
├── PROCEDURAL LEARNING
├── USER LEARNING
├── FAILURE LEARNING
├── STRATEGY LEARNING
├── TOOL LEARNING
├── ARCHITECTURE LEARNING
└── MODEL / ALGORITHM LEARNING

The system should preserve useful engineering rules.

Do not require weight changes for every form of learning.

---

92. PERSISTENT BEHAVIORAL RULES

When repeated experience establishes a useful rule:

experience
→ evidence
→ recurring pattern
→ validated rule
→ persistent behavioral knowledge

Rules must be:

- evidence-backed
- scoped
- revisable
- attributable to source experience

Do not turn every single event into permanent behavior.

---

93. RESEARCH MEMORY

Research should remain traceable:

RESEARCH QUESTION
→ SOURCE
→ MECHANISM
→ DECISION
→ IMPLEMENTATION
→ RESULT

This creates a chain from knowledge discovery to engineering outcome.

---

94. SELF-DEVELOPMENT FOUNDATION

The Building System must eventually be able to target:

ANY SOFTWARE PROJECT

and:

JARVIS ITSELF

Use ProjectTarget rather than hardcoding Jarvis.

Possible targets:

- Jarvis
- external repository
- workspace
- application
- service
- algorithm
- capability

---

95. JARVIS SELF-DEVELOPMENT LOOP

Once the generic Building System exists:

JARVIS
→ identifies limitation
→ researches solution
→ extracts mechanism
→ designs candidate
→ snapshots current system
→ creates mutated Jarvis environment
→ implements change
→ builds
→ executes
→ observes
→ benchmarks
→ compares against current
→ stages
→ promotion decision

Live Jarvis remains protected during experimentation.

---

96. SELF-EVOLUTION SAFETY

Self-evolution remains bounded.

Controls include:

- environment isolation
- resource limits
- change limits
- iteration limits
- promotion gates
- rollback
- lineage
- evidence
- human override where configured

Never create unrestricted recursive self-modification.

---

97. SELF-REPLACEMENT

Individual organs may eventually be treated as candidate implementations.

Example:

CURRENT MEMORY RETRIEVAL
        ↓
CANDIDATE A
CANDIDATE B
CANDIDATE C
        ↓
SAME WORKLOAD
        ↓
BENCHMARK
        ↓
COMPARE
        ↓
PROMOTION

This permits evolution of individual mechanisms without replacing the entire organism.

---

98. WHOLE-SYSTEM EVOLUTION

Eventually:

Jarvis
→ identifies opportunity
→ creates architecture candidate
→ forks system
→ mutates multiple organs
→ builds candidate
→ runs workloads
→ observes behavior
→ compares baseline
→ selects candidate
→ stages
→ promotes

Do not activate unrestricted whole-system evolution during the current build.

Build the machinery that makes bounded evolution possible.

---

99. BUILDING SYSTEM AS AN ORGAN

The Building System should eventually be a true Jarvis organ.

Relationship:

COGNITION
→ decides what should exist

RESEARCH
→ discovers how it could exist

BUILDING SYSTEM
→ constructs it

MUTATED ENVIRONMENT
→ provides alternative reality

CAPABILITY FABRIC
→ provides actions

OBSERVATION
→ determines what happened

MEMORY
→ preserves what was learned

KNOWLEDGE GRAPH
→ preserves relationships

SELF MODEL
→ understands what changed

NERVOUS SYSTEM
→ coordinates the organism

This is how Jarvis can eventually create new organs.

---

100. FUTURE ORGAN CREATION

The architecture must eventually support creation of:

- new capability
- new algorithm
- new memory mechanism
- new research mechanism
- new perception organ
- new voice capability
- new vision capability
- new UI
- new application
- new service
- new model adapter
- new environment type
- new development strategy

without requiring the Building System itself to be fundamentally rewritten for each category.

---

101. BUILDING SYSTEM SELF-EXTENSION

If Jarvis discovers:

«"The Building System itself is inefficient."»

it should eventually be able to use the same mechanism:

CURRENT BUILDING SYSTEM
→ SNAPSHOT
→ CANDIDATE
→ MUTATED ENVIRONMENT
→ IMPROVEMENT
→ BENCHMARK
→ COMPARE
→ PROMOTE

The Building System is not exempt from evolution.

It is protected by the same promotion boundary.

---

102. EVENT MODEL

The Building System must integrate with the existing event/nervous-system architecture.

Meaningful events include, where applicable:

- development session requested
- session state changed
- environment requested
- environment created
- environment prepared
- environment started
- environment stopped
- environment mutated
- environment destroyed
- workspace created
- workspace changed
- repository analyzed
- research requested
- research completed
- mechanism extracted
- candidate created
- candidate mutated
- change created
- change applied
- command started
- command completed
- build started
- build completed
- build failed
- process started
- process stopped
- observation created
- diagnosis created
- repair attempted
- repair succeeded
- repair failed
- benchmark started
- benchmark completed
- candidate evaluated
- candidate rejected
- candidate marked promotable
- staging started
- deployment started
- deployment completed
- promotion requested
- promotion approved
- promotion rejected
- rollback requested
- rollback completed
- artifact created
- lesson extracted
- worker created
- worker completed
- worker cancelled
- budget exhausted
- recovery started
- recovery completed

Do not create a second event bus.

---

103. OBSERVABILITY

The Building System must expose structured information about:

- current development session
- current phase
- active environments
- active workers
- active builds
- running processes
- resource consumption
- candidate status
- benchmark status
- deployment status
- failures
- recovery

The system should be understandable to:

- Jarvis
- future UI
- human operator
- future Stage 16 diagnostic system

Avoid enormous continuous logs when structured state is sufficient.

---

104. LOCAL HEALTH

Health information should exist for:

- environments
- workspaces
- builds
- processes
- artifacts
- deployments

Use existing health infrastructure where possible.

Do not build the final system-wide diagnostic fabric here.

---

105. DEVELOPMENT CONTROL INTERFACE

The Building System should provide a local operational interface, programmatic and/or CLI-based, for:

- sessions
- environments
- workspaces
- experiments
- candidates
- benchmarks
- deployments
- promotions
- rollbacks
- recovery
- cleanup

Underlying domain services must be shared.

Do not create a second logic path for the CLI.

---

106. DEVELOPMENT DASHBOARD CONTRACT

Expose enough structured state that a future UI can visualize:

- sessions
- environment genealogy
- candidate genealogy
- active workers
- builds
- benchmarks
- deployment status
- failures
- resources
- promotion state
- rollback history

Polished UI is not the main purpose of the current build.

Build the underlying organism.

---

107. SECURITY / TRUST BOUNDARY

The environment boundary is a trust boundary.

The system must distinguish:

EXPERIMENTAL
STAGED
LIVE

Architecture must enforce this distinction.

Do not rely only on the model remembering not to modify LIVE.

---

108. PERMISSIONS

Development capabilities must have explicit permission classes.

Examples:

- READ_WORKSPACE
- WRITE_WORKSPACE
- EXECUTE_PROCESS
- INSTALL_DEPENDENCY
- CREATE_ENVIRONMENT
- DEPLOY
- PROMOTE
- ROLLBACK

Experimental permission may be broader than LIVE permission.

Promotion must be separately controlled.

---

109. SECRET ISOLATION

Do not unnecessarily expose:

- API keys
- authentication tokens
- passwords
- private credentials
- live service secrets

inside experimental environments.

Do not persist secrets inside:

- experiment evidence
- memory
- source snapshots
- candidate metadata
- operation logs

---

110. NETWORK POLICY

Experimental network access should be policy-controlled.

Possible conceptual modes:

OFFLINE
RESTRICTED
ALLOWED

Default to minimal access needed for the task.

Record whether experimental success depended on network access.

---

111. EXPERIMENT CONTAMINATION CONTROL

An experiment should not silently depend upon undeclared:

- host files
- environment variables
- services
- network resources
- credentials

Relevant external dependencies should be recorded.

This improves reproducibility.

---

112. OFFLINE-FIRST

The Building System should remain useful offline.

It should be capable of:

- repository analysis
- code modification
- local builds
- local execution
- local experiments
- local benchmarks
- local deployment
- local memory
- local model use

without requiring cloud services.

Cloud may be an optional accelerator later.

---

113. CLOUD CODE BOUNDARY

Cloud Code is an external development accelerator.

It may be used now to build Jarvis.

It must never become a Jarvis runtime dependency.

Later, Cloud Code may become:

- external research source
- optional worker
- optional development capability

but never the foundation of Jarvis's existence.

---

114. OMNIROUTE BOUNDARY

OmniRoute may accelerate model routing during development.

It must not become a hardcoded Jarvis runtime dependency.

Jarvis should communicate with models through the existing ModelManager / ModelProvider abstractions.

The local model architecture must remain capable of operating without OmniRoute.

---

115. GRAPHIFY BOUNDARY

Graphify may accelerate repository intelligence.

It must not become the only repository understanding mechanism.

The Building System needs an abstraction that can query or replace Graphify.

If Graphify is unavailable, Jarvis must retain a fallback repository inspection path.

---

116. EXTERNAL RESEARCH BOUNDARY

Mem0, Prime-Agent, Multi-Agent systems research, LFM, Graphify, OpenAlternative, and other external sources are research inputs.

They are not automatically Jarvis architecture.

The process remains:

DISCOVER
→ RESEARCH
→ UNDERSTAND MECHANISM
→ IDENTIFY STRENGTHS
→ IDENTIFY LIMITATIONS
→ FIND ALTERNATIVES
→ GENERALIZE
→ SYNTHESIZE
→ JARVIS-NATIVE IMPLEMENTATION

---

117. BUILDING SYSTEM RESEARCH SYNTHESIS

The Building System should explicitly absorb useful mechanisms from:

Mem0

Relevant mechanism classes:

- extraction
- consolidation
- selective retrieval
- memory history
- relationship-aware memory
- evidence-backed memory

Do not build Mem0 inside Jarvis.

Use the existing memory architecture.

Prime-Agent

Relevant mechanism classes:

- externalized state
- kernel outside context
- bounded workers
- delegation
- task lineage
- iterative repair
- learning from outcomes

Do not reproduce the exact Prime-Agent architecture.

Multi-Agent Systems Research

Relevant mechanism classes:

- role specialization
- orchestration
- structured worker communication
- bounded worker context
- parallelization
- evaluation
- shared objective

Do not build a permanent uncontrolled swarm.

LFM

Relevant mechanism classes:

- efficient local inference
- adaptive computation
- model specialization
- low-memory execution
- retrieval specialization
- edge deployment
- task-specific intelligence

Do not make a vendor dependency.

---

118. RESEARCH → BUILDING PIPELINE

The complete research-to-building pipeline is:

PROBLEM
 ↓
UNDERSTAND
 ↓
RESEARCH
 ↓
SOURCE COLLECTION
 ↓
MECHANISM EXTRACTION
 ↓
STRENGTHS
 ↓
LIMITATIONS
 ↓
ALTERNATIVES
 ↓
GENERALIZATION
 ↓
DESIGN
 ↓
CANDIDATE
 ↓
MUTATED ENVIRONMENT
 ↓
IMPLEMENT
 ↓
RUN
 ↓
BENCHMARK
 ↓
COMPARE
 ↓
PROMOTE / REJECT

---

119. ALGORITHM DISCOVERY

Future Algorithm Discovery should use:

PROBLEM
 ↓
KNOWN ALGORITHMS
 ↓
RESEARCH
 ↓
GENERATE VARIANTS
 ↓
MUTATED ENVIRONMENTS
 ↓
BENCHMARK
 ↓
COMPARE
 ↓
SELECT

Algorithms may target:

- retrieval
- ranking
- planning
- scheduling
- routing
- memory
- compression
- orchestration
- resource management
- model selection

The Building System becomes the execution substrate for Algorithm Discovery.

---

120. MODEL EVOLUTION RESEARCH

Future model evolution may use:

MODEL PROBLEM
 ↓
RESEARCH
 ↓
ARCHITECTURE ANALYSIS
 ↓
COMPUTATIONAL BOTTLENECK
 ↓
HYPOTHESIS
 ↓
EXPERIMENT
 ↓
MODEL VARIANT
 ↓
TRAIN / FINE-TUNE / DISTILL / ADAPT
 ↓
BENCHMARK
 ↓
COMPARE

This is where the concept of Jarvis learning from efficient architectures such as LFM becomes useful.

The system should research the mechanism rather than blindly imitate a vendor.

---

121. SELF-MODIFICATION CONCEPT

Self-modification does not mean:

«Jarvis randomly edits its own model weights or source code.»

The preferred long-term architecture is:

EXPERIENCE
 ↓
OBSERVATION
 ↓
PATTERN
 ↓
HYPOTHESIS
 ↓
RESEARCH
 ↓
EXPERIMENT
 ↓
CANDIDATE
 ↓
BENCHMARK
 ↓
EVIDENCE
 ↓
PROMOTION

Potential targets include:

- prompting
- routing
- memory policy
- retrieval
- algorithms
- tools
- workflows
- code
- models
- quantization
- runtime
- specialization
- adapters
- fine-tuning
- distillation
- training
- system architecture

Model-level changes are future research targets.

They are not unrestricted self-modification.

---

122. COMPLETE AUTONOMOUS LOOP

Eventually all organs converge into:

REQUEST
                          │
                          ▼
                     UNDERSTAND
                          │
                          ▼
                       GOAL
                          │
                          ▼
                     ATTENTION
                          │
                          ▼
                    CONTEXT BUILD
                          │
                          ▼
                      RESEARCH
                          │
                          ▼
                       DESIGN
                          │
                          ▼
                  CREATE WORKSPACE
                          │
                          ▼
                 CREATE MUTATED WORLD
                          │
                          ▼
                       BUILD
                          │
                          ▼
                        RUN
                          │
                          ▼
                     OBSERVE
                          │
                          ▼
                     DIAGNOSE
                          │
                          ▼
                       REPAIR
                          │
                          ▼
                    BENCHMARK
                          │
                          ▼
                      COMPARE
                          │
                    ┌─────┴─────┐
                    │           │
                  REJECT      PROMOTE
                    │           │
                    ▼           ▼
                 REMEMBER      LIVE
                    │           │
                    └─────┬─────┘
                          ▼
                       LEARN
                          │
                          ▼
                       EVOLVE

---

123. THREE REALITIES

The architecture can ultimately be understood as three realities:

                         JARVIS
                           │
             ┌─────────────┼─────────────┐
             │             │             │
           LIVE          MEMORY       KNOWLEDGE
             │
             │
          OBSERVE
             │
             ▼
      MUTATED REALITIES
             │
       ┌─────┼─────┐
       │     │     │
      EXP1  EXP2  EXP3
       │     │     │
       └─────┼─────┘
             │
          EVIDENCE
             │
         CANDIDATES
             │
         PROMOTION
             │
             ▼
           LIVE

Live Jarvis is the organism.

Memory/knowledge is accumulated experience.

Mutated realities are where Jarvis is allowed to experiment.

This separation is fundamental.

---

124. DEVELOPMENT SYSTEM — COMPLETE SPECIFICATION

The Building System is the infrastructure that turns Jarvis from an AI that can use tools into an AI that can construct tools, applications, capabilities, algorithms, services, and eventually improved versions of itself.

It must include:

- DevelopmentSession
- ProjectTarget
- Workspace
- MutatedEnvironment
- Candidate
- CandidateLineage
- ChangeSet
- ExperimentSpecification
- OperationJournal
- Evidence
- Observation
- BuildAdapter
- RepositoryAdapter
- RuntimeAdapter
- EnvironmentAdapter
- DeploymentAdapter
- ModelAdapter
- BenchmarkAdapter
- ResearchAdapter
- DevelopmentOrchestrator
- bounded Workers
- Promotion
- Rollback
- Artifact management
- Memory integration
- Knowledge graph integration
- Resource governance
- Failure/recovery integration

---

125. DEVELOPMENT SESSION STATE MACHINE

REQUESTED
→ ANALYZING
→ PLANNING
→ RESEARCHING
→ DESIGNING
→ PREPARING
→ BUILDING
→ EXECUTING
→ OBSERVING
→ REPAIRING
→ EVALUATING
→ CANDIDATE_READY
→ STAGING
→ PROMOTING
→ DEPLOYED
→ COMPLETED

Failure states:

FAILED
BLOCKED
CANCELLED
ABANDONED
ROLLED_BACK

Transitions must be explicit.

---

126. EXPERIMENT STATE MACHINE

SPECIFIED
→ CREATED
→ PREPARED
→ RUNNING
→ OBSERVING
→ BENCHMARKING
→ VALIDATING
→ PROMOTABLE
→ DESTROYED

with:

- FAILED
- ABANDONED
- CANCELLED
- DESTROYING

---

127. ARTIFACT STATE MACHINE

CREATED
→ VERIFIED
→ CANDIDATE
→ STAGED
→ LIVE
→ ARCHIVED
→ DEPRECATED

---

128. BUILD / REPAIR LOOP

CHANGE
→ BUILD
→ RUN
→ OBSERVE
→ DIAGNOSE
→ REPAIR
→ BUILD AGAIN

Bounded by:

- iterations
- time
- resources
- mutation budget
- failure threshold

---

129. PROMOTION LOOP

CANDIDATE
→ BUILD
→ RUN
→ OBSERVE
→ BENCHMARK
→ REGRESSION CHECK
→ RESOURCE CHECK
→ COMPATIBILITY CHECK
→ PROMOTION DECISION

Outcome:

PROMOTE
REJECT
NEEDS_MORE_EVIDENCE

---

130. SELF-DEVELOPMENT LOOP

JARVIS IDENTIFIES IMPROVEMENT
        ↓
RESEARCH
        ↓
MECHANISM EXTRACTION
        ↓
DESIGN CANDIDATE
        ↓
SNAPSHOT JARVIS
        ↓
MUTATED JARVIS ENVIRONMENT
        ↓
IMPLEMENT
        ↓
BUILD
        ↓
RUN
        ↓
OBSERVE
        ↓
BENCHMARK
        ↓
COMPARE TO CURRENT
        ↓
PROMOTION DECISION
        ↓
PROMOTE OR REJECT

---

131. SELF-EVOLUTION BOUNDARY

Permanent rule:

«Jarvis must never directly rewrite the live organism while performing experimental development.»

Experimental development happens through:

LIVE
→ SNAPSHOT
→ MUTATED REALITY
→ CANDIDATE
→ EVALUATION
→ PROMOTION

The prior known-good system remains recoverable.

---

132. PROJECT-INDEPENDENT DESIGN

The Building System must work on:

- Jarvis
- another application
- another repository
- a service
- an algorithm
- a capability
- a deployment
- a model adapter

Do not hardcode Jarvis-specific paths or assumptions into the core.

---

133. CLOUD CODE

Cloud Code is allowed to build Jarvis now.

Cloud Code is not Jarvis.

Cloud Code is not a runtime dependency.

Cloud Code may eventually be represented as an optional:

ExternalDevelopmentWorker

with the same bounded worker contract:

task
→ context
→ environment
→ permissions
→ result
→ evidence

---

134. OMNIROUTE

OmniRoute may be used as an external routing/development accelerator.

It is not a fundamental Jarvis organ.

The Jarvis model layer remains behind:

- ModelManager
- ModelProvider
- local inference abstraction

OmniRoute can be an optional external adapter later.

---

135. GRAPHIFY

Graphify is repository intelligence support.

Use it for:

- architecture discovery
- symbols
- references
- dependency relationships
- relevant-file retrieval
- codebase understanding

But Graphify is not the source of truth.

The repository remains the source of actual implementation state.

---

136. NO PREMATURE DIAGNOSTIC FABRIC

Do not build the final Stage 16 Diagnostic Fabric during this Building System build.

The Building System may expose:

- operation evidence
- observation
- health
- failure records
- state transitions

required for its own operation.

Later Stage 16 will inspect the entire finished system.

That audit will then determine the final distributed diagnostic architecture.

---

137. STAGE 16 FORENSIC AUDIT

After the major offline organism stages are complete, perform a complete forensic exploration of:

- every directory
- every source file
- every class
- every function
- every interface
- every line that materially affects system behavior
- every configuration
- every dependency
- every model
- every asset
- every script
- every document
- every runtime path
- every deployment path
- every subsystem relationship
- dead code
- unused code
- duplicate code
- broken paths
- disconnected components
- performance hazards
- resource hazards
- security problems
- integration gaps

Only then design the final distributed Diagnostic Fabric.

---

138. IMPLEMENTATION RULE

Before changing code:

INSPECT.

Before creating:

SEARCH.

Before replacing:

UNDERSTAND CONSUMERS.

Before introducing dependency:

CHECK EXISTING REPOSITORY.

Before changing shared contract:

MAP IMPACT.

Reuse whenever possible.

---

139. EXISTING ARCHITECTURE OWNERSHIP

Preserve existing owners:

Cognition
→ Cognitive subsystem

Planning
→ GoalPlanner / PlanGraph

Execution
→ Execution subsystem

Capabilities
→ Capability Fabric

Memory
→ Memory system

Resources
→ ResourceGovernor

Failure
→ FailureSurface / Recovery

Models
→ ModelManager

Research
→ Universal Research

Development
→ Building System

Environment
→ Mutated Environment subsystem

If a new implementation creates conflicting ownership, stop and resolve ownership first.

---

140. DO NOT REBUILD WORKING SYSTEMS

Inspect the current repository and reuse completed mechanisms from:

- Cognitive State
- Working Memory
- Planning
- Execution
- Immune/Resilience
- Capability Fabric
- Memory Continuity
- Self/User/World Models
- ModelManager
- ResourceGovernor
- FailureSurface
- RecoveryController
- BodyCoordinator
- existing Builder foundation
- existing Universal Research foundation
- existing graph/repository intelligence

Do not rewrite working architecture merely because a new abstraction appears cleaner.

---

141. NO GIANT TEST PHASE

The repository has already undergone extensive verification.

Do not generate another 500+ test effort.

Do not spend the majority of the build writing redundant tests.

Use only focused validation needed to establish actual functionality.

The primary goal is the Building System.

---

142. MINIMUM BUILD VERIFICATION

At minimum demonstrate an actual path:

REQUEST
→ DEVELOPMENT SESSION
→ PROJECT INSPECTION
→ PLAN
→ ENVIRONMENT CREATION
→ WORKSPACE
→ CODE CHANGE
→ COMMAND
→ BUILD
→ OBSERVATION
→ STRUCTURED RESULT
→ CANDIDATE
→ EVALUATION
→ PROMOTION DECISION
→ EVIDENCE
→ MEMORY/LINEAGE

Then demonstrate:

LIVE JARVIS
→ SNAPSHOT
→ MUTATED JARVIS ENVIRONMENT
→ HARMLESS CHANGE
→ BUILD
→ RUN
→ OBSERVE
→ COMPARE
→ DESTROY

The live system must remain untouched unless explicit promotion is invoked.

---

143. NO PLACEHOLDER ARCHITECTURE

Do not satisfy this specification by creating empty classes with TODO comments.

Do not create fake:

- benchmarks
- deployments
- environment isolation
- rollback
- observations
- promotion
- model execution

If an implementation is blocked by an external environment constraint:

1. implement the correct contract
2. implement the safest usable local path
3. record the exact limitation
4. continue integrating everything that can be built

Never claim functionality that does not actually work.

---

144. RESOURCE-AWARE BUILDING

The Building System itself must be efficient.

Do not repeatedly:

- scan the whole repository
- load unnecessary models
- spawn unnecessary workers
- duplicate context
- rebuild unchanged environments
- repeat expensive research
- repeat known failed strategies

The Building System must embody the same efficiency principles it gives Jarvis.

---

145. ADAPTIVE COMPUTE

For every development request:

CAN DETERMINISTIC TOOLING SOLVE IT?
        ↓ no
CAN RETRIEVAL SOLVE IT?
        ↓ no
CAN SMALL MODEL SOLVE IT?
        ↓ no
CAN ONE WORKER SOLVE IT?
        ↓ no
DOES IT REQUIRE DEEP REASONING?
        ↓ yes
DOES IT REQUIRE RESEARCH?
        ↓ yes
DOES IT REQUIRE MULTIPLE WORKERS?

Only escalate computation when necessary.

---

146. MODEL MEMORY MANAGEMENT

Support the eventual ability to:

load small model
→ cheap task
→ unload
→ load deep model
→ difficult task
→ unload
→ restore lightweight model

Do not keep every model resident simultaneously.

---

147. DEVELOPMENT CONTINUITY

If the DevelopmentSession is interrupted, Jarvis must recover:

- objective
- environment
- workspace
- plan
- workers
- evidence
- pending work
- previous attempts
- safe next action

Do not restart from zero unnecessarily.

---

148. FINAL END-TO-END PIPELINE

The Building System must make this structurally possible:

USER GOAL
→ COGNITION
→ DEVELOPMENT REQUEST
→ CONTEXT RETRIEVAL
→ RESEARCH IF NEEDED
→ MECHANISM
→ PLAN
→ ENVIRONMENT
→ WORKSPACE
→ IMPLEMENTATION
→ BUILD
→ EXECUTE
→ OBSERVE
→ DIAGNOSE
→ REPAIR
→ BENCHMARK
→ EVALUATE
→ CANDIDATE
→ STAGE
→ PROMOTION
→ DEPLOY
→ OBSERVE
→ LEARN
→ MEMORY

---

149. SELF-BUILD PIPELINE

The system must make this possible:

JARVIS
→ identifies missing capability
→ researches mechanism
→ designs capability
→ creates candidate environment
→ implements capability
→ builds
→ executes
→ benchmarks
→ compares
→ stages
→ promotes
→ registers capability
→ remembers mechanism

---

150. SELF-REPLACEMENT PIPELINE

A single organ may be improved:

CURRENT ORGAN
→ CANDIDATE A
→ CANDIDATE B
→ CANDIDATE C
→ SAME WORKLOAD
→ BENCHMARK
→ COMPARE
→ SELECT
→ STAGE
→ PROMOTE

No direct live mutation.

---

151. WHOLE-SYSTEM EVOLUTION PIPELINE

Eventually:

JARVIS
→ identifies opportunity
→ researches
→ designs architecture candidate
→ forks system
→ mutates multiple organs
→ builds candidate
→ runs workloads
→ observes
→ benchmarks
→ compares baseline
→ stages
→ promotes

This is the long-term evolution mechanism.

---

152. FINAL DESIGN PRINCIPLE

The Mutated Environment is not where Jarvis merely tests code.

It is where Jarvis is allowed to create alternative realities.

Inside those realities Jarvis may:

- change architecture
- change algorithms
- change models
- change dependencies
- change tools
- change memory strategies
- change capabilities
- change orchestration
- break everything
- fix everything
- benchmark everything
- compare everything

Only the strongest justified candidate is promoted.

This is how Jarvis can eventually evolve without continuously risking the living organism.

---

153. COMPLETE DEVELOPMENT ORGANISM

The final development organism is:

                         ALIVE JARVIS
                              │
                    ┌─────────┴─────────┐
                    │                   │
                COGNITION            BUILDER
                    │                   │
                MEMORY             RESEARCH
                    │                   │
               SELF/WORLD         MECHANISMS
                    │                   │
                PLANNING           ALGORITHMS
                    │                   │
               CAPABILITIES        ENVIRONMENTS
                    │                   │
                EXECUTION             BUILD
                    │                   │
                OBSERVATION          REPAIR
                    │                   │
                LEARNING            BENCHMARK
                    │                   │
                    └─────────┬─────────┘
                              │
                         PROMOTION
                              │
                         DEPLOYMENT
                              │
                          ROLLBACK
                              │
                           MEMORY
                              │
                       FUTURE EVOLUTION

---

154. FINAL OBJECTIVE

The objective is not:

«"Jarvis can write code."»

The objective is:

«Jarvis can understand a desired outcome, create the reality required to pursue it, construct and modify systems inside that reality, observe what happens, learn from the results, compare alternatives, and safely promote successful discoveries into the living system.»

Eventually:

«Jarvis can use the same machinery to improve the mechanisms that make Jarvis.»

---

155. IMPLEMENTATION INSTRUCTION TO CLAUDE CODE

You are implementing an existing Jarvis organism.

Before writing code:

1. Read this entire document.
2. Inspect "./vault/".
3. Read all relevant architectural and research documents.
4. Inspect the existing repository.
5. Determine which mechanisms already exist.
6. Map ownership.
7. Identify genuine gaps.
8. Reuse and extend existing systems.
9. Implement the complete Building System described here.
10. Integrate it with the existing Jarvis organism.
11. Do not create duplicate systems.
12. Do not create a giant test phase.
13. Do not build Stage 16 diagnostics.
14. Do not make Cloud Code or OmniRoute runtime dependencies.
15. Do not directly mutate LIVE Jarvis during experimentation.
16. Preserve evidence and lineage.
17. Make the end-to-end development lifecycle actually executable.
18. Produce a truthful implementation report.

Do not ask the user to paste this document.

Do not simplify this document into a generic coding agent.

Do not reinterpret Jarvis as a human brain.

Do not reinterpret Jarvis as a swarm.

Do not reinterpret Jarvis as a chatbot.

Do not reinterpret Jarvis as Claude Code.

Build the system described here.

---

156. COMPLETION REPORT

When complete, create:

"MILESTONE_REPORT_jarvis_building_system.md"

Include:

- architecture
- existing systems reused
- new systems created
- environment architecture
- workspace architecture
- candidate architecture
- development session architecture
- worker architecture
- research integration
- repository intelligence
- code modification
- command execution
- build adapters
- runtime adapters
- experiment system
- observation
- evidence
- benchmarking
- promotion
- deployment
- rollback
- resource governance
- memory integration
- knowledge graph integration
- nervous-system integration
- recovery
- self-development path
- current supported path
- remaining genuine blockers

Also create:

"JARVIS_BUILDING_SYSTEM_MAP.md"

containing:

- component relationships
- data flows
- lifecycle flows
- ownership boundaries
- environment relationships
- candidate genealogy
- promotion flow
- self-development flow

Do not produce a giant testing report.

---

157. FINAL STOP CONDITION

When this Building System is implemented:

STOP.

Do not automatically:

- rewrite Jarvis
- modify live Jarvis
- start unrestricted self-evolution
- redesign cognition
- redesign memory
- build voice
- build vision
- build the final Stage 16 Diagnostic Fabric
- install random external frameworks
- add unrelated features

The Building System itself is the milestone.

The next decision will be based on the resulting architecture and milestone report.

---

158. FINAL NON-NEGOTIABLE PRINCIPLES

1. Jarvis is an organism, not an LLM wrapper.

2. The LLM is a replaceable intelligence substrate.

3. Cognition is distinct from model inference.

4. Memory is distinct from context.

5. Attention controls context relevance.

6. The Self/User/World models represent different domains.

7. The nervous system connects organs.

8. The Capability Fabric is the action bridge.

9. Execution is bounded and observable.

10. Immune/recovery protects the organism from subsystem failure.

11. Resource usage is governed.

12. Research extracts mechanisms, not products.

13. External systems are research sources or adapters, not automatically architecture.

14. Mutated Environments are alternative executable realities.

15. Experimental systems must not directly mutate LIVE Jarvis.

16. Candidates require lineage.

17. Promotion requires evidence.

18. Every meaningful development decision must be traceable.

19. Workers are bounded temporary execution units.

20. Context is a governed resource.

21. RAM is a governed resource.

22. Model selection should follow task requirements.

23. LFM is a research seed for efficient intelligence, not a vendor dependency.

24. Cloud Code is a temporary external development accelerator, not a runtime dependency.

25. OmniRoute is an optional external routing accelerator, not a fundamental Jarvis organ.

26. Graphify is repository intelligence support, not the repository source of truth.

27. The existing architecture must be reused before new architecture is created.

28. No second memory system.

29. No second capability registry.

30. No second execution engine.

31. No second graph system.

32. No second event bus.

33. No second resource governor.

34. No second final diagnostic system.

35. No unrestricted recursive self-modification.

36. Stage 16 is the later forensic system-wide audit.

37. The Building System is the mechanism by which Jarvis will eventually be able to build Jarvis.

---

159. FINAL COMMAND

READ THIS ENTIRE FILE FIRST.

THEN INSPECT THE REPOSITORY AND VAULT.

THEN IMPLEMENT THE COMPLETE BUILDING, EXPERIMENTATION, DEPLOYMENT, AND EVOLUTION SYSTEM DESCRIBED HERE.

DO NOT ASK FOR THE ROADMAP AGAIN.

DO NOT REDUCE THE SCOPE TO A CODING AGENT.

DO NOT STOP AT A SANDBOX.

DO NOT STOP AT A FILE EDITOR.

DO NOT STOP AT A TERMINAL WRAPPER.

DO NOT STOP AT A BUILD WRAPPER.

BUILD THE INTEGRATED ORGAN.

The intended final transition is:

Claude Code
    ↓
builds Jarvis
    ↓
Jarvis gains Building System
    ↓
Jarvis gains ability to create alternative realities
    ↓
Jarvis can build / modify / benchmark / deploy systems
    ↓
Jarvis can eventually target Jarvis itself
    ↓
Jarvis becomes capable of constructing and evolving its own organs

The final objective is:

«Jarvis can understand what should exist, discover how to build it, create the reality in which it can safely be built, construct it, observe it, compare it, and promote it into the living system when the evidence justifies doing so.»

BUILD THE SYSTEM THAT MAKES THIS POSSIBLE.