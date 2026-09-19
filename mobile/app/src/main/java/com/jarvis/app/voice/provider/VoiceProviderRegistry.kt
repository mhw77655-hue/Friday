package com.jarvis.app.voice.provider

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * R2 — provider registry inside the `:voice` habitat.
 *
 * Discoverable, priority-ordered, enable/disable-gated. The habitat builds this
 * at service start and the [VoiceProviderSelector] asks it "who can do X?".
 *
 * This is the *provider-level* registry (in-habitat). The app-side
 * [com.jarvis.app.capability.CapabilityRegistry] separately declares the
 * `voice` capability to the nervous system — two concerns, no duplication.
 */
class VoiceProviderRegistry {

    private val providers = LinkedHashMap<String, VoiceProvider>()
    private val priorities = HashMap<String, Int>()
    private val disabled = ConcurrentHashMap.newKeySet<String>()
    private val orderCounter = AtomicInteger(0)
    private val order = HashMap<String, Int>()

    companion object {
        const val DEFAULT_PRIORITY = 100
        const val HIGH_PRIORITY = 0
        const val LOW_PRIORITY = 200
    }

    /** Register a provider. Lower [priority] wins; later ties keep stable FIFO order. */
    fun register(provider: VoiceProvider, priority: Int = DEFAULT_PRIORITY) {
        synchronized(providers) {
            providers[provider.id] = provider
            priorities[provider.id] = priority
            order[provider.id] = orderCounter.getAndIncrement()
        }
    }

    fun unregister(id: String) {
        synchronized(providers) {
            providers.remove(id); priorities.remove(id); order.remove(id); disabled.remove(id)
        }
    }

    fun get(id: String): VoiceProvider? = synchronized(providers) { providers[id] }

    /** All registered providers (FIFO registration order). */
    fun all(): List<VoiceProvider> = synchronized(providers) { providers.values.toList() }

    fun isEnabled(id: String): Boolean = !disabled.contains(id)

    fun setEnabled(id: String, enabled: Boolean) {
        if (enabled) disabled.remove(id) else disabled.add(id)
    }

    /** Provider priority (lower = more preferred). */
    fun priorityOf(id: String): Int = synchronized(providers) { priorities[id] ?: DEFAULT_PRIORITY }

    /** Capability metadata for a provider, or null if unknown. */
    fun capabilitiesOf(id: String): VoiceCapabilities? = get(id)?.capabilities()

    /**
     * Discover providers that can serve [language] (default: any). Enabled,
     * priority-ordered. [requireStreaming]/[requireCloning] narrow strictly to
     * providers whose metadata truthfully claims it.
     */
    fun discover(
        language: String? = null,
        requireStreaming: Boolean = false,
        requireCloning: Boolean = false
    ): List<VoiceProvider> {
        val lang = language?.lowercase()?.trim().takeUnless { it.isNullOrBlank() }
        return all()
            .filter { isEnabled(it.id) }
            .filter { p ->
                (lang == null || p.capabilities().languages.any { l ->
                    lang == l.lowercase() || lang.startsWith(l.lowercase()) || l.lowercase().startsWith(lang)
                }) &&
                    (!requireStreaming || p.capabilities().streaming) &&
                    (!requireCloning || p.capabilities().cloning)
            }
            .sortedWith(compareBy({ priorityOf(it.id) }, { order[it.id] ?: Int.MAX_VALUE }))
    }

    /**
     * Resolve a requested [voiceId] across the registry to (provider, voice).
     * Voice ids are provider-local, so this matches any provider whose voice
     * list contains the id (preferring the provider the caller hinted).
     */
    fun resolveVoice(voiceId: String?, preferredProvider: String? = null): Pair<VoiceProvider?, VoiceInfo?> {
        if (voiceId.isNullOrBlank()) return preferredProvider?.let { get(it) } to null
        val preferred = preferredProvider?.let { get(it) }
        preferred?.let { p -> voicesOf(p).firstOrNull { it.id == voiceId }?.let { return p to it } }
        for (p in discover()) {
            voicesOf(p).firstOrNull { it.id == voiceId }?.let { return p to it }
        }
        return null to null
    }

    /** The voices a provider advertises (empty when it has none). */
    fun voicesOf(provider: VoiceProvider): List<VoiceInfo> = provider.voices()
}
