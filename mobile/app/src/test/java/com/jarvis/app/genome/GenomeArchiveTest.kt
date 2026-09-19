package com.jarvis.app.genome

import com.jarvis.app.humancore.store.FileStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Genome archive: persistent lineage storage.
 * Plain JVM (JUnit 4).
 */
class GenomeArchiveTest {

    private val tempDirs = mutableListOf<File>()

    @After
    fun cleanup() {
        tempDirs.forEach { it.deleteRecursively() }
    }

    private fun tmpDir(): File = File.createTempFile("genome-archive-test", "").apply {
        delete()
        mkdirs()
        tempDirs += this
    }

    private fun genome(id: String): Genome = GenomeBuilder(id)
        .capability("speech_to_text")
        .mutationOperator(MutationOperator.PARAMETER_MUTATION)
        .build()

    @Test
    fun `archive retains lineage through forks`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Default)
        try {
            val archive = GenomeArchive(FileStorage(tmpDir()), scope)
            val root = genome("root")
            archive.put(root)

            val c1 = root.fork(MutationOperator.PARAMETER_MUTATION, "m1", "c1")
            archive.put(c1)
            val c2 = c1.fork(MutationOperator.ALGORITHM_MUTATION, "m2", "c2")
            archive.put(c2)

            assertEquals(3, archive.count())
            val lineage = archive.lineage("c2")
            // c2 → c1 → root
            assertEquals(3, lineage.size)
            assertEquals("c2", lineage[0].id)
            assertEquals("c1", lineage[1].id)
            assertEquals("root", lineage[2].id)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `archive persists to disk and reloads`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Default)
        val dir = tmpDir()
        try {
            val archive = GenomeArchive(FileStorage(dir), scope)
            archive.put(genome("persisted"))
            // Give the async persistence a moment to write.
            Thread.sleep(200)
        } finally {
            scope.cancel()
        }

        val scope2 = CoroutineScope(Dispatchers.Default)
        try {
            val reloaded = GenomeArchive(FileStorage(dir), scope2)
            val loaded = reloaded.loadFromDisk()
            assertTrue(loaded >= 1)
            assertNotNull(reloaded.get("persisted"))
        } finally {
            scope2.cancel()
        }
    }

    @Test
    fun `children queries find direct lineage`() {
        val archive = GenomeArchive(FileStorage(tmpDir()), CoroutineScope(Dispatchers.Default))
        val root = genome("root")
        archive.put(root)
        archive.put(root.fork(MutationOperator.PARAMETER_MUTATION, "m1", "kid1"))
        archive.put(root.fork(MutationOperator.STRATEGY_REPLACEMENT, "m2", "kid2"))

        val children = archive.children("root")
        assertEquals(2, children.size)
        assertTrue(children.any { it.id == "kid1" })
        assertTrue(children.any { it.id == "kid2" })
    }

    @Test
    fun `archive returns null for unknown genome`() {
        val archive = GenomeArchive(FileStorage(tmpDir()), CoroutineScope(Dispatchers.Default))
        assertNull(archive.get("nonexistent"))
    }

    @Test
    fun `accepted and rejected genomes tracked separately`() {
        val archive = GenomeArchive(FileStorage(tmpDir()), CoroutineScope(Dispatchers.Default))
        archive.put(genome("good").copy(healthState = GenomeHealth.HEALTHY))
        archive.put(genome("bad").copy(healthState = GenomeHealth.REJECTED))
        archive.put(genome("testing").copy(healthState = GenomeHealth.TESTING))

        assertEquals(1, archive.allAccepted().size)
        assertEquals(1, archive.allRejected().size)
        assertEquals(1, archive.allTesting().size)
    }
}
