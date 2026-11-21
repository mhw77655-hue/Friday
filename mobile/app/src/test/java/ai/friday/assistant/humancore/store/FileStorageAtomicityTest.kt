package ai.friday.assistant.humancore.store

import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileStorageAtomicityTest {
    private fun tempDir(): File = File.createTempFile("friday-file-storage-", ".dir").apply {
        delete()
        mkdirs()
    }

    @Test
    fun `separate storage instances serialize writes to the same physical store`() {
        val directory = tempDir()
        val start = CountDownLatch(1)
        val done = CountDownLatch(16)
        val failures = ConcurrentLinkedQueue<Throwable>()
        val expected = (0 until 16).map { index ->
            "writer-$index:" + "x".repeat(20_000)
        }

        expected.forEach { payload ->
            Thread {
                try {
                    start.await()
                    FileStorage(directory).write(StoreKind.IDENTITY, payload)
                } catch (failure: Throwable) {
                    failures.add(failure)
                } finally {
                    done.countDown()
                }
            }.start()
        }

        start.countDown()
        assertTrue("all concurrent writers should finish", done.await(10, TimeUnit.SECONDS))
        assertTrue("no writer should fail: $failures", failures.isEmpty())
        val finalValue = FileStorage(directory).read(StoreKind.IDENTITY)
        assertTrue("the final document must be one complete writer payload", finalValue in expected)
        directory.deleteRecursively()
    }

    @Test
    fun `concurrent appenders preserve every complete line`() {
        val directory = tempDir()
        val start = CountDownLatch(1)
        val done = CountDownLatch(32)
        val failures = ConcurrentLinkedQueue<Throwable>()
        val expected = (0 until 32).map { "failure-$it:" + "y".repeat(2_000) }

        expected.forEach { line ->
            Thread {
                try {
                    start.await()
                    FileStorage(directory).append(StoreKind.FAILURES, line)
                } catch (failure: Throwable) {
                    failures.add(failure)
                } finally {
                    done.countDown()
                }
            }.start()
        }

        start.countDown()
        assertTrue("all appenders should finish", done.await(15, TimeUnit.SECONDS))
        assertTrue("no appender should fail: $failures", failures.isEmpty())
        val actual = FileStorage(directory).read(StoreKind.FAILURES)!!
            .lineSequence().filter { it.isNotBlank() }.toList()
        assertEquals(expected.toSet(), actual.toSet())
        assertEquals(expected.size, actual.size)
        directory.deleteRecursively()
    }

    @Test
    fun `append separates a truncated prior record from the next durable record`() {
        val directory = tempDir()
        val target = File(directory, "humancore/${StoreKind.FAILURES.fileName}")
        target.parentFile?.mkdirs()
        target.writeText("{\"id\":\"truncated\"}") // deliberately no terminating newline

        FileStorage(directory).append(StoreKind.FAILURES, "{\"id\":\"next-valid\"}")

        val lines = FileStorage(directory).read(StoreKind.FAILURES)!!
            .lineSequence().filter { it.isNotBlank() }.toList()
        assertEquals(listOf("{\"id\":\"truncated\"}", "{\"id\":\"next-valid\"}"), lines)
        directory.deleteRecursively()
    }

    @Test
    fun `failed atomic replacement preserves the previous target`() {
        val directory = tempDir()
        val targetDirectory = File(directory, "protected-target").apply { mkdirs() }
        val sentinel = File(targetDirectory, "keep.txt").apply { writeText("previous-data") }

        try {
            FileStorage(directory).write(targetDirectory, "replacement")
            throw AssertionError("Expected atomic replacement of a non-empty directory to fail")
        } catch (_: IOException) {
            assertTrue(sentinel.isFile)
            assertEquals("previous-data", sentinel.readText())
        }
        directory.deleteRecursively()
    }
}
