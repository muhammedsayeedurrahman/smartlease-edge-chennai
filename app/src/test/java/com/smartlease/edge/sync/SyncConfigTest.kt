package com.smartlease.edge.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncConfigTest {

    @Test
    fun `default config is opt-in disabled and points at the documented default base URL`() {
        // Sync is opt-in: an inspection must never depend on a network that may not be
        // there, and DEFAULT_BASE_URL is a placeholder host that does not resolve -- see
        // the KDoc on SyncConfig.syncEnabled. A build that wants sync turns it on
        // explicitly alongside a real baseUrl.
        val config = SyncConfig()
        assertFalse(config.syncEnabled)
        assertEquals(SyncConfig.DEFAULT_BASE_URL, config.baseUrl)
    }

    @Test
    fun `baseUrl without a trailing slash is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            SyncConfig(baseUrl = "https://example.com/api/v1")
        }
    }

    @Test
    fun `non-positive timeout is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            SyncConfig(connectTimeoutMs = 0)
        }
    }

    @Test
    fun `a custom base URL overrides the default`() {
        val config = SyncConfig(baseUrl = "https://staging.example.com/api/v1/")
        assertEquals("https://staging.example.com/api/v1/", config.baseUrl)
    }

    @Test
    fun `sync can be explicitly opted into`() {
        val config = SyncConfig(syncEnabled = true, baseUrl = "https://staging.example.com/api/v1/")
        assertTrue(config.syncEnabled)
    }
}
