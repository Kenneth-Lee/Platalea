package com.kenny.localmanager.file

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

class FileSearchQueryParsersTest {

    // ---------- 大小范围 ----------

    @Test
    fun `大小范围两端`() {
        val r = parseSizeRange("1m-2m")!!
        assertEquals(1024L * 1024, r.minBytes)
        assertEquals(2L * 1024 * 1024, r.maxBytes)
        // 小数与单位变体
        val r2 = parseSizeRange("0.5G-1T")!!
        assertEquals((0.5 * 1024 * 1024 * 1024).toLong(), r2.minBytes)
        assertEquals(1024L * 1024 * 1024 * 1024, r2.maxBytes)
    }

    @Test
    fun `大小范围只限一端`() {
        assertEquals(1024L, parseSizeRange("1k-")!!.minBytes)
        assertNull(parseSizeRange("1k-")!!.maxBytes)
        assertNull(parseSizeRange("-2b")!!.minBytes)
        assertEquals(2L, parseSizeRange("-2b")!!.maxBytes)
        // 空白与留空
        assertNull(parseSizeRange("  ")!!.minBytes)
        assertNull(parseSizeRange("")!!.maxBytes)
    }

    @Test
    fun `大小范围非法输入`() {
        assertNull(parseSizeRange("1m"))
        assertNull(parseSizeRange("1x-2m"))
        assertNull(parseSizeRange("2m-1m")) // 下限大于上限
        assertNull(parseSizeRange("1m--2m"))
        assertNull(parseSizeRange("a-b"))
    }

    // ---------- 修改时间 ----------

    @Test
    fun `最近 N 单位内`() {
        val now = Calendar.getInstance().apply {
            set(2026, Calendar.MARCH, 15, 10, 30, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val d3 = parseTimeRange("3d", now)!!
        val expect = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_MONTH, -3)
        }.timeInMillis
        assertEquals(expect, d3.afterMillis)
        assertNull(d3.beforeMillis)
        // 月/年按日历周期
        val m4 = parseTimeRange("4m", now)!!
        val expectM = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.MONTH, -4)
        }.timeInMillis
        assertEquals(expectM, m4.afterMillis)
        val y1 = parseTimeRange("1y", now)!!
        val expectY = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.YEAR, -1)
        }.timeInMillis
        assertEquals(expectY, y1.afterMillis)
    }

    @Test
    fun `N 单位之前`() {
        val now = 1_700_000_000_000L
        val r = parseTimeRange("-3d", now)!!
        assertNull(r.afterMillis)
        val expect = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_MONTH, -3)
        }.timeInMillis
        assertEquals(expect, r.beforeMillis)
        // 留空
        assertNull(parseTimeRange("", now)!!.afterMillis)
    }

    @Test
    fun `时间非法输入`() {
        val now = 0L
        assertNull(parseTimeRange("3", now))
        assertNull(parseTimeRange("3w", now))
        assertNull(parseTimeRange("--3d", now))
        assertNull(parseTimeRange("3d4h", now))
    }
}
