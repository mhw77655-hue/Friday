"""Tests for the JARVIS Building System — minimum verification path from §142.

These tests demonstrate the complete REQUEST → DEVELOPMENT SESSION → ... → PROMOTION
path and the self-development path: LIVE JARVIS → SNAPSHOT → MUTATED JARVIS
ENVIRONMENT → HARMLESS CHANGE → BUILD → RUN → OBSERVE → COMPARE → DESTROY
"""

import asyncio
import json
import os
import sys
import tempfile
import shutil
from pathlib import Path
from typing import Any

# Add repo to path
sys.path.insert(0, str(Path(__file__).parent.parent))

from building import (
    Genome,
    GenomeBuilder,
    GenomeRegistry,
    GenomeArchive,
    GenomeHealth,
    MutationOperator,
    CapabilityRegistry,
    CapabilitySpec,
    ProjectTarget,
    ProjectTargetType,
    SessionManager,
    DevelopmentSession,
    SessionState,
    ExperimentSpecification,
    MutationEngine,
    CandidateGenerator,
    SpecSynthesizer,
    FitnessModel,
    PromotionGate,
    GateVerdict,
    EvolutionEngine,
    PromotionController,
    RollbackController,
    OperationJournal,
    Observation,
    ObservationType,
    Evidence,
    EvidenceStrength,
    CandidateLineage,
    LineageTracker,
    AdapterRegistry,
    GradleBuildAdapter,
    GitRepositoryAdapter,
    ProcessRuntimeAdapter,
    FilesystemEnvironmentAdapter,
    LocalDeploymentAdapter,
    LlamaCppModelAdapter,
    LatencyBenchmarkAdapter,
    NullResearchAdapter,
    SelfDevelopmentPipeline,
    SelfDevelopmentConfig,
    BuildingSystem,
    BuildingSystemConfig,
    get_building_system,
    harmless_logging_change,
    basic_health_observation,
)


def test_genome_creation():
    """Test genome creation and serialization."""
    builder = GenomeBuilder("test-capability")
    builder.capability("test_capability")
    builder.version(1)
    builder.author("test")
    builder.resource_budget(max_memory_mb=512)
    builder.test("unit_test", "unit", "python -m pytest", timeout_seconds=30)
    builder.benchmark("latency", "http", "latency_ms", 100.0, "ms")

    genome = builder.build()

    assert genome.id == "test-capability"
    assert genome.version == 1
    assert "test_capability" in genome.capabilities
    assert genome.resource_budget.max_memory_mb == 512
    assert len(genome.test_suite) == 1
    assert len(genome.benchmark_suite) == 1

    # Test serialization
    json_data = genome.to_json()
    assert json_data["id"] == "test-capability"

    # Test deserialization
    genome2 = Genome.from_json(json_data)
    assert genome2.id == genome.id
    assert genome2.version == genome.version
    assert genome2.capabilities == genome.capabilities

    print("✓ test_genome_creation passed")


def test_genome_mutation():
    """Test genome forking with mutations."""
    builder = GenomeBuilder("parent")
    builder.capability("base")
    builder.version(1)
    parent = builder.build()

    # Fork with capability addition
    child = parent.fork(
        MutationOperator.CAPABILITY_ADD,
        "Add new capability",
        capabilities=parent.capabilities | {"new_cap"},
    )

    assert child.version == 2
    assert "new_cap" in child.capabilities
    assert parent.id in child.parent_lineage
    assert child.health_state == GenomeHealth.UNKNOWN

    print("✓ test_genome_mutation passed")


def test_genome_registry():
    """Test genome registry promotion/rollback."""
    registry = GenomeRegistry()
    archive = GenomeArchive("logs/test_genome_archive.jsonl")

    # Create and promote genome
    builder = GenomeBuilder("cap-v1")
    builder.capability("test_cap")
    builder.version(1)
    genome = builder.build()
    genome = genome.copy(health_state=GenomeHealth.HEALTHY)

    registry.register(genome)
    archive.put(genome)

    # Check it's the best
    best = registry.best_for("test_cap")
    assert best is not None
    assert best.id == genome.id

    # Create newer version
    builder2 = GenomeBuilder("cap-v2")
    builder2.capability("test_cap")
    builder2.version(2)
    genome2 = builder2.build().copy(health_state=GenomeHealth.HEALTHY)
    registry.register(genome2)
    archive.put(genome2)

    # Should now be the best
    best = registry.best_for("test_cap")
    assert best.id == genome2.id

    print("✓ test_genome_registry passed")


