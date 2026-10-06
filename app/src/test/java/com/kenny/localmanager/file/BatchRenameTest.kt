package com.kenny.localmanager.file

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchRenameTest {

    private fun compileOk(pattern: String, template: String): BatchRenamePlan {
        val result = BatchRenamePlan.compile(pattern, template)
        assertTrue("compile should be Ok: $result", result is BatchRenameCompileResult.Ok)
        return (result as BatchRenameCompileResult.Ok).plan
    }

    // ---------- 编译期校验 ----------

    @Test
    fun `空模式与空模板的隐式语义`() {
        // 空正则 = 匹配整个文件名；空模板 = 保留匹配段（\0）
        val plan = compileOk("", "")
        assertEquals("ab.txt", plan.applyTo("ab.txt"))
        assertEquals("x ab.txt y", compileOk("", "x \\0 y").applyTo("ab.txt"))
        assertEquals("ab.txt", compileOk("(a)(b)", "").applyTo("ab.txt")) // 空模板保留匹配段（其余部分不动）
    }

    @Test
    fun `空模式下模板引用捕获组报 BadGroupReference`() {
        val result = BatchRenamePlan.compile("", "\\1")
        assertTrue(result is BatchRenameCompileResult.BadGroupReference)
        assertEquals(0, (result as BatchRenameCompileResult.BadGroupReference).available)
    }

    @Test
    fun `非法正则报 InvalidPattern`() {
        val result = BatchRenamePlan.compile("(unclosed", "x")
        assertTrue(result is BatchRenameCompileResult.InvalidPattern)
    }

    @Test
    fun `模板引用越界组报 BadGroupReference`() {
        val result = BatchRenamePlan.compile("a(\\d)", "x\\2y")
        assertTrue(result is BatchRenameCompileResult.BadGroupReference)
        val bad = result as BatchRenameCompileResult.BadGroupReference
        assertEquals(2, bad.group)
        assertEquals(1, bad.available)
    }

    @Test
    fun `转义反斜杠不算组引用`() {
        compileOk("a(\\d)", "x\\\\\\1") // x\\1：字面反斜杠 + 组 1
    }

    // ---------- applyTo（逐匹配替换语义） ----------

    @Test
    fun `基本捕获组替换`() {
        val plan = compileOk("photo_(\\d{3})\\.jpg", "IMG_\\1.jpg")
        assertEquals("IMG_042.jpg", plan.applyTo("photo_042.jpg"))
        assertNull(plan.applyTo("other.png"))
    }

    @Test
    fun `多处匹配全部替换`() {
        val plan = compileOk("\\s+", "_")
        assertEquals("a_b_c.txt", plan.applyTo("a b  c.txt"))
    }

    @Test
    fun `每处匹配的组独立引用`() {
        val plan = compileOk("(\\w)_(\\w)", "\\2\\1")
        assertEquals("ba dc", plan.applyTo("a_b c_d"))
    }

    @Test
    fun `group 0 引用整个匹配段`() {
        val plan = compileOk("\\d+", "[\\0]")
        assertEquals("x[12]y[345]", plan.applyTo("x12y345"))
    }

    @Test
    fun `双反斜杠展开为字面反斜杠`() {
        val plan = compileOk("a", "b\\\\c")
        assertEquals("b\\c", plan.applyTo("a"))
    }

    @Test
    fun `部分匹配只替换匹配段`() {
        val plan = compileOk("IMG", "P")
        assertEquals("P_1234.jpg", plan.applyTo("IMG_1234.jpg"))
    }

    // ---------- 预览构建 ----------

    private fun preview(names: List<String>, pattern: String, template: String): List<BatchRenamePreviewItem> {
        return buildBatchRenamePreview(names, compileOk(pattern, template))
    }

    @Test
    fun `不匹配项跳过且不算错误`() {
        val items = preview(listOf("a.txt", "b.log"), "a", "c")
        assertEquals("c.txt", items[0].newName)
        assertNull(items[0].error)
        assertNull(items[1].newName)
        assertNull(items[1].error)
    }

    @Test
    fun `新旧同名视为无变化跳过`() {
        val items = preview(listOf("a.txt"), "a", "a")
        assertNull(items[0].newName)
        assertTrue(items[0].matched)
        assertNull(items[0].error)
    }

    @Test
    fun `预览携带命中区间供高亮`() {
        val items = preview(listOf("ab12cd.txt"), "(\\d)(\\d)", "")
        assertEquals(listOf(IntRange(2, 3)), items[0].matchRanges)
        // 不匹配项无区间
        assertTrue(preview(listOf("z.txt"), "(\\d)", "").all { it.matchRanges.isEmpty() })
    }

    @Test
    fun `预览携带捕获组区间`() {
        // br_([a-z])(\\d) 对 br_a1.txt：组1=[3,3]，组2=[4,4]
        val items = preview(listOf("br_a1.txt"), "br_([a-z])(\\d)", "x")
        assertEquals(listOf(IntRange(3, 3), IntRange(4, 4)), items[0].groupRanges)
        // 隐式空正则无捕获组
        assertTrue(preview(listOf("a.txt"), "", "x\\0").all { it.groupRanges.isEmpty() })
    }

    @Test
    fun `多出匹配的区间都记录`() {
        val items = preview(listOf("a1b2.txt"), "\\d", "x")
        assertEquals(listOf(IntRange(1, 1), IntRange(3, 3)), items[0].matchRanges)
    }

    @Test
    fun `不匹配项 matched 为 false`() {
        val items = preview(listOf("z.txt"), "a", "c")
        assertNull(items[0].newName)
        assertTrue(!items[0].matched)
    }

    @Test
    fun `改名后为空报 EMPTY`() {
        // 模板 \1 且捕获组匹配空串：替换结果为空
        val items = preview(listOf("x.txt"), "().*", "\\1")
        assertEquals("", items[0].newName)
        assertEquals(BatchRenameItemError.EMPTY, items[0].error)
    }

    @Test
    fun `改名后含斜杠报 SLASH`() {
        val items = preview(listOf("a.txt"), "a", "x/y")
        assertEquals("x/y.txt", items[0].newName)
        assertEquals(BatchRenameItemError.SLASH, items[0].error)
    }

    @Test
    fun `批内改名后互撞报 CONFLICT`() {
        val items = preview(listOf("a_1.txt", "b_2.txt"), "[ab]_(\\d)", "c_\\1")
        assertEquals("c_1.txt", items[0].newName)
        assertEquals("c_2.txt", items[1].newName)
        // c_1 与 c_2 不撞——构造真正互撞：
        val clash = preview(listOf("a_1.txt", "b_1.txt"), "[ab]_(\\d)", "c_\\1")
        assertEquals("c_1.txt", clash[0].newName)
        assertEquals("c_1.txt", clash[1].newName)
        assertEquals(BatchRenameItemError.CONFLICT, clash[0].error)
        assertEquals(BatchRenameItemError.CONFLICT, clash[1].error)
    }

    @Test
    fun `改名后撞未改名文件报 CONFLICT`() {
        val items = preview(listOf("a_1.txt", "c_1.txt"), "[a]_(\\d)", "c_\\1")
        assertEquals("c_1.txt", items[0].newName)
        assertEquals(BatchRenameItemError.CONFLICT, items[0].error)
        assertNull(items[1].newName)
        assertNull(items[1].error)
    }

    @Test
    fun `互换名字不报冲突`() {
        // a.txt -> b.txt 且 b.txt -> a.txt：静态集合无互撞，执行期按逐个结果如实报告
        val items = preview(listOf("a.txt", "b.txt"), "^([ab])\\.txt$", "\\1x.txt")
        assertEquals("ax.txt", items[0].newName)
        assertEquals("bx.txt", items[1].newName)
        assertNull(items[0].error)
        assertNull(items[1].error)
    }
}
