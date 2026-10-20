package com.kenny.localmanager.ui

import org.junit.Test

/**
 * 安全键盘布局结构测试（字母页数字行 + QWERTY 三行；符号拆标点/符号两集）。
 */
class PasswordKeyboardLayoutTest {

    @Test
    fun `大写字母页为数字行加 QWERTY 三行`() {
        val rows = categoryPages(PasswordKeyCategory.UPPER)
        check(rows.map { it.size } == listOf(10, 10, 9, 7)) { "四行键数应为 10/10/9/7，实得 ${rows.map { it.size }}" }
        check(rows[0].joinToString("") == "1234567890") { "首行应为数字行，实得 ${rows[0].joinToString("")}" }
        check(rows[1].joinToString("") == "QWERTYUIOP")
        check(rows[2].joinToString("") == "ASDFGHJKL")
        check(rows[3].joinToString("") == "ZXCVBNM")
        val letters = rows.drop(1).flatten()
        check(letters.size == 26 && letters.toSet().size == 26) { "字母应 26 个无重复，实得 ${letters.size}" }
    }

    @Test
    fun `小写字母页为数字行加 QWERTY 三行`() {
        val rows = categoryPages(PasswordKeyCategory.LOWER)
        check(rows.map { it.size } == listOf(10, 10, 9, 7))
        check(rows[0].joinToString("") == "1234567890")
        check(rows[1].joinToString("") == "qwertyuiop")
        check(rows[2].joinToString("") == "asdfghjkl")
        check(rows[3].joinToString("") == "zxcvbnm")
        check(rows.drop(1).flatten().toSet().size == 26)
    }

    @Test
    fun `标点集与符号集各十六键四乘四网格且无重叠`() {
        val punct = categoryPages(PasswordKeyCategory.PUNCT)
        val symbols = categoryPages(PasswordKeyCategory.SYMBOL)
        check(punct.flatten().size == 16) { "标点应 16 个，实得 ${punct.flatten().size}" }
        check(symbols.flatten().size == 16) { "符号应 16 个，实得 ${symbols.flatten().size}" }
        check(punct.all { it.size == 4 } && punct.size == 4 && symbols.all { it.size == 4 } && symbols.size == 4) { "两集应为 4x4 网格" }
        val overlap = punct.flatten().toSet() intersect symbols.flatten().toSet()
        check(overlap.isEmpty()) { "两集不应重叠，重叠 ${overlap}" }
    }

    @Test
    fun `两集合并覆盖全部三十二个符号`() {
        val all = (categoryPages(PasswordKeyCategory.PUNCT).flatten() +
                categoryPages(PasswordKeyCategory.SYMBOL).flatten()).toSet()
        val expected = listOf(
            '~', '!', '@', '#', '$', '%', '^', '&', '*',
            '(', ')', '_', '+', '-', '=', '[', ']', '{', '}',
            '|', ';', ':', '\'', '"', ',', '.', '<', '>', '/', '?', '`', '\\'
        ).toSet()
        check(all == expected) {
            "两集合并应恰为原 32 个符号，差异：缺 ${(expected - all)}、多 ${(all - expected)}"
        }
    }
}
