"""Building System Integration — bridges the existing JARVIS Core (Phase 1)
with the Building System.

This module connects:
- core/jarvis_core.py (Phase 1 CLI cycle) → Building System
- core/classifier.py → Building System capability detection
- core/decision_gate.py → Building System promotion gates
- core/execution_layer.py → Building System execution
- Cognitive Engine, Capability Fabric, Memory, Models → Building System
"""

from __future__ import annotations

import asyncio
from dataclasses import dataclass
from typing import Any, Optional

# Import existing core
from . import jarvis_core, classifier, decision_gate, execution_layer

# Import Building System
from building import (
    BuildingSystem,
    BuildingSystemConfig,
    BuildingSystemCapability,
    get_building_system,
    ProjectTarget,
    ProjectTargetType,
    CapabilitySpec,
    CapabilityRegistry,
    Genome,
    GenomeBuilder,
    GenomeHealth,
)


@dataclass
class IntegrationConfig:
    """Configuration for Building System integration."""
    enabled: bool = True
    auto_register_capabilities: bool = True
    building_system_config: Optional[BuildingSystemConfig] = None


class BuildingSystemIntegration:
    """Integrates the Building System with the existing JARVIS Core."""

    def __init__(self, config: Optional[IntegrationConfig] = None):
        self.config = config or IntegrationConfig()
        self.building_system: Optional[BuildingSystem] = None
        self.building_capability: Optional[BuildingSystemCapability] = None
        self._initialized = False

    def initialize(self) -> None:
        """Initialize the Building System and register capabilities."""
        if not self.config.enabled:
            return

        self.building_system = get_building_system(self.config.building_system_config)
        self.building_capability = BuildingSystemCapability(self.building_system)

        if self.config.auto_register_capabilities:
            self._register_building_capabilities()

        self._initialized = True

    def _register_building_capabilities(self) -> None:
        """Register Building System capabilities with the existing capability fabric."""
        # This would integrate with the existing cognitive/capability system
        # For now, we expose the building system capability spec
        spec = self.building_capability.get_capability_spec()
        print(f"[BuildingSystemIntegration] Registered capability: {spec['name']}")

    async def handle_capability_gap(self, capability_name: str, requirements: list[str]) -> dict[str, Any]:
        """Handle a capability gap detected by the cognitive system."""
        if not self._initialized:
            self.initialize()

        return await self.building_system.fill_capability_gap(capability_name, requirements)

    async def run_self_development(self, change_type: str = "logging", description: str = "") -> dict[str, Any]:
        """Run a self-development experiment."""
        if not self._initialized:
            self.initialize()

        return await self.building_system.run_self_development_experiment(change_type, description)

    async def evolve_capability(self, capability_name: str, generations: int = 5) -> list[dict[str, Any]]:
        """Evolve an existing capability."""
        if not self._initialized:
            self.initialize()

        return await self.building_system.evolve_capability(capability_name, generations)

    def get_status(self) -> dict[str, Any]:
        """Get integrated system status."""
        base_status = {
            "core_phase": "Phase 1",
            "core_loop": "active",
        }
        if self._initialized and self.building_system:
            base_status["building_system"] = self.building_system.get_system_status()
        else:
            base_status["building_system"] = {"initialized": False}
        return base_status

    def shutdown(self) -> None:
        """Shutdown the integration."""
        if self.building_system:
            self.building_system.shutdown()
        self._initialized = False


# ============================================================================
# Enhanced Classifier with Building System Awareness
# ============================================================================

BUILDING_INTENTS = {
    "build_capability": [
        "build", "create", "implement", "add capability", "new capability",
        "make a tool", "write a module", "generate code",
    ],
    "self_develop": [
        "improve yourself", "self develop", "self modify", "experiment on yourself",
        "run self experiment", "modify yourself",
    ],
    "evolve": [
        "evolve", "improve", "optimize", "make better", "enhance",
    ],
    "build_status": [
        "build status", "building status", "construction status",
    ],
}


