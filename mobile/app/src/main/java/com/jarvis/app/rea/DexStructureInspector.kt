package com.jarvis.app.rea

import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import java.io.BufferedInputStream
import java.io.File
import java.net.URI
import java.util.zip.ZipFile

data class DexStructureReport(
    val fileName: String,
    val byteSize: Long,
    val parsed: Boolean,
    val classCount: Int,
    val methodCount: Int,
    val fieldCount: Int,
    val packageNames: List<String>,
    val classDescriptors: List<String>,
    val interestingMethods: List<String>,
    val apiHosts: List<String>,
    val warning: String? = null,
    val methodEvidence: List<DexMethodEvidence> = emptyList()
) {
    fun toJson(): org.json.JSONObject = org.json.JSONObject()
        .put("fileName", fileName)
        .put("byteSize", byteSize)
        .put("parsed", parsed)
        .put("classCount", classCount)
        .put("methodCount", methodCount)
        .put("fieldCount", fieldCount)
        .put("packageNames", org.json.JSONArray(packageNames))
        .put("classDescriptors", org.json.JSONArray(classDescriptors))
        .put("interestingMethods", org.json.JSONArray(interestingMethods))
        .put("apiHosts", org.json.JSONArray(apiHosts))
        .put("methodEvidence", org.json.JSONArray(methodEvidence.map { it.toJson() }))
        .put("warning", warning ?: org.json.JSONObject.NULL)
}

data class DexMethodEvidence(
    val signature: String,
    val opcodeSequence: List<String>,
    val referencedSymbols: List<String>,
    val stringLiterals: List<String>
) {
    fun toJson(): org.json.JSONObject = org.json.JSONObject()
        .put("signature", signature)
        .put("opcodes", org.json.JSONArray(opcodeSequence))
        .put("references", org.json.JSONArray(referencedSymbols))
        .put("strings", org.json.JSONArray(stringLiterals))
}

/**
 * Bounded static DEX inspection. It extracts class, method, opcode, reference
 * and literal evidence without executing APK code or attempting to reconstruct
 * proprietary source. This is a lightweight on-device REA pass; heavier
 * semantic reconstruction remains a cloud-side task.
 */
object DexStructureInspector {
    private const val MAX_DEX_FILES = 8
    private const val MAX_DEX_BYTES = 32L * 1024L * 1024L
    private const val MAX_TOTAL_DEX_BYTES = 64L * 1024L * 1024L
    private const val MAX_CLASSES = 10_000
    private const val MAX_METHODS = 20_000
    private const val MAX_STRINGS_SCANNED = 12_000
    private const val MAX_RECORDED_SYMBOLS = 100
    private const val MAX_METHOD_EVIDENCE = 24
    private const val MAX_INSTRUCTIONS_PER_METHOD = 40
    private const val MAX_REFERENCES_PER_METHOD = 12
    private const val MAX_LITERALS_PER_METHOD = 8
    private val INTERESTING_METHODS = setOf(
        "connect", "execute", "request", "send", "receive", "parse", "decode",
        "encrypt", "decrypt", "initialize", "init", "start", "stop", "build",
        "load", "invoke", "register", "open", "read", "write", "query",
        "observe", "create", "update", "download", "upload", "process", "analyze"
    )

