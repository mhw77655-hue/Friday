package com.jarvis.app.microsystem

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Registry of active micro-systems. The nervous system discovers and
 * routes to micro-systems through this registry. Micro-systems register
 * themselves after construction and unregister on shutdown.
 *
 * Thread-safe, dormant when unused.
 */
class MicroSystemRegistry {

    private val systems = ConcurrentHashMap<String, MicroSystemContract>()

    private val _registered = MutableStateFlow<List<String>>(emptyList())
    val registered: StateFlow<List<String>> = _registered.asStateFlow()

    fun register(system: MicroSystemContract) {
        systems[system.id] = system
        _registered.value = systems.keys.sorted()
    }

    fun unregister(id: String) {
        systems.remove(id)
        _registered.value = systems.keys.sorted()
    }

    fun get(id: String): MicroSystemContract? = systems[id]

    fun has(id: String): Boolean = systems.containsKey(id)

    fun provides(capability: String): List<MicroSystemContract> =
        systems.values.filter { capability in it.capabilities }

    fun requires(capability: String): List<MicroSystemContract> =
        systems.values.filter { capability in it.requiredCapabilities }

    fun all(): Collection<MicroSystemContract> = systems.values

    fun count(): Int = systems.size
}
