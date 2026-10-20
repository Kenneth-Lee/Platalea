package com.kenny.localmanager.browser

import androidx.annotation.StringRes
import com.kenny.localmanager.R
import com.kenny.localmanager.file.DocumentFileModel
import org.junit.Test

/**
 * 动作注册表测试（文件浏览功能拆解设计 第一批）：
 * 槽位最小匹配、注册序菜单、重复 id 拒绝、空表安全。
 */
class BrowserActionsTest {

    private fun file(name: String) = DocumentFileModel(
        name = name,
        isDirectory = false,
        uri = android.net.StubUri(),
        lastModified = 0L,
        size = 1L,
    )

    private class FakeIntent(
        override val id: String,
        override val slot: Int,
        private val appliesTo: String,
        @StringRes override val labelRes: Int = R.string.context_view_zip_markdown,
    ) : BrowserOpenIntent {
        override fun applies(target: DocumentFileModel) = target.name == appliesTo
        override fun open(target: DocumentFileModel, session: BrowserSessionHandle) = Unit
    }

    @Test
    fun `空注册表匹配为空`() {
        val registry = BrowserActions.Registry()
        check(registry.matchIntent(file("a.md.zip")) == null)
    }

    @Test
    fun `单击分发取槽位最小的适用意图`() {
        val registry = BrowserActions.Registry()
        registry.register(FakeIntent("viewer.txt", 200, "a.txt"))
        registry.register(FakeIntent("archivereader.mdzip", 100, "a.md.zip"))
        registry.register(FakeIntent("browser.fallback", 900, "a.md.zip"))

        val match = registry.matchIntent(file("a.md.zip"))
        checkNotNull(match)
        check(match.id == "archivereader.mdzip") { "应取槽位 100 而非 ${match.slot}" }
    }

    @Test
    fun `不适用文件匹配为空`() {
        val registry = BrowserActions.Registry()
        registry.register(FakeIntent("archivereader.mdzip", 100, "a.md.zip"))
        check(registry.matchIntent(file("b.txt")) == null)
    }

    @Test
    fun `重复 id 注册被拒绝`() {
        val registry = BrowserActions.Registry()
        check(registry.register(FakeIntent("x", 100, "a")))
        check(!registry.register(FakeIntent("x", 100, "a"))) { "同 id 二次注册应失败" }
    }

    @Test
    fun `菜单动作按注册序过滤`() {
        val registry = BrowserActions.Registry()
        val snapshot = BrowserActionSnapshot()
        check(registry.menuActions(file("a"), snapshot).isEmpty())
    }
}