    fun inspect(apk: File): List<DexStructureReport> {
        val reports = mutableListOf<DexStructureReport>()
        var totalBytes = 0L
        ZipFile(apk).use { zip ->
            val entries = zip.entries().asSequence()
                .filter { it.name.matches(Regex("""classes(\d*)\.dex""")) }
                .sortedBy { it.name }
                .take(MAX_DEX_FILES)
                .toList()
            for (entry in entries) {
                val size = entry.size
                if (size <= 0L || size > MAX_DEX_BYTES || totalBytes + size > MAX_TOTAL_DEX_BYTES) {
                    reports += DexStructureReport(entry.name, size.coerceAtLeast(0), false,
                        0, 0, 0, emptyList(), emptyList(), emptyList(), emptyList(),
                        "DEX size exceeds the on-device inspection budget.")
                    continue
                }
                totalBytes += size
                val report = runCatching {
                    BufferedInputStream(zip.getInputStream(entry)).use { input ->
                        val dex = DexBackedDexFile.fromInputStream(Opcodes.getDefault(), input)
                        val classes = dex.classes.take(MAX_CLASSES)
                        val packages = linkedSetOf<String>()
                        val classNames = linkedSetOf<String>()
                        val methods = linkedSetOf<String>()
                        val methodEvidence = mutableListOf<DexMethodEvidence>()
                        var methodCount = 0
                        var fieldCount = 0
                        for (classDef in classes) {
                            val descriptor = classDef.type
                            classNames += descriptor
                            val packageName = descriptor.removePrefix("L").substringBeforeLast('/', "")
                                .replace('/', '.')
                            if (packageName.isNotBlank()) packages += packageName
                            fieldCount += classDef.fields.count()
                            for (method in classDef.methods) {
                                methodCount++
                                val parameters = method.parameterTypes.joinToString("") { it.toString() }
                                val signature = descriptor + "->" + method.name + "(" + parameters + ")" + method.returnType
                                if (method.name.lowercase() in INTERESTING_METHODS && methods.size < MAX_RECORDED_SYMBOLS) {
                                    methods += signature
                                }
                                // Capture only a small, deterministic slice of method bodies.
                                // This gives the cloud REA actual behavioural clues (calls,
                                // opcodes and embedded protocol strings) without shipping DEX.
                                if (methodEvidence.size < MAX_METHOD_EVIDENCE && method.implementation != null) {
                                    runCatching {
                                        val opcodes = mutableListOf<String>()
                                        val references = linkedSetOf<String>()
                                        val literals = linkedSetOf<String>()
                                        method.implementation.instructions
                                            .take(MAX_INSTRUCTIONS_PER_METHOD)
                                            .forEach { instruction ->
                                                opcodes += instruction.opcode.name
                                                val reference = (instruction as? ReferenceInstruction)?.reference
                                                when (reference) {
                                                    is StringReference -> if (literals.size < MAX_LITERALS_PER_METHOD) {
                                                        literals += reference.string.take(180)
                                                    }
                                                    null -> Unit
                                                    else -> if (references.size < MAX_REFERENCES_PER_METHOD) {
                                                        references += reference.toString().take(220)
                                                    }
                                                }
                                            }
                                        if (opcodes.isNotEmpty()) {
                                            methodEvidence += DexMethodEvidence(
                                                signature = signature.take(320),
                                                opcodeSequence = opcodes,
                                                referencedSymbols = references.toList(),
                                                stringLiterals = literals.toList()
                                            )
                                        }
                                    }
                                }
                                if (methodCount >= MAX_METHODS) break
                            }
                            if (methodCount >= MAX_METHODS) break
                        }
                        val hosts = linkedSetOf<String>()
                        dex.stringReferences.take(MAX_STRINGS_SCANNED).forEach { reference ->
                            val value = reference.string
                            if (value.startsWith("https://") || value.startsWith("http://")) {
                                runCatching { URI(value).host?.lowercase() }
                                    .getOrNull()
                                    ?.takeIf { it.length in 4..253 && !it.contains('@') }
                                    ?.let(hosts::add)
                            }
                        }
                        DexStructureReport(
                            fileName = entry.name,
                            byteSize = size,
                            parsed = true,
                            classCount = dex.classes.size,
                            methodCount = methodCount,
                            fieldCount = fieldCount,
                            packageNames = packages.toList().sorted().take(MAX_RECORDED_SYMBOLS),
                            classDescriptors = classNames.toList().sorted().take(MAX_RECORDED_SYMBOLS),
                            interestingMethods = methods.toList().sorted(),
                            apiHosts = hosts.toList().sorted().take(40),
                            methodEvidence = methodEvidence
                        )
                    }
                }.getOrElse { failure ->
                    DexStructureReport(entry.name, size, false, 0, 0, 0,
                        emptyList(), emptyList(), emptyList(), emptyList(),
                        failure.message?.take(240) ?: "DEX parsing failed.")
                }
                reports += report
            }
        }
        return reports
    }
}