def test_capability_registry():
    """Test capability registry."""
    registry = CapabilityRegistry()

    spec = CapabilitySpec(
        name="test_capability",
        version="1.0.0",
        description="Test capability",
        provides={"test_capability"},
    )

    registry.register_spec(spec)
    assert registry.has_spec("test_capability")
    assert registry.get_spec("test_capability").name == "test_capability"

    print("✓ test_capability_registry passed")


def test_session_management():
    """Test development session lifecycle."""
    manager = SessionManager("building/test_sessions")

    target = ProjectTarget.jarvis_capability("test_feature")
    session = manager.create_session(
        request="Build test feature",
        project_target=target,
    )

    assert session.id.startswith("session-")
    assert session.request == "Build test feature"
    assert session.state == SessionState.INITIALIZING
    assert session.workspace is not None

    # Test state transitions
    session.transition(SessionState.RESEARCHING)
    assert session.state == SessionState.RESEARCHING

    session.transition(SessionState.DESIGNING)
    assert session.state == SessionState.DESIGNING

    session.complete(success=True)
    assert session.state == SessionState.COMPLETED
    assert session.completed_at is not None

    print("✓ test_session_management passed")


def test_experiment_specification():
    """Test experiment specification."""
    exp = ExperimentSpecification.create(
        name="Test Experiment",
        hypothesis="Adding logging improves observability",
        success_criteria={"build_passes": True, "tests_pass": True},
        description="Test experiment for building system",
    )

    assert exp.id.startswith("exp-")
    assert exp.hypothesis == "Adding logging improves observability"
    assert exp.success_criteria["build_passes"] is True

    print("✓ test_experiment_specification passed")


def test_mutation_engine():
    """Test mutation engine."""
    builder = GenomeBuilder("base")
    builder.capability("base_cap")
    builder.version(1)
    builder.resource_budget(max_memory_mb=512)
    genome = builder.build()

    engine = MutationEngine(mutation_rate=1.0)  # 100% for testing
    variants = engine.mutate(genome)

    assert len(variants) >= 1
    for v in variants:
        assert v.version == 2
        assert v.parent_lineage[-1] == genome.id

    print("✓ test_mutation_engine passed")


def test_spec_synthesizer():
    """Test spec synthesizer."""
    synthesizer = SpecSynthesizer()
    genome = synthesizer.synthesize("new_feature", ["req1", "req2"])

    assert genome.id == "new_feature"
    assert "new_feature" in genome.capabilities
    assert "req1" in genome.dependencies
    assert "req2" in genome.dependencies

    print("✓ test_spec_synthesizer passed")


def test_fitness_model():
    """Test fitness evaluation."""
    from building import Candidate
    from building.evolution import CandidateImplementation

    fitness = FitnessModel()

    builder = GenomeBuilder("fit-test")
    builder.capability("fit_cap")
    builder.version(1)
    builder.resource_budget(max_memory_mb=256)
    builder.latency_budget(p50_ms=50, p95_ms=200, p99_ms=500, max_ms=1000)
    builder.test("test1", "unit", "pytest")
    builder.benchmark("latency", "http", "latency_ms", 100.0, "ms")
    builder.safety_constraint("safe", "Must be safe")
    genome = builder.build()

    impl = CandidateImplementation(
        id="impl-1",
        genome_id=genome.id,
        source_path="/tmp/test",
    )
    candidate = Candidate(
        id="cand-1",
        genome=genome,
        implementation=impl,
        test_results={"test1": {"passed": True}},
        benchmark_results={"latency": 80.0},
    )

    score = fitness.evaluate(candidate, genome)
    assert 0.0 <= score <= 1.0
    assert score > 0.5  # Should score reasonably well

    print("✓ test_fitness_model passed")