def enhanced_classify(text: str) -> str:
    """Enhanced classifier that includes Building System intents."""
    # First check building intents
    lowered = text.lower().strip()
    for intent, keywords in BUILDING_INTENTS.items():
        if any(k in lowered for k in keywords):
            return intent

    # Fall back to original classifier
    return classifier.classify(text)


# ============================================================================
# Enhanced Decision Gate with Building System Awareness
# ============================================================================

BUILDING_PROTECTED = [
    "building/orchestrator.py",
    "building/self_development.py",
    "building/genome.py",
    "building/evolution.py",
    "core/building_integration.py",
]

def enhanced_is_tier2(action: dict) -> bool:
    """Enhanced tier-2 detection including Building System protected paths."""
    target = action.get("target_path") or ""
    return any(p in target for p in decision_gate.PROTECTED_PATTERNS + BUILDING_PROTECTED)


def enhanced_review(action: dict) -> dict:
    """Enhanced decision gate review."""
    if enhanced_is_tier2(action):
        return {
            "approved": False,
            "tier": 2,
            "reason": "Building System core component requires explicit approval",
        }
    return decision_gate.review(action)


# ============================================================================
# Enhanced Execution Layer with Building System Actions
# ============================================================================

BUILDING_ACTION_TYPES = {
    "build_capability",
    "run_self_development",
    "evolve_capability",
    "create_genome",
    "promote_genome",
    "rollback_genome",
}

def enhanced_apply(action: dict, decision: dict) -> dict:
    """Enhanced execution layer that handles Building System actions."""
    action_type = action.get("type")

    if action_type in BUILDING_ACTION_TYPES:
        if not decision.get("approved"):
            return {"applied": False, "reason": decision.get("reason", "not approved")}

        # These actions are handled asynchronously via the Building System
        # For synchronous execution layer, we queue them
        return {
            "applied": True,
            "output": f"Building System action '{action_type}' queued for execution",
            "queued": True,
        }

    # Fall back to original execution layer
    return execution_layer.apply(action, decision)


# ============================================================================
# Enhanced Core Cycle with Building System Integration
# ============================================================================

async def enhanced_run_cycle(user_input: str, integration: BuildingSystemIntegration) -> str:
    """Enhanced core cycle that includes Building System operations."""
    intent = enhanced_classify(user_input)

    # Handle Building System intents
    if intent == "build_capability":
        # Extract capability name from input
        # This is simplified - real implementation would use NLP
        capability_name = "new_capability"
        result = await integration.handle_capability_gap(capability_name, [])
        return f"Capability build initiated: {result}"

    elif intent == "self_develop":
        result = await integration.run_self_development("logging", "User-initiated self-development")
        return f"Self-development experiment: {result}"

    elif intent == "evolve":
        capability_name = "core"  # Default
        result = await integration.evolve_capability(capability_name, 3)
        return f"Evolution initiated: {result}"

    elif intent == "build_status":
        status = integration.get_status()
        return f"Building System: {status}"

    # Fall back to original cycle for other intents
    return jarvis_core.run_cycle(user_input)


# ============================================================================
# Singleton Integration Instance
# ============================================================================

_integration: Optional[BuildingSystemIntegration] = None


def get_integration(config: Optional[IntegrationConfig] = None) -> BuildingSystemIntegration:
    """Get or create the global Building System Integration."""
    global _integration
    if _integration is None:
        _integration = BuildingSystemIntegration(config)
    return _integration


def initialize_building_system(config: Optional[IntegrationConfig] = None) -> BuildingSystemIntegration:
    """Initialize the Building System Integration."""
    global _integration
    _integration = BuildingSystemIntegration(config)
    _integration.initialize()
    return _integration


# ============================================================================
# CLI Entry Point
# ============================================================================

if __name__ == "__main__":
    import sys

    integration = initialize_building_system()

    if len(sys.argv) > 1:
        text = " ".join(sys.argv[1:])
        # Run async cycle
        result = asyncio.run(enhanced_run_cycle(text, integration))
        print(result)
    else:
        print(integration.get_status())