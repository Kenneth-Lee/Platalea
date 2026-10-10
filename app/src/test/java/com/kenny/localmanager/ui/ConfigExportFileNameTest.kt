package com.kenny.localmanager.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** normalizeConfigExportFileName 后缀规范化：加密 .json.gpg / 明文 .json。 */
class ConfigExportFileNameTest {

    @Test
    fun `plain export appends json`() {
        assertEquals("config.json", normalizeConfigExportFileName("config", encrypt = false))
        assertEquals("config.json", normalizeConfigExportFileName("config.json", encrypt = false))
    }

    @Test
    fun `plain export strips gpg suffix`() {
        assertEquals("config.json", normalizeConfigExportFileName("config.json.gpg", encrypt = false))
    }

    @Test
    fun `encrypted export appends json gpg`() {
        assertEquals("config.json.gpg", normalizeConfigExportFileName("config", encrypt = true))
        assertEquals("config.json.gpg", normalizeConfigExportFileName("config.json", encrypt = true))
        assertEquals("config.json.gpg", normalizeConfigExportFileName("config.json.gpg", encrypt = true))
    }

    @Test
    fun `suffix match is case insensitive`() {
        assertEquals("A.JSON.gpg", normalizeConfigExportFileName("A.JSON", encrypt = true))
        assertEquals("A.JSON", normalizeConfigExportFileName("A.JSON.GPG", encrypt = false))
    }

    @Test
    fun `blank or path separators are rejected`() {
        assertNull(normalizeConfigExportFileName("   ", encrypt = true))
        assertNull(normalizeConfigExportFileName("a/b", encrypt = true))
        assertNull(normalizeConfigExportFileName("a\\b", encrypt = false))
    }

    @Test
    fun `input is trimmed`() {
        assertEquals("my cfg.json.gpg", normalizeConfigExportFileName("  my cfg ", encrypt = true))
    }
}
