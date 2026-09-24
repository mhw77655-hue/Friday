package com.jarvis.app.selfreconfig

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SystemGraphTest {

    private lateinit var graph: SystemGraph

    @Before
    fun setUp() {
        graph = SystemGraph()
    }

    @Test
    fun `registerNode adds node and node is retrievable`() {
        val node = SystemGraph.SystemNode(
            id = "cognitive.engine",
            name = "CognitiveEngine",
            organType = SystemGraph.OrganType.COGNITIVE,
            qualifiedClassName = "com.jarvis.app.cognitive.CognitiveEngine"
        )
        graph.registerNode(node)
        assertEquals(1, graph.nodeCount())
        assertEquals(node, graph.node("cognitive.engine"))
    }

    @Test
    fun `registerNode rejects duplicate id`() {
        val node = SystemGraph.SystemNode(id = "a", name = "A", organType = SystemGraph.OrganType.CORE)
        graph.registerNode(node)
        try {
            graph.registerNode(node.copy(name = "B"))
            assertTrue("Should have thrown", false)
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Duplicate"))
        }
    }

    @Test
    fun `addEdge connects two registered nodes`() {
        graph.registerNode(SystemGraph.SystemNode(id = "a", name = "A", organType = SystemGraph.OrganType.CORE))
        graph.registerNode(SystemGraph.SystemNode(id = "b", name = "B", organType = SystemGraph.OrganType.COGNITIVE))
        val edge = graph.addEdge("a", "b")
        assertEquals(1, graph.edgeCount())
        assertEquals("a", edge.fromId)
        assertEquals("b", edge.toId)
        assertEquals(listOf(edge), graph.edgesFrom("a"))
        assertEquals(listOf(edge), graph.edgesTo("b"))
    }

    @Test
    fun `addEdge rejects unregistered source or target`() {
        graph.registerNode(SystemGraph.SystemNode(id = "a", name = "A", organType = SystemGraph.OrganType.CORE))
        try {
            graph.addEdge("a", "nonexistent")
            assertTrue("Should have thrown", false)
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Target node not registered"))
        }
        try {
            graph.addEdge("nonexistent", "a")
            assertTrue("Should have thrown", false)
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Source node not registered"))
        }
    }

    @Test
    fun `markFixedInvariant sets invariant flag`() {
        val node = SystemGraph.SystemNode(id = "auth", name = "AuthRoot", organType = SystemGraph.OrganType.SAFETY)
        graph.registerNode(node)
        graph.markFixedInvariant("auth")
        assertTrue(graph.node("auth")!!.isFixedInvariant)
        assertEquals(1, graph.fixedInvariants().size)
    }

    @Test
    fun `markFixedInvariant rejects already-invariant node`() {
        graph.registerNode(SystemGraph.SystemNode(id = "a", name = "A", organType = SystemGraph.OrganType.SAFETY, isFixedInvariant = true))
        try {
            graph.markFixedInvariant("a")
            assertTrue("Should have thrown", false)
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("already a fixed invariant"))
        }
    }

    @Test
    fun `tryMarkMutable returns false for fixed invariant`() {
        graph.registerNode(SystemGraph.SystemNode(id = "a", name = "A", organType = SystemGraph.OrganType.SAFETY))
        graph.markFixedInvariant("a")
        assertFalse(graph.tryMarkMutable("a"))
    }

    @Test
    fun `tryMarkMutable returns true for non-invariant`() {
        graph.registerNode(SystemGraph.SystemNode(id = "a", name = "A", organType = SystemGraph.OrganType.COGNITIVE))
        assertTrue(graph.tryMarkMutable("a"))
    }

    @Test
    fun `computeReachability finds transitive reachable nodes`() {
        graph.registerNode(SystemGraph.SystemNode(id = "entry", name = "Entry", organType = SystemGraph.OrganType.ENTRY))
        graph.registerNode(SystemGraph.SystemNode(id = "a", name = "A", organType = SystemGraph.OrganType.COGNITIVE))
        graph.registerNode(SystemGraph.SystemNode(id = "b", name = "B", organType = SystemGraph.OrganType.MEMORY))
        graph.registerNode(SystemGraph.SystemNode(id = "isolated", name = "Isolated", organType = SystemGraph.OrganType.PLANNED))
        graph.addEdge("entry", "a")
        graph.addEdge("a", "b")
        val result = graph.computeReachability("entry")
        assertTrue(result.reachableIds.containsAll(setOf("entry", "a", "b")))
        assertTrue(result.unreachableIds.contains("isolated"))
        assertEquals(1, result.unreachableNodes.size)
        assertEquals("isolated", result.unreachableNodes[0].id)
    }

    @Test
    fun `computeReachability rejects unregistered entry point`() {
        try {
            graph.computeReachability("nonexistent")
            assertTrue("Should have thrown", false)
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Entry point not registered"))
        }
    }

    @Test
    fun `all real organs from jarvis single-authority map are represented`() {
        val realOrgans = JarvisOrganGraph.build()

        val expectedIds = listOf(
            "entry.latencyPipeline",
            "core.humanCore",
            "core.bodyCoordinator",
            "cognitive.engine",
            "cognitive.contextWindowAssembler",
            "memory.graphStore",
            "memory.blendedRetriever",
            "identity.selfModel",
            "identity.userProfile",
            "identity.worldModelService",
            "identity.mentalStateEstimator",
            "identity.personaTuner",
            "social.personRelationshipModel",
            "social.confidentialityFirewall",
            "model.modelManager",
            "model.resourceGovernor",
            "model.anchorEngine",
            "model.cognitiveAdmissionPolicy",
            "model.reasoningTier",
            "emotion.fusionLayerTier1",
            "language.egyptianArabicTextHalf",
            "capability.capabilityRegistry",
            "safety.approvalMechanism",
            "safety.circuitBreaker",
            "research.universalResearchEngine",
            "builder.toolBuilder",
            "identity.ownerBiometricBinder",
            "android.riskGate",
            "android.deviceControlRouter",
            "android.mediaSessionBackend",
            "android.screenBridgeBackend"
        )

        for (id in expectedIds) {
            assertNotNull("Organ '$id' must be in the graph", realOrgans.node(id))
        }
        assertTrue("At least 15 organs represented", realOrgans.nodeCount() >= 15)
        assertTrue("At least 10 edges", realOrgans.edgeCount() >= 10)
    }

    @Test
    fun `wiring diagnostics flags disconnected organ as unreachable`() {
        val testGraph = JarvisOrganGraph.build()

        testGraph.registerNode(SystemGraph.SystemNode(
            id = "test.disconnected.organ",
            name = "DisconnectedTestOrgan",
            organType = SystemGraph.OrganType.PLANNED,
            qualifiedClassName = "com.jarvis.app.test.FakeDisconnectedOrgan"
        ))

        val diagnostics = WiringDiagnostics(testGraph)
        val result = diagnostics.diagnose()

        assertTrue("Disconnected organ must be unreachable", result.unreachableNodes.any { it.id == "test.disconnected.organ" })
        assertTrue("At least 1 organ unreachable", result.unreachableCount >= 1)
    }

    @Test
    fun `wiring diagnostics reports all invariant nodes present when configured`() {
        val testGraph = JarvisOrganGraph.build()

        val diagnostics = WiringDiagnostics(testGraph)
        val result = diagnostics.diagnose()

        assertTrue("All required invariants must be present", result.allInvariantNodesPresent)
        assertTrue("Missing invariants list must be empty", result.missingInvariants.isEmpty())
    }

    @Test
    fun `edge kind is preserved`() {
        graph.registerNode(SystemGraph.SystemNode(id = "a", name = "A", organType = SystemGraph.OrganType.CORE))
        graph.registerNode(SystemGraph.SystemNode(id = "b", name = "B", organType = SystemGraph.OrganType.COGNITIVE))
        val edge = graph.addEdge("a", "b", SystemGraph.DependencyEdge.EdgeKind.SENDS_TO)
        assertEquals(SystemGraph.DependencyEdge.EdgeKind.SENDS_TO, edge.kind)
    }

    @Test
    fun `safety approval mechanism points at the real ApprovalGate class`() {
        val realOrgans = JarvisOrganGraph.build()
        val node = realOrgans.node("safety.approvalMechanism")!!
        assertEquals("com.jarvis.app.approval.ApprovalGate", node.qualifiedClassName)
        assertFalse(
            "must not point at the non-existent ApprovalMechanism class",
            node.qualifiedClassName!!.endsWith("ApprovalMechanism")
        )
    }

    @Test
    fun `body coordinator is reachable from the entry in the corrected composition`() {
        val realOrgans = JarvisOrganGraph.build()
        val result = realOrgans.computeReachability("entry.latencyPipeline")
        assertTrue(
            "bodyCoordinator gains an inbound edge reflecting its real construction",
            result.reachableIds.contains("core.bodyCoordinator")
        )
    }

    @Test
    fun `phase-a wired subsystems are genuinely reachable from the entry`() {
        val realOrgans = JarvisOrganGraph.build()
        val result = realOrgans.computeReachability("entry.latencyPipeline")
        // Story 1 wired Galaxy Memory + Stage-03 Identity through the live engine,
        // so these are genuinely reachable in the corrected composition.
        assertTrue(result.reachableIds.contains("memory.graphStore"))
        assertTrue(result.reachableIds.contains("memory.blendedRetriever"))
        assertTrue(result.reachableIds.contains("identity.selfModel"))
        assertTrue(result.reachableIds.contains("identity.identityContext"))
        assertTrue(result.reachableIds.contains("cognitive.contextWindowAssembler"))
    }

    @Test
    fun `anchor engine node is real and wired to its two real dependencies`() {
        val realOrgans = JarvisOrganGraph.build()

        // ANCHOR-ENGINE-FOUNDATION AC4: the PLANNED placeholder is replaced by
        // a real node pointing at the real class, not a stub/interface.
        val node = realOrgans.node("model.anchorEngine")
        assertNotNull("anchor engine node must exist", node)
        assertEquals("com.jarvis.app.anchor.AnchorEngine", node!!.qualifiedClassName)
        assertTrue(
            "anchor engine must be a REAL organ, not PLANNED",
            node.organType != SystemGraph.OrganType.PLANNED
        )
        assertTrue(
            "JarvisOrganGraph must no longer contain the PLANNED placeholder",
            realOrgans.node("planned.anchorEngine") == null
        )

        // The anchor is genuinely reachable from the entry (constructed and
        // anchored in JarvisEngine.init) and depends on BOTH the single model
        // loading authority and the shared cognitive runtime gateway.
        val reachability = realOrgans.computeReachability("entry.latencyPipeline")
        assertTrue(
            "anchor engine must be reachable from the entry in the production composition",
            reachability.reachableIds.contains("model.anchorEngine")
        )
        val deps = realOrgans.edgesFrom("model.anchorEngine")
        assertTrue(
            "anchor depends on the real model_manager authority",
            deps.any {
                it.toId == "model.modelManager" && it.kind == SystemGraph.DependencyEdge.EdgeKind.DEPENDS_ON
            }
        )
        assertTrue(
            "anchor depends on the shared cognitive runtime gateway",
            deps.any {
                it.toId == "model.cognitiveAdmissionPolicy" && it.kind == SystemGraph.DependencyEdge.EdgeKind.DEPENDS_ON
            }
        )

        // The gateway is a real node, and the engine + anchor share the one
        // instance — the anchor must NOT introduce a second decision authority.
        val gatewayNode = realOrgans.node("model.cognitiveAdmissionPolicy")
        assertNotNull(gatewayNode)
        assertEquals("com.jarvis.app.model.CognitiveAdmissionPolicy", gatewayNode!!.qualifiedClassName)
        assertTrue(
            "engine and anchor must both depend on the same gateway node",
            realOrgans.edgesFrom("cognitive.engine").any {
                it.toId == "model.cognitiveAdmissionPolicy" && it.kind == SystemGraph.DependencyEdge.EdgeKind.DEPENDS_ON
            }
        )
    }

    @Test
    fun `reasoning tier node is real and reachable through the model authority`() {
        val realOrgans = JarvisOrganGraph.build()

        // REASONING-TIER-MODEL-GROUND-TRUTH-AND-WIRE AC5: the ON_DEMAND_REASONING
        // tier is POPULATED and served by the real backend — not a PLANNED
        // placeholder node.
        val node = realOrgans.node("model.reasoningTier")
        assertNotNull("reasoning tier node must exist", node)
        assertEquals("com.jarvis.app.model.OllamaModelBackend", node!!.qualifiedClassName)
        assertTrue(
            "reasoning tier must be a REAL organ, not PLANNED",
            node.organType != SystemGraph.OrganType.PLANNED
        )

        // The tier is genuinely reachable from the live entry: engine -> the one
        // ModelManager loading authority -> the tier's real backend.
        val reachability = realOrgans.computeReachability("entry.latencyPipeline")
        assertTrue(
            "reasoning tier must be reachable from the entry in the production composition",
            reachability.reachableIds.contains("model.reasoningTier")
        )
        val deps = realOrgans.edgesFrom("model.modelManager")
        assertTrue(
            "the single model authority depends on the tier's real backend",
            deps.any {
                it.toId == "model.reasoningTier" && it.kind == SystemGraph.DependencyEdge.EdgeKind.DEPENDS_ON
            }
        )
    }

    @Test
    fun `voice forge backend is a real organ owning the spoken output path`() {
        val realOrgans = JarvisOrganGraph.build()

        // VOICE-FORGE-EGYPTIAN-KAREN-TTS AC5: the spoken-reply VoiceForge
        // backend is a REAL organ (MODEL), constructed at the SAME composition
        // point as the model backends — not a PLANNED placeholder node.
        val node = realOrgans.node("voice.voiceForge")
        assertNotNull("voice.voiceForge node must exist", node)
        assertEquals("com.jarvis.app.voice.VoiceForgeBackend", node!!.qualifiedClassName)
        assertTrue(
            "voice forge must be a REAL organ, not PLANNED",
            node.organType != SystemGraph.OrganType.PLANNED
        )

        // The entry's spoken-output organ (entry.latencyPipeline owns fullSpeak)
        // DEPENDS_ON the backend, so it is reachable from the entry.
        val reachability = realOrgans.computeReachability("entry.latencyPipeline")
        assertTrue(
            "voice forge must be reachable from the entry in the production composition",
            reachability.reachableIds.contains("voice.voiceForge")
        )
        val entryDeps = realOrgans.edgesFrom("entry.latencyPipeline")
        assertTrue(
            "the spoken-output entry depends on the voice forge backend",
            entryDeps.any {
                it.toId == "voice.voiceForge" && it.kind == SystemGraph.DependencyEdge.EdgeKind.DEPENDS_ON
            }
        )

        // Construction-site ground truth (AC2) is captured on the node.
        assertTrue(
            "voice forge node carries the ground-truth construction-site description",
            node.description.contains("JarvisEngine.kt:175-189") && node.description.contains("TermuxJarvisServer.kt:232")
        )
    }

    @Test
    fun `context window assembler graph reflects the ground-truthed wiring state`() {
        val realOrgans = JarvisOrganGraph.build()

        // Ground truth (CONTEXT-WINDOW-ASSEMBLER-GROUND-TRUTH): the assembler's
        // READS_FROM edges must point at BOTH of its real construction seams as
        // wired by JarvisEngine.init — the live retriever and the live estimator.
        val readEdges = realOrgans.edgesFrom("cognitive.contextWindowAssembler")
        assertTrue(
            "assembler reads cross-session memories from the real retriever seam",
            readEdges.any { it.toId == "memory.blendedRetriever" && it.kind == SystemGraph.DependencyEdge.EdgeKind.READS_FROM }
        )
        assertTrue(
            "assembler reads the per-turn mental-state hypothesis from the real estimator seam",
            readEdges.any { it.toId == "identity.mentalStateEstimator" && it.kind == SystemGraph.DependencyEdge.EdgeKind.READS_FROM }
        )

        // The engine node depends on the assembler it constructs.
        assertTrue(
            "engine depends on the assembler it constructs",
            realOrgans.edgesFrom("cognitive.engine").any {
                it.toId == "cognitive.contextWindowAssembler" && it.kind == SystemGraph.DependencyEdge.EdgeKind.DEPENDS_ON
            }
        )

        // The verified construction-site ground truth is captured on the node.
        val assemblerNode = realOrgans.node("cognitive.contextWindowAssembler")
        assertNotNull(assemblerNode)
        assertTrue(
            "assembler node carries the ground-truth description",
            assemblerNode!!.description.contains("CognitiveEngine.kt:135-140")
        )
    }

    @Test
    fun `egyptian arabic text half is a real organ feeding the generation seam`() {
        val realOrgans = JarvisOrganGraph.build()

        // EGYPTIAN-ARABIC-TEXT-HALF AC4: the dialect detector is a REAL node
        // pointing at the real class — not a PLANNED placeholder.
        val node = realOrgans.node("language.egyptianArabicTextHalf")
        assertNotNull("language.egyptianArabicTextHalf node must exist", node)
        assertEquals("com.jarvis.app.language.EgyptianArabicDialectDetector", node!!.qualifiedClassName)
        assertTrue(
            "dialect detector must be a REAL organ, not PLANNED",
            node.organType != SystemGraph.OrganType.PLANNED
        )

        // The engine consumes the detector per-turn (DEPENDS_ON), so the
        // detector is genuinely reachable from the production entry.
        assertTrue(
            "engine must depend on the dialect detector",
            realOrgans.edgesFrom("cognitive.engine").any {
                it.toId == "language.egyptianArabicTextHalf" &&
                    it.kind == SystemGraph.DependencyEdge.EdgeKind.DEPENDS_ON
            }
        )
        val reachability = realOrgans.computeReachability("entry.latencyPipeline")
        assertTrue(
            "dialect detector must be reachable from the entry in the production composition",
            reachability.reachableIds.contains("language.egyptianArabicTextHalf")
        )
        assertTrue(
            "dialect detector node carries the construction-site ground truth",
            node.description.contains("JarvisEngine.kt:454")
        )
    }

    @Test
    fun `build verification twin is a real organ gating artifact promotion`() {
        val realOrgans = JarvisOrganGraph.build()

        // BUILD-TWIN-ARM64-VERIFICATION AC5: the verification twin is a REAL
        // INFRA node pointing at the real BuildTwinVerifier class — not a
        // PLANNED placeholder.
        val node = realOrgans.node("infra.buildVerificationTwin")
        assertNotNull("infra.buildVerificationTwin node must exist", node)
        assertEquals("com.jarvis.app.buildtwin.BuildTwinVerifier", node!!.qualifiedClassName)
        assertTrue(
            "verification twin must be a REAL INFRA organ, not PLANNED",
            node.organType != SystemGraph.OrganType.PLANNED
        )
        assertEquals(SystemGraph.OrganType.INFRA, node.organType)

        // The organs that own device-artifact promotion are GATED by it: the
        // VoiceForge spoken-output organ and the reasoning-tier model organ.
        val voiceDeps = realOrgans.edgesFrom("voice.voiceForge")
        assertTrue(
            "voice forge (artifact promotion owner) must be GATED by the verification twin",
            voiceDeps.any {
                it.toId == "infra.buildVerificationTwin" &&
                    it.kind == SystemGraph.DependencyEdge.EdgeKind.GATES
            }
        )
        val tierDeps = realOrgans.edgesFrom("model.reasoningTier")
        assertTrue(
            "reasoning tier (model artifact promotion owner) must be GATED by the verification twin",
            tierDeps.any {
                it.toId == "infra.buildVerificationTwin" &&
                    it.kind == SystemGraph.DependencyEdge.EdgeKind.GATES
            }
        )

        // Gated from the entry via the entry -> voiceForge -> twin path, so it
        // is genuinely reachable in the production composition.
        val reachability = realOrgans.computeReachability("entry.latencyPipeline")
        assertTrue(
            "verification twin must be reachable from the entry in the production composition",
            reachability.reachableIds.contains("infra.buildVerificationTwin")
        )
        assertTrue(
            "verification twin node carries the ground-truth provisioning/architecture description",
            node.description.contains(".ralph/buildtwin/provision.sh") &&
                node.description.contains("TermuxJarvisServer")
        )
    }

    @Test
    fun `emotion fusion layer tier 1 is a real organ feeding the mental state estimator`() {
        val realOrgans = JarvisOrganGraph.build()

        // EMOTIONAL-INTELLIGENCE-FUSION-LAYER-TIER-1 AC4: the emotion tier is a
        // REAL node pointing at the real class — not a PLANNED placeholder.
        val node = realOrgans.node("emotion.fusionLayerTier1")
        assertNotNull("emotion.fusionLayerTier1 node must exist", node)
        assertEquals("com.jarvis.app.emotion.FusionLayerTier1", node!!.qualifiedClassName)
        assertTrue(
            "emotion tier must be a REAL organ, not PLANNED",
            node.organType != SystemGraph.OrganType.PLANNED
        )

        // The tier feeds its EmotionHypothesis INTO the real mental-state
        // estimator seam (edge into identity.mentalStateEstimator)...
        assertTrue(
            "emotion tier must feed the mental state estimator seam",
            realOrgans.edgesFrom("emotion.fusionLayerTier1").any {
                it.toId == "identity.mentalStateEstimator" &&
                    it.kind == SystemGraph.DependencyEdge.EdgeKind.SENDS_TO
            }
        )
        // ...and the estimator's hypothesis derives from the tier, so the tier
        // is genuinely reachable from the production entry (not bolted on).
        assertTrue(
            "estimator must derive its hypothesis from the emotion tier",
            realOrgans.edgesFrom("identity.mentalStateEstimator").any {
                it.toId == "emotion.fusionLayerTier1" &&
                    it.kind == SystemGraph.DependencyEdge.EdgeKind.DEPENDS_ON
            }
        )
        val reachability = realOrgans.computeReachability("entry.latencyPipeline")
        assertTrue(
            "emotion tier must be reachable from the entry in the production composition",
            reachability.reachableIds.contains("emotion.fusionLayerTier1")
        )
        assertTrue(
            "emotion tier node carries the construction-site ground truth",
            node.description.contains("JarvisEngine.kt:288-293")
        )
    }

    @Test
    fun `per-person relationship model and firewall are real organs feeding the generation seam`() {
        val realOrgans = JarvisOrganGraph.build()

        // PERSON-RELATIONSHIP-MODEL-AND-CONFIDENTIALITY-FIREWALL AC5: both the
        // relationship model and the confidentiality firewall are REAL nodes
        // pointing at the real classes — not PLANNED placeholders.
        val model = realOrgans.node("social.personRelationshipModel")
        assertNotNull("social.personRelationshipModel node must exist", model)
        assertEquals("com.jarvis.app.social.PersonRelationshipModel", model!!.qualifiedClassName)
        assertTrue(
            "relationship model must be a REAL organ, not PLANNED",
            model.organType != SystemGraph.OrganType.PLANNED
        )

        val firewall = realOrgans.node("social.confidentialityFirewall")
        assertNotNull("social.confidentialityFirewall node must exist", firewall)
        assertEquals("com.jarvis.app.social.ConfidentialityFirewall", firewall!!.qualifiedClassName)
        assertTrue(
            "confidentiality firewall must be a REAL organ, not PLANNED",
            firewall.organType != SystemGraph.OrganType.PLANNED
        )

        // Both are wired into the identity context — the seam the real
        // DIRECT_REPLY generation consults per turn — and both read from the
        // galaxy memory graph via the world-model seam (no second store).
        val ctxDeps = realOrgans.edgesFrom("identity.identityContext")
        assertTrue(
            "identity context depends on the relationship model",
            ctxDeps.any {
                it.toId == "social.personRelationshipModel" &&
                    it.kind == SystemGraph.DependencyEdge.EdgeKind.DEPENDS_ON
            }
        )
        assertTrue(
            "identity context depends on the confidentiality firewall",
            ctxDeps.any {
                it.toId == "social.confidentialityFirewall" &&
                    it.kind == SystemGraph.DependencyEdge.EdgeKind.DEPENDS_ON
            }
        )
        for (account in listOf("social.personRelationshipModel", "social.confidentialityFirewall")) {
            assertTrue(
                "$account must read from the galaxy graph store (no second store)",
                realOrgans.edgesFrom(account).any {
                    it.toId == "memory.graphStore" && it.kind == SystemGraph.DependencyEdge.EdgeKind.READS_FROM
                }
            )
        }

        // Reaching the generation seam means being reachable from the entry
        // through the live engine and the identity context.
        val reachability = realOrgans.computeReachability("entry.latencyPipeline")
        assertTrue(
            "relationship model must be reachable from the entry in the production composition",
            reachability.reachableIds.contains("social.personRelationshipModel")
        )
        assertTrue(
            "confidentiality firewall must be reachable from the entry in the production composition",
            reachability.reachableIds.contains("social.confidentialityFirewall")
        )
        assertTrue(
            "relationship model node carries the construction-site ground truth",
            model.description.contains("JarvisEngine.kt:312-313")
        )
        assertTrue(
            "firewall node carries the construction-site ground truth",
            firewall.description.contains("JarvisEngine.kt:312-313")
        )
    }

    @Test
    fun `continuity gate is a real organ every generation contributor feeds into`() {
        val realOrgans = JarvisOrganGraph.build()

        // CONTINUITY-GATE-ENFORCED-SEAM AC6: the ContinuityGate is a REAL
        // COGNITIVE node pointing at the real class — not a PLANNED placeholder.
        val node = realOrgans.node("continuity.continuityGate")
        assertNotNull("continuity.continuityGate node must exist", node)
        assertEquals("com.jarvis.app.continuity.ContinuityGate", node!!.qualifiedClassName)
        assertEquals(SystemGraph.OrganType.COGNITIVE, node.organType)
        assertTrue(
            "continuity gate must be a REAL organ, not PLANNED",
            node.organType != SystemGraph.OrganType.PLANNED
        )

        // The gate is genuinely reachable from the live entry (engine holds it).
        val reachability = realOrgans.computeReachability("entry.latencyPipeline")
        assertTrue(
            "continuity gate must be reachable from the entry in the production composition",
            reachability.reachableIds.contains("continuity.continuityGate")
        )

        // Every contributor organ feeds its signal INTO the gate (SENDS_TO).
        val contributorIds = listOf(
            "language.egyptianArabicTextHalf",
            "social.personRelationshipModel",
            "social.confidentialityFirewall",
            "model.reasoningTier"
        )
        for (contributor in contributorIds) {
            assertTrue(
                "$contributor must feed its contribution into continuity.continuityGate",
                realOrgans.edgesFrom(contributor).any {
                    it.toId == "continuity.continuityGate" &&
                        it.kind == SystemGraph.DependencyEdge.EdgeKind.SENDS_TO
                }
            )
        }

        // The gate has an edge INTO the real generation seam: the identity
        // generation-assembly method (gatherForTurn) and the assembler that
        // formats the cross-session memory block both consume the snapshot.
        assertTrue(
            "continuity gate must send its snapshot into the identity generation-assembly seam",
            realOrgans.edgesFrom("continuity.continuityGate").any {
                it.toId == "identity.identityContext" &&
                    it.kind == SystemGraph.DependencyEdge.EdgeKind.SENDS_TO
            }
        )
        assertTrue(
            "continuity gate must send its snapshot into the context window assembler",
            realOrgans.edgesFrom("continuity.continuityGate").any {
                it.toId == "cognitive.contextWindowAssembler" &&
                    it.kind == SystemGraph.DependencyEdge.EdgeKind.SENDS_TO
            }
        )

        // AC6 pin: NO contributor organ may have an edge directly into the
        // generation seam that bypasses continuity.continuityGate. The seams a
        // bypass would attach to are the identity generation-assembly method
        // (identity.identityContext) and the payload formatter
        // (cognitive.contextWindowAssembler).
        for (contributor in contributorIds) {
            val bypass = realOrgans.edgesFrom(contributor).filter {
                (it.toId == "identity.identityContext" || it.toId == "cognitive.contextWindowAssembler") &&
                    it.kind == SystemGraph.DependencyEdge.EdgeKind.SENDS_TO
            }
            assertTrue(
                "$contributor must NOT bypass continuity.continuityGate with a direct " +
                    "payload edge (found ${bypass.map { it.toId }})",
                bypass.isEmpty()
            )
        }
    }

    @Test
    fun `turn trace store is a real local organ fed by the live engine`() {
        val realOrgans = JarvisOrganGraph.build()

        // TURN-TRACE (Gate 3a): JsonlTurnTraceStore is a REAL TRACE organ — not
        // a PLANNED placeholder — pointing at the real production class.
        val node = realOrgans.node("trace.turnTraceStore")
        assertNotNull("trace.turnTraceStore node must exist", node)
        assertEquals("com.jarvis.app.trace.JsonlTurnTraceStore", node!!.qualifiedClassName)
        assertEquals(SystemGraph.OrganType.TRACE, node.organType)
        assertTrue(
            "turn trace store must be a REAL organ, not PLANNED",
            node.organType != SystemGraph.OrganType.PLANNED
        )

        // The live engine SENDS_TO the store on every real turn (constructor-injected
        // turnTraceStore seam in JarvisEngine.init).
        assertTrue(
            "cognitive.engine must send its turn trace into trace.turnTraceStore",
            realOrgans.edgesFrom("cognitive.engine").any {
                it.toId == "trace.turnTraceStore" &&
                    it.kind == SystemGraph.DependencyEdge.EdgeKind.SENDS_TO
            }
        )

        // The store is genuinely reachable from the live entry via the engine.
        val reachability = realOrgans.computeReachability("entry.latencyPipeline")
        assertTrue(
            "trace.turnTraceStore must be reachable from the entry in the production composition",
            reachability.reachableIds.contains("trace.turnTraceStore")
        )
    }

    @Test
    fun `thread tracker is a real open-thread organ fed by the live engine`() {
        val realOrgans = JarvisOrganGraph.build()

        // THREAD-OBJECTS (Gate 3a, priority 2): ThreadTracker is a REAL THREAD organ
        // — not a PLANNED placeholder — pointing at the real production class.
        val node = realOrgans.node("threads.threadTracker")
        assertNotNull("threads.threadTracker node must exist", node)
        assertEquals("com.jarvis.app.threads.ThreadTracker", node!!.qualifiedClassName)
        assertEquals(SystemGraph.OrganType.THREAD, node.organType)
        assertTrue(
            "thread tracker must be a REAL organ, not PLANNED",
            node.organType != SystemGraph.OrganType.PLANNED
        )

        // The live engine SENDS_TO the registry on every real turn (constructor-
        // injected threadTracker seam in JarvisEngine.init).
        assertTrue(
            "cognitive.engine must feed its open threads into threads.threadTracker",
            realOrgans.edgesFrom("cognitive.engine").any {
                it.toId == "threads.threadTracker" &&
                    it.kind == SystemGraph.DependencyEdge.EdgeKind.SENDS_TO
            }
        )

        // The registry is genuinely reachable from the live entry via the engine.
        val reachability = realOrgans.computeReachability("entry.latencyPipeline")
        assertTrue(
            "threads.threadTracker must be reachable from the entry in the production composition",
            reachability.reachableIds.contains("threads.threadTracker")
        )
    }
}
