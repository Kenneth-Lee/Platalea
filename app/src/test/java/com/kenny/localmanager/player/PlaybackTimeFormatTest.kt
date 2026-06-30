package com.kenny.localmanager.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackTimeFormatTest {

    @Test
    fun formatMs_zero() {
        assertEquals("00:00", PlaybackTimeFormat.formatMs(0))
    }

    @Test
    fun formatMs_minutesAndSeconds() {
        assertEquals("01:05", PlaybackTimeFormat.formatMs(65_000))
    }

    @Test
    fun formatMs_hours() {
        assertEquals("1:02:03", PlaybackTimeFormat.formatMs((3600 + 120 + 3) * 1000L))
    }

    @Test
    fun parseToMs_plainSeconds() {
        assertEquals(90_000L, PlaybackTimeFormat.parseToMs("90"))
    }

    @Test
    fun parseToMs_mmSs() {
        assertEquals(65_000L, PlaybackTimeFormat.parseToMs("1:05"))
    }

    @Test
    fun parseToMs_hhMmSs() {
        assertEquals((3600 + 120 + 3) * 1000L, PlaybackTimeFormat.parseToMs("1:02:03"))
    }

    @Test
    fun parseToMs_emptyIsZero() {
        assertEquals(0L, PlaybackTimeFormat.parseToMs(""))
        assertEquals(0L, PlaybackTimeFormat.parseToMs("   "))
    }

    @Test
    fun parseToMs_invalid() {
        assertNull(PlaybackTimeFormat.parseToMs("abc"))
        assertNull(PlaybackTimeFormat.parseToMs("1:xx"))
        assertNull(PlaybackTimeFormat.parseToMs("1:2:3:4"))
    }
}
