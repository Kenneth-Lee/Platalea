package com.kenny.localmanager.file

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileSearchContentTest {

    @Test
    fun `字面内容匹配`() {
        val bytes = "hello world\nsecond line\n".toByteArray()
        assertTrue(fileContentMatches(bytes, Regex("world")))
        assertFalse(fileContentMatches(bytes, Regex("worle")))
    }

    @Test
    fun `正则语义与 UTF-8 中文`() {
        val bytes = "第1行\n第2行\n".toByteArray(Charsets.UTF_8)
        assertTrue(fileContentMatches(bytes, Regex("第\\d行")))
        assertFalse(fileContentMatches(bytes, Regex("第[3-9]行")))
        // 多行内容中 ^ $ 默认按整串锚定（与 Kotlin Regex 一致）
        assertFalse(fileContentMatches(bytes, Regex("^第2行")))
    }

    @Test
    fun `空内容与空模式`() {
        assertFalse(fileContentMatches(ByteArray(0), Regex("x")))
        assertTrue(fileContentMatches(ByteArray(0), Regex("")))
    }
}
