package com.jarvis.app

import org.junit.Assert.assertEquals
import org.junit.Test
import java.lang.reflect.Modifier

class ShellServiceContractParityTest {

    @Test
    fun `privileged service operations match the binder contract`() {
        val contract = IJarvisShellService::class.java.declaredMethods
            .filter { !it.isSynthetic }
            .map { it.name }
            .toSet()

        val implementation = JarvisShellService::class.java.declaredMethods
            .filter { Modifier.isPublic(it.modifiers) && !it.isSynthetic }
            .map { it.name }
            .toSet()

        assertEquals(
            "every operation implemented by the UserService must be declared by its Binder contract",
            contract,
            implementation
        )
    }

    @Test
    fun `every non destruction operation has a binder transaction`() {
        val operations = IJarvisShellService::class.java.declaredMethods
            .filter { !it.isSynthetic }
            .map { it.name }
            .toSet() - "destroy"

        val transactions = IJarvisShellService.Stub::class.java.declaredFields
            .filter { Modifier.isStatic(it.modifiers) && it.name.startsWith("TRANSACTION_") }
            .map { it.name }
            .toSet()

        assertEquals(operations.size, transactions.size)
    }
}
