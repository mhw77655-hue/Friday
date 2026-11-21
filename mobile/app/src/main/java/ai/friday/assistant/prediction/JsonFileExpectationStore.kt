package ai.friday.assistant.prediction

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/** Cross-host, atomic JSON store so Android and workshop builds share one schema. */
class JsonFileExpectationStore(private val file: File) : ExpectationStore {
    private val lock = Any()

    override fun loadTransitions(): List<ExpectationTransition> = synchronized(lock) {
        val array = readRoot().optJSONArray("transitions") ?: JSONArray()
        (0 until array.length()).mapNotNull { index ->
            runCatching {
                val item = array.getJSONObject(index)
                ExpectationTransition(
                    personKey = item.getString("person"),
                    before = item.getJSONObject("before").toStringMap(),
                    after = item.getJSONObject("after").toStringMap(),
                    observedAtMs = item.optLong("at")
                )
            }.getOrNull()
        }
    }

    override fun saveTransitions(transitions: List<ExpectationTransition>) = synchronized(lock) {
        val root = readRoot()
        val array = JSONArray()
        transitions.forEach { transition ->
            array.put(JSONObject().apply {
                put("person", transition.personKey)
                put("before", JSONObject(transition.before))
                put("after", JSONObject(transition.after))
                put("at", transition.observedAtMs)
            })
        }
        root.put("transitions", array)
        writeRoot(root)
    }

    override fun loadCalibration(personKey: String): PredictionCalibration = synchronized(lock) {
        val value = readRoot().optJSONObject("calibration")?.optJSONObject(key(personKey))
            ?: return@synchronized PredictionCalibration()
        PredictionCalibration(
            settledSamples = value.optInt("n"),
            predictorAccuracyEma = value.optDouble("predictor", 0.0),
            baselineAccuracyEma = value.optDouble("baseline", 0.0)
        )
    }

    override fun saveCalibration(personKey: String, calibration: PredictionCalibration) = synchronized(lock) {
        val root = readRoot()
        val values = root.optJSONObject("calibration") ?: JSONObject()
        values.put(key(personKey), JSONObject().apply {
            put("n", calibration.settledSamples)
            put("predictor", calibration.predictorAccuracyEma)
            put("baseline", calibration.baselineAccuracyEma)
        })
        root.put("calibration", values)
        writeRoot(root)
    }

    override fun clearCalibration(personKey: String) = synchronized(lock) {
        val root = readRoot()
        root.optJSONObject("calibration")?.remove(key(personKey))
        writeRoot(root)
    }

    private fun readRoot(): JSONObject {
        if (!file.isFile) return JSONObject()
        return try {
            JSONObject(file.readText(Charsets.UTF_8))
        } catch (_: Exception) {
            // Preserve a damaged store for inspection instead of silently overwriting it.
            val backup = File(file.parentFile, file.name + ".corrupt-" + System.currentTimeMillis())
            runCatching { file.copyTo(backup, overwrite = false) }
            JSONObject()
        }
    }

    private fun writeRoot(root: JSONObject) {
        val parent = file.absoluteFile.parentFile
        parent?.mkdirs()
        val temporary = File(parent, file.name + ".tmp")
        FileOutputStream(temporary).use { output ->
            output.write(root.toString().toByteArray(Charsets.UTF_8))
            output.fd.sync()
        }
        try {
            Files.move(
                temporary.toPath(),
                file.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun key(personKey: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(personKey.toByteArray(Charsets.UTF_8))
        return digest.take(12).joinToString("") { "%02x".format(it) }
    }

    private fun JSONObject.toStringMap(): Map<String, String> {
        val result = linkedMapOf<String, String>()
        keys().forEach { field -> result[field] = optString(field) }
        return result
    }
}
