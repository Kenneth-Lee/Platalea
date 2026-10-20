package com.kenny.localmanager.ui

import org.junit.Test

/**
 * 安全键盘布局结构测试（字母页改传统键盘 QWERTY 三行）。
 */
class PasswordKeyboardLayoutTest {

    private fun flat(pages: List<List<List<Char>>>): List<Char> = pages.flatten().flatten()

    @Test
    fun `大写字母页为 QWERTY 三行且字母齐全无重复`() {
        val pages = categoryPages(PasswordKeyCategory.UPPER)
        check(pages.size == 1) { "字母页应一页，实得 ${pages.size}" }
        val rows = pages[0]
        check(rows.map { it.size } == listOf(10, 9, 7)) { "三行键数应为 10/9/7，实得 ${rows.map { it.size }}" }
        check(rows[0].joinToString("") == "QWERTYUIOP") { "首行应为 QWERTYUIOP，实得 ${rows[0].joinToString("")}" }
        check(rows[1].joinToString("") == "ASDFGHJKL") { "次行应为 ASDFGHJKL，实得 ${rows[1].joinToString("")}" }
        check(rows[2].joinToString("") == "ZXCVBNM") { "末行应为 ZXCVBNM，实得 ${rows[2].joinToString("")}" }
        val letters = flat(pages)
        check(letters.size == 26) { "应含 26 个字母，实得 ${letters.size}" }
        check(letters.toSet().size == 26) { "字母不应重复" }
    }

    @Test
    fun `小写字母页为 QWERTY 三行且字母齐全无重复`() {
        val pages = categoryPages(PasswordKeyCategory.LOWER)
        val rows = pages[0]
        check(rows.map { it.size } == listOf(10, 9, 7))
        check(rows[0].joinToString("") == "qwertyuiop")
        check(rows[1].joinToString("") == "asdfghjkl")
        check(rows[2].joinToString("") == "zxcvbnm")
        check(flat(pages).toSet().size == 26)
    }

    @Test
    fun `数字页保持两行各五键`() {
        val pages = categoryPages(PasswordKeyCategory.DIGIT)
        check(pages.size == 1)
        check(pages[0].map { it.joinToString("") } == listOf("12345", "67890")) {
            "数字页应保持 12345/67890 两行，实得 ${pages[0]}"
        }
    }

    @Test
    fun `符号页保持两页每行五键且字符齐全`() {
        val pages = categoryPages(PasswordKeyCategory.SYMBOL)
        check(pages.size == 2) { "符号页应两页，实得 ${pages.size}" }
        check(pages[0].all { it.size == 5 }) { "符号首页每行应 5 键" }
        check(pages[1].size == 1 && pages[1][0].size == 2) { "符号次页应 1 行 2 键，实得 ${pages[1]}" }
        val all = flat(pages)
        check(all.size == 32) { "符号共 32 个，实得 ${all.size}" }
        check(all.toSet().size == 32) { "符号不应重复" }
    }
}
