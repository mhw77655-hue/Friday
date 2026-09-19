package com.jarvis.app.cognitive.model

import com.jarvis.app.failure.FailureCategory
import com.jarvis.app.failure.FailureReport
import com.jarvis.app.failure.FailureSeverity
import com.jarvis.app.failure.Recoverability
import com.jarvis.app.humancore.store.StoragePort
import com.jarvis.app.humancore.store.StoreKind
import org.json.JSONArray
import org.json.JSONObject

/**
 * ModelPersistence - Bounded snapshot persistence for SelfModel / UserModel /
 * WorldModel, reusing the existing [StoragePort] (file-backed, atomic writes).
 *
 * Each model persists to its own [StoreKind] document, so losing or corrupting
 * one model never cascades into another (matching the Human Core store
 * philosophy and the 01G [com.jarvis.app.cognitive.memory.ContinuityPersistence]
 * pattern).
 *
 * Corrupt model state produces a structured [FailureReport] and degrades to
 * "start from empty" for that model only — never a crash and never a poisoned
 * store leaking into unrelated state.
 *
 * Privacy: sensitive facts are persisted with their [Sensitivity] classification
 * intact so diagnostics can redact them; secrets themselves must never be
 * written here (callers classify CRITICAL before persisting).
 */
class ModelPersistence(
    private val storage: StoragePort,
    private val onFailure: (FailureReport) -> Unit = {}
) {

    companion object {
        const val SCHEMA_VERSION = 1
        const val DOC_CLOCK_FIELD = "updatedAt"
    }

    // ---- save ----------------------------------------------------------------------

    fun save(self: SelfModel, user: UserModel, world: WorldModel): PersistResult {
        val failures = mutableListOf<FailureReport>()
        val count = saveDoc(StoreKind.SELF_MODEL, selfToJson(self), failures) +
            saveDoc(StoreKind.USER_MODEL, userToJson(user), failures) +
            saveDoc(StoreKind.WORLD_MODEL, worldToJson(world), failures)
        failures.forEach(onFailure)
        return if (failures.isEmpty()) PersistResult.SUCCESS(count) else PersistResult.FAILURE(failures)
    }

    private fun saveDoc(kind: StoreKind, doc: JSONObject, failures: MutableList<FailureReport>): Int {
        return try {
            storage.write(kind, doc.toString())
            1
        } catch (e: Exception) {
            failures.add(FailureReport(
                subsystem = "model",
                operation = "persist:${kind.name}",
                severity = FailureSeverity.WARNING,
                category = FailureCategory.PERSISTENCE,
                message = "failed to persist ${kind.name}: ${e.message}",
                recoverability = Recoverability.RETRYABLE
            ))
            0
        }
    }

    // ---- load ----------------------------------------------------------------------

    /**
     * Load all three models. Corrupt models degrade to null (start empty for
     * that model); a structured failure is reported for each corrupt store.
     */
    fun load(): LoadResult {
        val failures = mutableListOf<FailureReport>()

        val self = loadDoc(StoreKind.SELF_MODEL, failures, ::selfFromJson)
        val user = loadDoc(StoreKind.USER_MODEL, failures, ::userFromJson)
        val world = loadDoc(StoreKind.WORLD_MODEL, failures, ::worldFromJson)

        failures.forEach(onFailure)
        return LoadResult(self, user, world, failures)
    }

    private fun <T> loadDoc(kind: StoreKind, failures: MutableList<FailureReport>, parse: (JSONObject) -> T): T? {
        val raw = try {
            storage.read(kind)
        } catch (e: Exception) {
            failures.add(report(kind, "read failed: ${e.message}"))
            return null
        }
        if (raw == null) return null
        return try {
            parse(JSONObject(raw))
        } catch (e: Exception) {
            failures.add(report(kind, "corrupt snapshot: ${e.message}"))
            null
        }
    }

    private fun report(kind: StoreKind, message: String): FailureReport = FailureReport(
        subsystem = "model",
        operation = "load:${kind.name}",
        severity = FailureSeverity.WARNING,
        category = FailureCategory.PERSISTENCE,
        message = message,
        recoverability = Recoverability.PERMANENT
    )

    // ---- self serialization ----------------------------------------------------------

    private fun selfToJson(self: SelfModel): JSONObject {
        val doc = JSONObject()
        doc.put(DOC_CLOCK_FIELD, System.currentTimeMillis())
        doc.put("schemaVersion", SCHEMA_VERSION)

        val id = JSONObject()
        id.put("identityId", self.identity.identityId)
        id.put("canonicalName", self.identity.canonicalName)
        id.put("aliases", JSONArray(self.identity.aliases))
        id.put("version", self.identity.version)
        id.put("systemGeneration", self.identity.systemGeneration)
        id.put("identityState", self.identity.identityState)
        doc.put("identity", id)

        val nature = JSONObject()
        nature.put("what", self.nature.what)
        nature.put("architectureIdentity", self.nature.architectureIdentity)
        nature.put("operatingConstraints", JSONArray(self.nature.operatingConstraints))
        nature.put("supportedEnvironments", JSONArray(self.nature.supportedEnvironments))
        doc.put("nature", nature)

        val caps = JSONArray()
        for (c in self.capabilities) {
            val cj = JSONObject()
            cj.put("capabilityId", c.capabilityId)
            cj.put("purpose", c.purpose)
            cj.put("availability", c.availability)
            cj.put("confidence", c.confidence.toDouble())
            cj.put("knownLimitations", JSONArray(c.knownLimitations))
            cj.put("observedAt", c.observedAt)
            caps.put(cj)
        }
        doc.put("capabilities", caps)

        val lims = JSONArray()
        for (l in self.limitations) {
            val lj = JSONObject()
            lj.put("limitationType", l.limitationType.name)
            lj.put("description", l.description)
            lj.put("source", l.source)
            lj.put("observedAt", l.observedAt)
            lims.put(lj)
        }
        doc.put("limitations", lims)

        val res = JSONArray()
        for (r in self.resources) {
            val rj = JSONObject()
            rj.put("kind", r.kind.name)
            rj.put("name", r.name)
            rj.put("state", r.state)
            rj.put("confidence", r.confidence.toDouble())
            rj.put("observedAt", r.observedAt)
            res.put(rj)
        }
        doc.put("resources", res)

        val ks = JSONObject()
        ks.put("knownCount", self.knowledgeState.knownCount)
        ks.put("unknownCount", self.knowledgeState.unknownCount)
        ks.put("uncertainCount", self.knowledgeState.uncertainCount)
        ks.put("staleCount", self.knowledgeState.staleCount)
        doc.put("knowledgeState", ks)

        val as_ = JSONObject()
        as_.put("activeGoalIds", JSONArray(self.activeState.activeGoalIds))
        as_.putOpt("currentTask", self.activeState.currentTask)
        as_.putOpt("currentEnvironment", self.activeState.currentEnvironment)
        as_.putOpt("currentMode", self.activeState.currentMode)
        as_.putOpt("cognitiveStateId", self.activeState.cognitiveStateId)
        doc.put("activeState", as_)

        doc.put("facts", factsToJson(self.facts))
        doc.put("factHistory", factListToJson(self.factHistory))
        return doc
    }

    private fun selfFromJson(doc: JSONObject): SelfModel {
        val id = doc.optJSONObject("identity") ?: JSONObject()
        val nature = doc.optJSONObject("nature") ?: JSONObject()
        val caps = doc.optJSONArray("capabilities") ?: JSONArray()
        val lims = doc.optJSONArray("limitations") ?: JSONArray()
        val res = doc.optJSONArray("resources") ?: JSONArray()
        val ks = doc.optJSONObject("knowledgeState") ?: JSONObject()
        val as_ = doc.optJSONObject("activeState") ?: JSONObject()

        val capabilities = mutableListOf<SelfCapability>()
        for (i in 0 until caps.length()) {
            val c = caps.getJSONObject(i)
            capabilities.add(SelfCapability(
                capabilityId = c.optString("capabilityId", ""),
                purpose = c.optString("purpose", ""),
                availability = c.optString("availability", "UNKNOWN"),
                confidence = c.optDouble("confidence", 0.5).toFloat(),
                knownLimitations = optStringList(c, "knownLimitations"),
                observedAt = c.optLong("observedAt", System.currentTimeMillis())
            ))
        }

        val limitations = mutableListOf<SelfLimitation>()
        for (i in 0 until lims.length()) {
            val l = lims.getJSONObject(i)
            limitations.add(SelfLimitation(
                limitationType = enumOrDefault(l.optString("limitationType"), SelfLimitationType.RESOURCE_CONSTRAINT),
                description = l.optString("description", ""),
                source = l.optString("source", ""),
                observedAt = l.optLong("observedAt", System.currentTimeMillis())
            ))
        }

        val resources = mutableListOf<SelfResource>()
        for (i in 0 until res.length()) {
            val r = res.getJSONObject(i)
            resources.add(SelfResource(
                kind = enumOrDefault(r.optString("kind"), SelfResourceKind.RUNTIME_ATTACHMENT),
                name = r.optString("name", ""),
                state = r.optString("state", "unknown"),
                confidence = r.optDouble("confidence", 0.5).toFloat(),
                observedAt = r.optLong("observedAt", System.currentTimeMillis())
            ))
        }

        return SelfModel(
            identity = SelfIdentity(
                identityId = id.optString("identityId", "jarvis"),
                canonicalName = id.optString("canonicalName", "JARVIS"),
                aliases = optStringList(id, "aliases"),
                version = id.optString("version", "0.1.0"),
                systemGeneration = id.optString("systemGeneration", "01H"),
                identityState = id.optString("identityState", "BOOTED")
            ),
            nature = SelfNature(
                what = nature.optString("what", "an offline cognitive assistant subsystem"),
                architectureIdentity = nature.optString("architectureIdentity", "com.jarvis.app.cognitive.model"),
                operatingConstraints = optStringList(nature, "operatingConstraints"),
                supportedEnvironments = optStringList(nature, "supportedEnvironments")
            ),
            capabilities = capabilities,
            limitations = limitations,
            resources = resources,
            knowledgeState = SelfKnowledgeState(
                knownCount = ks.optInt("knownCount", 0),
                unknownCount = ks.optInt("unknownCount", 0),
                uncertainCount = ks.optInt("uncertainCount", 0),
                staleCount = ks.optInt("staleCount", 0)
            ),
            activeState = SelfActiveState(
                activeGoalIds = optStringList(as_, "activeGoalIds"),
                currentTask = as_.optString("currentTask", "").ifEmpty { null },
                currentEnvironment = as_.optString("currentEnvironment", "").ifEmpty { null },
                currentMode = as_.optString("currentMode", "").ifEmpty { null },
                cognitiveStateId = as_.optString("cognitiveStateId", "").ifEmpty { null }
            ),
            facts = factsFromJson(doc.optJSONObject("facts")),
            factHistory = factListFromJson(doc.optJSONArray("factHistory"))
        )
    }

    // ---- user serialization ------------------------------------------------------------

    private fun userToJson(user: UserModel): JSONObject {
        val doc = JSONObject()
        doc.put(DOC_CLOCK_FIELD, System.currentTimeMillis())
        doc.put("schemaVersion", SCHEMA_VERSION)

        val id = JSONObject()
        id.putOpt("userId", user.identity.userId)
        id.putOpt("displayIdentity", user.identity.displayIdentity)
        id.put("knownNames", JSONArray(user.identity.knownNames))
        id.put("aliases", JSONArray(user.identity.aliases))
        id.putOpt("preferredAddress", user.identity.preferredAddress)
        doc.put("identity", id)

        val prefs = JSONArray()
        for (p in user.preferences) {
            val pj = JSONObject()
            pj.put("key", p.key)
            pj.put("value", p.value)
            pj.put("source", p.source.name)
            pj.put("confidence", p.confidence.toDouble())
            pj.put("lastObservedAt", p.lastObservedAt)
            pj.put("confirmationState", p.confirmationState.name)
            pj.put("sensitivity", p.sensitivity.name)
            prefs.put(pj)
        }
        doc.put("preferences", prefs)

        val goals = JSONArray()
        for (g in user.goals) {
            val gj = JSONObject()
            gj.put("goalId", g.goalId)
            gj.put("description", g.description)
            gj.put("isRecurring", g.isRecurring)
            gj.put("isActive", g.isActive)
            goals.put(gj)
        }
        doc.put("goals", goals)

        val ws = JSONObject()
        ws.put("conciseVsDetailed", user.workingStyle.conciseVsDetailed.toDouble())
        ws.putOpt("preferredWorkflow", user.workingStyle.preferredWorkflow)
        ws.put("frequentProjectContext", JSONArray(user.workingStyle.frequentProjectContext))
        ws.put("commonShorthand", JSONArray(user.workingStyle.commonShorthand))
        ws.putOpt("preferredExecutionBehavior", user.workingStyle.preferredExecutionBehavior)
        doc.put("workingStyle", ws)

        val pc = JSONObject()
        pc.put("projects", JSONArray(user.projectContext.projects))
        pc.put("recurringDomains", JSONArray(user.projectContext.recurringDomains))
        pc.put("activeInterests", JSONArray(user.projectContext.activeInterests))
        pc.put("knownArtifactEntityIds", JSONArray(user.projectContext.knownArtifactEntityIds))
        doc.put("projectContext", pc)

        val ce = JSONObject()
        ce.putOpt("expectedResponseStyle", user.communicationExpectations.expectedResponseStyle)
        ce.putOpt("interruptionPreference", user.communicationExpectations.interruptionPreference)
        ce.putOpt("explanationPreference", user.communicationExpectations.explanationPreference)
        ce.putOpt("confirmationExpectations", user.communicationExpectations.confirmationExpectations)
        ce.putOpt("preferredInteractionMode", user.communicationExpectations.preferredInteractionMode)
        doc.put("communicationExpectations", ce)

        val rc = JSONObject()
        rc.putOpt("relationshipStoreId", user.relationshipContext.relationshipStoreId)
        rc.put("mirroredFacts", JSONObject(user.relationshipContext.mirroredFacts))
        rc.put("lastSyncedAt", user.relationshipContext.lastSyncedAt)
        doc.put("relationshipContext", rc)

        doc.put("facts", factsToJson(user.facts))
        doc.put("factHistory", factListToJson(user.factHistory))
        return doc
    }

    private fun userFromJson(doc: JSONObject): UserModel {
        val id = doc.optJSONObject("identity") ?: JSONObject()
        val prefs = doc.optJSONArray("preferences") ?: JSONArray()
        val goals = doc.optJSONArray("goals") ?: JSONArray()
        val ws = doc.optJSONObject("workingStyle") ?: JSONObject()
        val pc = doc.optJSONObject("projectContext") ?: JSONObject()
        val ce = doc.optJSONObject("communicationExpectations") ?: JSONObject()
        val rc = doc.optJSONObject("relationshipContext") ?: JSONObject()

        val preferences = mutableListOf<UserPreference>()
        for (i in 0 until prefs.length()) {
            val p = prefs.getJSONObject(i)
            preferences.add(UserPreference(
                key = p.optString("key", ""),
                value = p.optString("value", ""),
                source = enumOrDefault(p.optString("source"), ModelEvidenceSource.EXPLICIT_USER_STATEMENT),
                confidence = p.optDouble("confidence", 0.5).toFloat(),
                lastObservedAt = p.optLong("lastObservedAt", System.currentTimeMillis()),
                confirmationState = enumOrDefault(p.optString("confirmationState"), ConfirmationState.UNCONFIRMED),
                sensitivity = enumOrDefault(p.optString("sensitivity"), Sensitivity.NONE)
            ))
        }

        val userGoals = mutableListOf<UserGoalReference>()
        for (i in 0 until goals.length()) {
            val g = goals.getJSONObject(i)
            userGoals.add(UserGoalReference(
                goalId = g.optString("goalId", ""),
                description = g.optString("description", ""),
                isRecurring = g.optBoolean("isRecurring", false),
                isActive = g.optBoolean("isActive", false)
            ))
        }

        return UserModel(
            identity = UserIdentity(
                userId = id.optString("userId", "").ifEmpty { null },
                displayIdentity = id.optString("displayIdentity", "").ifEmpty { null },
                knownNames = optStringList(id, "knownNames"),
                aliases = optStringList(id, "aliases"),
                preferredAddress = id.optString("preferredAddress", "").ifEmpty { null }
            ),
            preferences = preferences,
            goals = userGoals,
            workingStyle = UserWorkingStyle(
                conciseVsDetailed = ws.optDouble("conciseVsDetailed", 0.0).toFloat(),
                preferredWorkflow = ws.optString("preferredWorkflow", "").ifEmpty { null },
                frequentProjectContext = optStringList(ws, "frequentProjectContext"),
                commonShorthand = optStringList(ws, "commonShorthand"),
                preferredExecutionBehavior = ws.optString("preferredExecutionBehavior", "").ifEmpty { null }
            ),
            projectContext = UserProjectContext(
                projects = optStringList(pc, "projects"),
                recurringDomains = optStringList(pc, "recurringDomains"),
                activeInterests = optStringList(pc, "activeInterests"),
                knownArtifactEntityIds = optStringList(pc, "knownArtifactEntityIds")
            ),
            communicationExpectations = UserCommunicationExpectations(
                expectedResponseStyle = ce.optString("expectedResponseStyle", "").ifEmpty { null },
                interruptionPreference = ce.optString("interruptionPreference", "").ifEmpty { null },
                explanationPreference = ce.optString("explanationPreference", "").ifEmpty { null },
                confirmationExpectations = ce.optString("confirmationExpectations", "").ifEmpty { null },
                preferredInteractionMode = ce.optString("preferredInteractionMode", "").ifEmpty { null }
            ),
            relationshipContext = UserRelationshipContext(
                relationshipStoreId = rc.optString("relationshipStoreId", "").ifEmpty { null },
                mirroredFacts = optStringMap(rc, "mirroredFacts"),
                lastSyncedAt = rc.optLong("lastSyncedAt", 0)
            ),
            facts = factsFromJson(doc.optJSONObject("facts")),
            factHistory = factListFromJson(doc.optJSONArray("factHistory"))
        )
    }

    // ---- world serialization ------------------------------------------------------------

    private fun worldToJson(world: WorldModel): JSONObject {
        val doc = JSONObject()
        doc.put(DOC_CLOCK_FIELD, System.currentTimeMillis())
        doc.put("schemaVersion", SCHEMA_VERSION)

        val entities = JSONObject()
        for ((id, e) in world.entities) entities.put(id, entityToJson(e))
        doc.put("entities", entities)

        val rels = JSONObject()
        for ((id, r) in world.relationships) rels.put(id, relationshipToJson(r))
        doc.put("relationships", rels)

        doc.put("facts", factsToJson(world.facts))
        doc.put("factHistory", factListToJson(world.factHistory))
        doc.put("historicalEntityIds", JSONArray(world.historicalEntityIds))
        return doc
    }

    private fun worldFromJson(doc: JSONObject): WorldModel {
        val entitiesJson = doc.optJSONObject("entities") ?: JSONObject()
        val entities = LinkedHashMap<String, WorldEntity>()
        for (key in entitiesJson.keys()) {
            entities[key] = entityFromJson(entitiesJson.getJSONObject(key))
        }
        val relsJson = doc.optJSONObject("relationships") ?: JSONObject()
        val rels = LinkedHashMap<String, WorldRelationship>()
        for (key in relsJson.keys()) {
            rels[key] = relationshipFromJson(relsJson.getJSONObject(key))
        }
        return WorldModel(
            entities = entities,
            relationships = rels,
            facts = factsFromJson(doc.optJSONObject("facts")),
            factHistory = factListFromJson(doc.optJSONArray("factHistory")),
            historicalEntityIds = optStringList(doc, "historicalEntityIds"),
            updatedAt = doc.optLong(DOC_CLOCK_FIELD, System.currentTimeMillis())
        )
    }

    private fun entityToJson(e: WorldEntity): JSONObject {
        val j = JSONObject()
        j.put("entityId", e.entityId)
        j.put("entityType", e.entityType.id)
        j.put("canonicalName", e.canonicalName)
        j.put("aliases", JSONArray(e.aliases))
        j.put("description", e.description)
        val attrs = JSONObject()
        for ((k, v) in e.attributes) {
            val aj = JSONObject()
            aj.put("type", when (v) {
                is AttributeValue.Text -> "text"
                is AttributeValue.Number -> "number"
                is AttributeValue.Bool -> "bool"
                is AttributeValue.Strings -> "strings"
            })
            aj.put("value", v.asText())
            attrs.put(k, aj)
        }
        j.put("attributes", attrs)
        j.put("state", e.state.name)
        j.put("source", e.source.name)
        j.put("sourceId", e.sourceId)
        j.put("confidence", confidenceToJson(e.confidence))
        j.put("observedAt", e.observedAt)
        j.put("updatedAt", e.updatedAt)
        if (e.validFrom != null) j.put("validFrom", e.validFrom)
        if (e.validUntil != null) j.put("validUntil", e.validUntil)
        j.put("expectedChangeRate", e.expectedChangeRate.name)
        j.put("sensitivity", e.sensitivity.name)
        return j
    }

    private fun entityFromJson(j: JSONObject): WorldEntity {
        val attrs = LinkedHashMap<String, AttributeValue>()
        val attrsJson = j.optJSONObject("attributes") ?: JSONObject()
        for (key in attrsJson.keys()) {
            val aj = attrsJson.getJSONObject(key)
            val v = when (aj.optString("type", "text")) {
                "number" -> AttributeValue.Number(aj.optDouble("value", 0.0))
                "bool" -> AttributeValue.Bool(aj.optBoolean("value", false))
                "strings" -> AttributeValue.Strings(aj.optString("value", "").split(",").filter { it.isNotEmpty() })
                else -> AttributeValue.Text(aj.optString("value", ""))
            }
            attrs[key] = v
        }
        return WorldEntity(
            entityId = j.optString("entityId", ""),
            entityType = WorldEntityType.fromId(j.optString("entityType", "unknown")),
            canonicalName = j.optString("canonicalName", ""),
            aliases = optStringList(j, "aliases"),
            description = j.optString("description", ""),
            attributes = attrs,
            state = enumOrDefault(j.optString("state"), WorldEntityState.CURRENT),
            source = enumOrDefault(j.optString("source"), ModelEvidenceSource.ENVIRONMENT_OBSERVATION),
            sourceId = j.optString("sourceId", ""),
            confidence = confidenceFromJson(j.optJSONObject("confidence")),
            observedAt = j.optLong("observedAt", System.currentTimeMillis()),
            updatedAt = j.optLong("updatedAt", System.currentTimeMillis()),
            validFrom = if (j.has("validFrom")) j.getLong("validFrom") else null,
            validUntil = if (j.has("validUntil")) j.getLong("validUntil") else null,
            expectedChangeRate = enumOrDefault(j.optString("expectedChangeRate"), ExpectedChangeRate.STABLE),
            sensitivity = enumOrDefault(j.optString("sensitivity"), Sensitivity.NONE)
        )
    }

    private fun relationshipToJson(r: WorldRelationship): JSONObject {
        val j = JSONObject()
        j.put("relationshipId", r.relationshipId)
        j.put("sourceEntityId", r.sourceEntityId)
        j.put("targetEntityId", r.targetEntityId)
        j.put("type", r.type.id)
        j.put("confidence", confidenceToJson(r.confidence))
        j.put("provenance", r.provenance)
        val ev = JSONArray()
        for (e in r.evidence) ev.put(evidenceToJson(e))
        j.put("evidence", ev)
        if (r.validFrom != null) j.put("validFrom", r.validFrom)
        if (r.validUntil != null) j.put("validUntil", r.validUntil)
        j.put("observedAt", r.observedAt)
        j.putOpt("supersedesRelationshipId", r.supersedesRelationshipId)
        return j
    }

    private fun relationshipFromJson(j: JSONObject): WorldRelationship {
        val evArr = j.optJSONArray("evidence") ?: JSONArray()
        val evidence = mutableListOf<ModelEvidence>()
        for (i in 0 until evArr.length()) evidence.add(evidenceFromJson(evArr.getJSONObject(i)))
        return WorldRelationship(
            relationshipId = j.optString("relationshipId", ""),
            sourceEntityId = j.optString("sourceEntityId", ""),
            targetEntityId = j.optString("targetEntityId", ""),
            type = WorldRelationshipType.fromId(j.optString("type", "related-to")),
            confidence = confidenceFromJson(j.optJSONObject("confidence")),
            provenance = j.optString("provenance", ""),
            evidence = evidence,
            validFrom = if (j.has("validFrom")) j.getLong("validFrom") else null,
            validUntil = if (j.has("validUntil")) j.getLong("validUntil") else null,
            observedAt = j.optLong("observedAt", System.currentTimeMillis()),
            supersedesRelationshipId = j.optString("supersedesRelationshipId", "").ifEmpty { null }
        )
    }

    // ---- shared fact serialization ------------------------------------------------------

    private fun factsToJson(facts: Map<String, ModelFact>): JSONObject {
        val j = JSONObject()
        for ((key, f) in facts) j.put(key, factToJson(f))
        return j
    }

    private fun factsFromJson(j: JSONObject?): Map<String, ModelFact> {
        if (j == null) return emptyMap()
        val out = LinkedHashMap<String, ModelFact>()
        for (key in j.keys()) out[key] = factFromJson(j.getJSONObject(key))
        return out
    }

    private fun factListToJson(facts: List<ModelFact>): JSONArray {
        val arr = JSONArray()
        for (f in facts) arr.put(factToJson(f))
        return arr
    }

    private fun factListFromJson(j: JSONArray?): List<ModelFact> {
        if (j == null) return emptyList()
        val out = mutableListOf<ModelFact>()
        for (i in 0 until j.length()) out.add(factFromJson(j.getJSONObject(i)))
        return out
    }

    private fun factToJson(f: ModelFact): JSONObject {
        val j = JSONObject()
        j.put("factId", f.factId)
        j.put("domain", f.domain.name)
        j.put("factKey", f.factKey)
        j.put("value", f.value)
        j.put("status", f.status.name)
        j.put("confidence", confidenceToJson(f.confidence))
        j.put("sensitivity", f.sensitivity.name)
        val ev = JSONArray()
        for (e in f.evidence) ev.put(evidenceToJson(e))
        j.put("evidence", ev)
        j.put("observedAt", f.observedAt)
        j.put("updatedAt", f.updatedAt)
        j.putOpt("supersededBy", f.supersededBy)
        j.put("conflictingFactIds", JSONArray(f.conflictingFactIds))
        j.putOpt("memoryId", f.memoryId)
        val st = JSONObject()
        st.put("lastVerifiedAt", f.staleness.lastVerifiedAt)
        st.put("expectedChangeRate", f.staleness.expectedChangeRate.name)
        st.put("worldVolatility", f.staleness.worldVolatility.toDouble())
        st.put("isStale", f.staleness.isStale)
        j.put("staleness", st)
        return j
    }

    private fun factFromJson(j: JSONObject): ModelFact {
        val st = j.optJSONObject("staleness") ?: JSONObject()
        val evArr = j.optJSONArray("evidence") ?: JSONArray()
        val evidence = mutableListOf<ModelEvidence>()
        for (i in 0 until evArr.length()) evidence.add(evidenceFromJson(evArr.getJSONObject(i)))
        return ModelFact(
            factId = j.optString("factId", ""),
            domain = enumOrDefault(j.optString("domain"), ModelDomain.SELF),
            factKey = j.optString("factKey", ""),
            value = j.optString("value", ""),
            status = enumOrDefault(j.optString("status"), ModelFactStatus.UNKNOWN),
            confidence = confidenceFromJson(j.optJSONObject("confidence")),
            sensitivity = enumOrDefault(j.optString("sensitivity"), Sensitivity.NONE),
            evidence = evidence,
            observedAt = j.optLong("observedAt", System.currentTimeMillis()),
            updatedAt = j.optLong("updatedAt", System.currentTimeMillis()),
            supersededBy = j.optString("supersededBy", "").ifEmpty { null },
            conflictingFactIds = optStringList(j, "conflictingFactIds"),
            memoryId = j.optString("memoryId", "").ifEmpty { null },
            staleness = StalenessMetadata(
                lastVerifiedAt = st.optLong("lastVerifiedAt", System.currentTimeMillis()),
                expectedChangeRate = enumOrDefault(st.optString("expectedChangeRate"), ExpectedChangeRate.STABLE),
                worldVolatility = st.optDouble("worldVolatility", 0.0).toFloat(),
                isStale = st.optBoolean("isStale", false)
            )
        )
    }

    private fun confidenceToJson(c: ModelConfidence): JSONObject {
        val j = JSONObject()
        j.put("score", c.score.toDouble())
        j.put("sourceReliability", c.sourceReliability.toDouble())
        j.put("confirmationState", c.confirmationState.name)
        j.put("lastValidation", c.lastValidation)
        j.put("evidenceCount", c.evidenceCount)
        j.put("contradictionCount", c.contradictionCount)
        return j
    }

    private fun confidenceFromJson(j: JSONObject?): ModelConfidence {
        if (j == null) return ModelConfidence()
        return ModelConfidence(
            score = j.optDouble("score", 0.5).toFloat(),
            sourceReliability = j.optDouble("sourceReliability", 0.5).toFloat(),
            confirmationState = enumOrDefault(j.optString("confirmationState"), ConfirmationState.UNCONFIRMED),
            lastValidation = j.optLong("lastValidation", 0),
            evidenceCount = j.optInt("evidenceCount", 0),
            contradictionCount = j.optInt("contradictionCount", 0)
        )
    }

    private fun evidenceToJson(e: ModelEvidence): JSONObject {
        val j = JSONObject()
        j.put("source", e.source.name)
        j.put("description", e.description)
        j.put("sourceId", e.sourceId)
        j.put("strength", e.strength.toDouble())
        j.put("timestamp", e.timestamp)
        return j
    }

    private fun evidenceFromJson(j: JSONObject): ModelEvidence =
        ModelEvidence(
            source = enumOrDefault(j.optString("source"), ModelEvidenceSource.SYSTEM_DECLARATION),
            description = j.optString("description", ""),
            sourceId = j.optString("sourceId", ""),
            strength = j.optDouble("strength", 0.5).toFloat(),
            timestamp = j.optLong("timestamp", System.currentTimeMillis())
        )

    // ---- helpers -----------------------------------------------------------------------

    private fun optStringList(obj: JSONObject, key: String): List<String> {
        val arr = obj.optJSONArray(key) ?: return emptyList()
        val out = mutableListOf<String>()
        for (i in 0 until arr.length()) out.add(arr.optString(i, ""))
        return out
    }

    private fun optStringMap(obj: JSONObject, key: String): Map<String, String> {
        val j = obj.optJSONObject(key) ?: return emptyMap()
        val out = LinkedHashMap<String, String>()
        for (k in j.keys()) out[k] = j.optString(k, "")
        return out
    }

    private fun <E : Enum<E>> enumOrDefault(name: String, default: E): E =
        try {
            java.lang.Enum.valueOf(default.javaClass, name)
        } catch (e: Exception) {
            default
        }
}

// ---- results ---------------------------------------------------------------------

sealed class PersistResult {
    data class SUCCESS(val savedCount: Int) : PersistResult()
    data class FAILURE(val failures: List<FailureReport>) : PersistResult()
}

/**
 * LoadResult - One field per model; a field is null when that model's store was
 * absent or corrupt (degraded to empty). Failures list carries the structured
 * reports for corrupt stores.
 */
data class LoadResult(
    val self: SelfModel? = null,
    val user: UserModel? = null,
    val world: WorldModel? = null,
    val failures: List<FailureReport> = emptyList()
) {
    fun restoreInto(store: ModelStore): ModelStore {
        store.restore(
            self ?: store.self,
            user ?: store.user,
            world ?: store.world
        )
        return store
    }
}