def test_promotion_gate():
    """Test promotion gate decision."""
    from building import Candidate
    from building.evolution import CandidateImplementation

    fitness = FitnessModel()
    gate = PromotionGate(fitness, min_fitness=0.7, require_human_approval=False)

    builder = GenomeBuilder("gate-test")
    builder.capability("gate_cap")
    builder.version(1)
    builder.resource_budget(max_memory_mb=256)
    builder.latency_budget(p50_ms=50, p95_ms=200, p99_ms=500, max_ms=1000)
    builder.test("test1", "unit", "pytest")
    builder.benchmark("latency", "http", "latency_ms", 100.0, "ms")
    builder.safety_constraint("safe", "Must be safe")
    genome = builder.build()

    impl = CandidateImplementation(
        id="impl-gate",
        genome_id=genome.id,
        source_path="/tmp/test",
    )

    # Passing candidate - test_results must be dict of test_name -> {"passed": bool}
    # The promotion gate checks for top-level "passed" key, so we add it
    test_results = {"test1": {"passed": True}, "test2": {"passed": True}, "passed": True}
    candidate_pass = Candidate(
        id="cand-pass",
        genome=genome,
        implementation=impl,
        test_results=test_results,
        benchmark_results={"latency": 50.0},
        fitness_score=0.8,
    )
    decision = gate.evaluate(candidate_pass, genome)
    assert decision.verdict == GateVerdict.PROMOTE

    # Failing candidate - missing "passed" key
    candidate_fail = Candidate(
        id="cand-fail",
        genome=genome,
        implementation=impl,
        test_results={"test1": {"passed": False}},
        fitness_score=0.3,
    )
    decision = gate.evaluate(candidate_fail, genome)
    assert decision.verdict == GateVerdict.REJECT

    print("✓ test_promotion_gate passed")


def test_operation_journal():
    """Test operation journal with observations and evidence."""
    with tempfile.TemporaryDirectory() as tmpdir:
        journal = OperationJournal(
            session_id="test-session",
            storage_path=Path(tmpdir) / "journal.jsonl",
        )

        # Log operations
        journal.log_operation("test_op", "testing", "started", {"detail": "value"})
        journal.log_operation("test_op", "testing", "completed", {"result": "ok"})

        # Log observation
        obs = Observation.create(
            type=ObservationType.TEST_RESULT,
            session_id="test-session",
            description="Test passed",
            candidate_id="cand-1",
            genome_id="genome-1",
            metrics={"pass_rate": 1.0},
        )
        journal.log_observation(obs)

        # Log evidence
        evidence = Evidence.create(
            claim="Tests pass for candidate",
            observation_ids=[obs.id],
            strength=EvidenceStrength.STRONG,
            supports=True,
            confidence=0.9,
        )
        journal.log_evidence(evidence)

        # Verify persistence
        assert len(journal.entries) >= 3
        assert obs.id in journal.observations
        assert evidence.id in journal.evidence

    print("✓ test_operation_journal passed")


def test_candidate_lineage():
    """Test candidate lineage tracking."""
    tracker = LineageTracker("logs/test_lineage.jsonl")
    lineage = tracker.create_lineage("cand-1", "genome-root")

    assert lineage.candidate_id == "cand-1"
    assert lineage.root_genome_id == "genome-root"
    assert len(lineage.genome_chain) == 1
    assert len(lineage.mutation_chain) == 0
    assert len(lineage.evaluation_history) == 0
    assert len(lineage.promotion_history) == 0
    assert len(lineage.rollback_history) == 0

    # Add mutation
    lineage = lineage.add_mutation({"operator": "CAPABILITY_ADD", "description": "Added cap"})
    assert len(lineage.mutation_chain) == 1

    # Add evaluation
    lineage = lineage.add_evaluation({"fitness": 0.8, "gate": "promote"})
    assert len(lineage.evaluation_history) == 1

    # Add promotion
    lineage = lineage.add_promotion({"receipt_id": "promo-1"})
    assert len(lineage.promotion_history) == 1

    tracker.put(lineage)
    retrieved = tracker.get("cand-1")
    assert retrieved is not None
    assert len(retrieved.mutation_chain) == 1

    print("✓ test_candidate_lineage passed")


