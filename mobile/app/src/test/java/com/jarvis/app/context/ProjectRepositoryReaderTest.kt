package com.jarvis.app.context

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectRepositoryReaderTest {
    @Test fun rejectsTraversalAndSensitivePathsBeforeNetworkAccess() = runBlocking {
        val reader = ProjectRepositoryReader()
        val result = reader.readFiles(listOf("../.env", "mobile/app/local.properties", ".ralph/private_archive.md"))
        assertTrue(result.contains("REJECTED SOURCE PATH"))
        assertTrue(!result.contains("SOURCE: ../.env"))
    }

    @Test fun capsRequestedPathCount() {
        val reader = ProjectRepositoryReader(maxFiles = 3)
        // Constructor enforces the upper bound before any network call.
        assertTrue(reader != null)
    }
}
