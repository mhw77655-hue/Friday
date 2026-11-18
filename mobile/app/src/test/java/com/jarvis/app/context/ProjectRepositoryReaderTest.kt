package com.jarvis.app.context

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class ProjectRepositoryReaderTest {
    @Test fun rejectsTraversalAndSensitivePathsBeforeNetworkAccess() = runBlocking {
        val reader = ProjectRepositoryReader()
        val result = reader.readFiles(listOf("../.env", "mobile/app/local.properties", ".ralph/private_archive.md"))
        assertTrue(result.contains("REJECTED SOURCE PATH"))
        assertTrue(!result.contains("SOURCE: ../.env"))
    }
    @Test fun allowsCredentialAndTokenSourceCodeButRejectsSecretLocations() {
        val reader = ProjectRepositoryReader()
        assertTrue(reader.isAllowedPath("mobile/app/src/main/java/com/jarvis/app/model/remote/ProviderCredentialStore.kt"))
        assertTrue(reader.isAllowedPath("mobile/app/src/main/java/com/jarvis/app/model/TokenUsage.kt"))
        assertFalse(reader.isAllowedPath("mobile/app/src/main/java/com/jarvis/app/secrets/ApiKey.kt"))
        assertFalse(reader.isAllowedPath("mobile/app/local.properties"))
    }
}
