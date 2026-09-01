package com.kenny.localmanager.family

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HttpRangeUtilsTest {

    @Test
    fun parseTotalSize_validHeader_returnsTotal() {
        assertEquals(200L, parseTotalSizeFromContentRange("bytes 0-99/200"))
    }

    @Test
    fun parseTotalSize_unknownTotal_returnsNull() {
        assertNull(parseTotalSizeFromContentRange("bytes 0-99/*"))
    }

    @Test
    fun parseTotalSize_blankHeader_returnsNull() {
        assertNull(parseTotalSizeFromContentRange("  "))
    }

    @Test
    fun parseTotalSize_zeroOrNegative_returnsNull() {
        assertNull(parseTotalSizeFromContentRange("bytes 0-99/0"))
        assertNull(parseTotalSizeFromContentRange("bytes 0-99/-1"))
    }
}