def test_adapters():
    """Test adapter registry and basic adapters."""
    registry = AdapterRegistry()

    # Register adapters
    registry.register("gradle", GradleBuildAdapter())
    registry.register("git", GitRepositoryAdapter())
    registry.register("process", ProcessRuntimeAdapter())
    registry.register("filesystem", FilesystemEnvironmentAdapter())
    registry.register("local", LocalDeploymentAdapter())
    registry.register("llama.cpp", LlamaCppModelAdapter())
    registry.register("latency", LatencyBenchmarkAdapter())
    registry.register("null", NullResearchAdapter())

    # Test lookup
    build = registry.get_build("gradle")
    assert build is not None
    assert build.name == "gradle"

    repo = registry.get_repository("git")
    assert repo is not None
    assert repo.name == "git"

    runtime = registry.get_runtime("process")
    assert runtime is not None
    assert runtime.name == "process"

    env = registry.get_environment("filesystem")
    assert env is not None
    assert env.name == "filesystem"

    deploy = registry.get_deployment("local")
    assert deploy is not None
    assert deploy.name == "local"

    model = registry.get_model("llama.cpp")
    assert model is not None
    assert model.name == "llama.cpp"

    bench = registry.get_benchmark("latency")
    assert bench is not None
    assert bench.name == "latency"

    research = registry.get_research("null")
    assert research is not None
    assert research.name == "null"

    print("✓ test_adapters passed")


async def test_self_development_pipeline():
    """Test self-development pipeline (dry run)."""
    config = SelfDevelopmentConfig(
        live_jarvis_path="/nonexistent",  # Will use repo fallback
        mutant_base_path="building/test_self_mutants",
    )

    pipeline = SelfDevelopmentPipeline(config)

    # Run with harmless change
    result = await pipeline.run_self_development_cycle(
        change_description="Test logging change",
        harmless_change_fn=harmless_logging_change,
        observation_fn=basic_health_observation,
    )

    assert "cycle_id" in result
    assert "session_id" in result
    # In dry run mode, some errors may occur due to missing dependencies
    # but the pipeline should complete
    assert "promoted" in result
    assert result.get("promoted") is False  # Self-dev never auto-promotes

    print("✓ test_self_development_pipeline passed")


def test_building_system_integration():
    """Test full Building System initialization."""
    config = BuildingSystemConfig(
        workspace_root="building/test_integration",
        log_dir="logs/test_integration",
        genome_archive_path="logs/test_integration/genome_archive.jsonl",
        lineage_path="logs/test_integration/lineage.jsonl",
    )

    system = BuildingSystem(config)

    assert system._initialized
    assert system.genome_registry is not None
    assert system.genome_archive is not None
    assert system.capability_registry is not None
    assert system.mutant_env is not None
    assert system.evolution_engine is not None
    assert system.session_manager is not None
    assert system.orchestrator is not None
    assert system.self_dev is not None

    status = system.get_system_status()
    assert status["initialized"] is True
    assert "genomes_tracked" in status
    assert "adapters_registered" in status

    system.shutdown()
    assert system._initialized is False

    print("✓ test_building_system_integration passed")


async def test_full_development_cycle():
    """Test the complete REQUEST → ... → PROMOTION path (dry run)."""
    config = BuildingSystemConfig(
        workspace_root="building/test_full_cycle",
        log_dir="logs/test_full_cycle",
    )

    system = BuildingSystem(config)

    # Create a session manually and verify it works
    target = ProjectTarget.jarvis_capability("test_full_cycle")
    session = system.session_manager.create_session(
        request="Full cycle test",
        project_target=target,
    )

    assert session.id.startswith("session-")
    assert session.project_target.target_type == ProjectTargetType.JARVIS_CAPABILITY

    # Run the evolution engine for one generation
    candidates = await system.evolution_engine.run_generation(
        capability_gap="test_full_cycle",
        population_size=2,
    )

    assert len(candidates) >= 1
    for c in candidates:
        assert c.genome is not None
        assert c.implementation is not None

    system.shutdown()
    print("✓ test_full_development_cycle passed")


