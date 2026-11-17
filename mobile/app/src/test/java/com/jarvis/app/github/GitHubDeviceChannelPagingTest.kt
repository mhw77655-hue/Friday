package com.jarvis.app.github

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubDeviceChannelPagingTest {
    private fun comments(vararg ids: Long): List<JSONObject> =
        ids.map { JSONObject().put("id", it).put("body", "comment-$it") }

    @Test
    fun `fetches older pages until saved cursor and returns chronological comments`() {
        val requestedPages = mutableListOf<Int>()
        val result = collectNewDeviceChannelComments(
            lastSeenId = 296,
            pageSize = 3,
            maxPages = 5
        ) { page ->
            requestedPages += page
            when (page) {
                1 -> comments(300, 299, 298)
                2 -> comments(297, 296, 295)
                else -> error("pagination should stop when it reaches the cursor")
            }
        }

        assertEquals(listOf(1, 2), requestedPages)
        assertEquals(listOf(297L, 298L, 299L, 300L), result.map { it.getLong("id") })
    }

    @Test
    fun `empty first page is a valid no-new-comments result`() {
        val result = collectNewDeviceChannelComments(100) { emptyList() }
        assertTrue(result.isEmpty())
    }

    @Test
    fun `short final page stops pagination without skipping its comments`() {
        val result = collectNewDeviceChannelComments(
            lastSeenId = 0,
            pageSize = 3,
            maxPages = 3
        ) { page ->
            when (page) {
                1 -> comments(3, 2, 1)
                2 -> comments(6, 5)
                else -> error("short page should terminate pagination")
            }
        }
        assertEquals(listOf(1L, 2L, 3L, 5L, 6L), result.map { it.getLong("id") })
    }

    @Test
    fun `missing comment id fails closed`() {
        val result = runCatching {
            collectNewDeviceChannelComments(0) { listOf(JSONObject().put("body", "no id")) }
        }
        assertTrue(result.exceptionOrNull() is java.io.IOException)
    }

    @Test
    fun `page limit fails without returning a partial cursor advance`() {
        val result = runCatching {
            collectNewDeviceChannelComments(
                lastSeenId = 0,
                pageSize = 2,
                maxPages = 2
            ) { page -> if (page == 1) comments(4, 3) else comments(2, 1) }
        }
        assertTrue(result.exceptionOrNull() is java.io.IOException)
    }
}