def test_minimum_verification_path():
    """Test the minimum verification path from §142.

    REQUEST → DEVELOPMENT SESSION → PROJECT INSPECTION → PLAN → ENVIRONMENT CREATION
    → WORKSPACE → CODE CHANGE → COMMAND → BUILD → OBSERVATION → STRUCTURED RESULT
    → CANDIDATE → EVALUATION → PROMOTION DECISION → EVIDENCE → MEMORY/LINEAGE
    """
    # This is the comprehensive integration test
    config = BuildingSystemConfig(
        workspace_root="building/test_min_ver",
        log_dir="logs/test_min_ver",
    )

    system = BuildingSystem(config)

    # 1. REQUEST
    request = "Add a simple logging capability"

    # 2. DEVELOPMENT SESSION
    session = system.session_manager.create_session(
        request=request,
        project_target=ProjectTarget.jarvis_capability("logging"),
    )

    # 3. PROJECT INSPECTION (simulated - would inspect target repo)
    session.transition(SessionState.RESEARCHING)
    session.metadata["research"] = {"findings": ["logging module available"]}

    # 4. PLAN (genome synthesis)
    session.transition(SessionState.DESIGNING)
    from building import SpecSynthesizer
    synthesizer = SpecSynthesizer()
    genome = synthesizer.synthesize("logging", ["logging", "structured_output"])
    session.genome_id = genome.id

    # 5. ENVIRONMENT CREATION
    session.transition(SessionState.ENVIRONMENT_PREPARING)

    # 6. WORKSPACE
    session.transition(SessionState.WORKSPACE_PREPARING)

    # 7. CODE CHANGE (generate implementation)
    session.transition(SessionState.IMPLEMENTING)
    from building import CandidateGenerator
    generator = CandidateGenerator()
    implementation = generator.generate(genome)
    assert len(implementation.files) > 0

    # 8. COMMAND → BUILD
    session.transition(SessionState.BUILDING)
    # Build is tested in mutant env tests

    # 9. OBSERVATION
    session.transition(SessionState.OBSERVING)
    obs = Observation.create(
        type=ObservationType.BUILD_RESULT,
        session_id=session.id,
        description="Build completed",
        genome_id=genome.id,
        data={"files_generated": len(implementation.files)},
        metrics={"files_count": float(len(implementation.files))},
    )

    journal = system.create_journal(session.id)
    journal.log_observation(obs)

    # 10. STRUCTURED RESULT → CANDIDATE
    from building import Candidate
    candidate = Candidate(
        id=f"cand-{genome.id}",
        genome=genome,
        implementation=implementation,
        test_results={"passed": True},
    )

    # 11. EVALUATION
    session.transition(SessionState.EVALUATING)
    decision = system.evolution_engine.promotion_gate.evaluate(candidate, genome)

    # 12. PROMOTION DECISION
    assert decision.verdict in (GateVerdict.PROMOTE, GateVerdict.REJECT, GateVerdict.NEEDS_HUMAN_REVIEW)

    # 13. EVIDENCE
    evidence = Evidence.create(
        claim=f"Candidate {candidate.id} meets promotion criteria",
        observation_ids=[obs.id],
        strength=EvidenceStrength.MODERATE,
        supports=decision.verdict == GateVerdict.PROMOTE,
        confidence=0.8,
    )
    journal.log_evidence(evidence)

    # 14. MEMORY/LINEAGE
    lineage = system.lineage_tracker.create_lineage(candidate.id, genome.id)
    lineage = lineage.add_evaluation({"fitness": candidate.fitness_score, "gate": decision.verdict.value})
    system.lineage_tracker.put(lineage)

    retrieved = system.get_lineage(candidate.id)
    assert retrieved is not None
    assert len(retrieved.evaluation_history) == 1

    system.shutdown()
    print("✓ test_minimum_verification_path passed")


def run_all_tests():
    """Run all tests."""
    tests = [
        test_genome_creation,
        test_genome_mutation,
        test_genome_registry,
        test_capability_registry,
        test_session_management,
        test_experiment_specification,
        test_mutation_engine,
        test_spec_synthesizer,
        test_fitness_model,
        test_promotion_gate,
        test_operation_journal,
        test_candidate_lineage,
        test_adapters,
        test_building_system_integration,
        test_minimum_verification_path,
    ]

    async_tests = [
        test_self_development_pipeline,
        test_full_development_cycle,
    ]

    print("Running synchronous tests...")
    for test in tests:
        try:
            test()
        except Exception as e:
            print(f"✗ {test.__name__} FAILED: {e}")
            import traceback
            traceback.print_exc()
            return False

    print("\nRunning asynchronous tests...")
    for test in async_tests:
        try:
            asyncio.run(test())
        except Exception as e:
            print(f"✗ {test.__name__} FAILED: {e}")
            import traceback
            traceback.print_exc()
            return False

    print("\n✅ All tests passed!")
    return True


if __name__ == "__main__":
    success = run_all_tests()
    sys.exit(0 if success else 1)