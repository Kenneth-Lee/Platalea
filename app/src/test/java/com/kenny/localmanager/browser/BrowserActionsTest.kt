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
        override fun open(ctx: BrowserActionContext) = Unit
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

    @Test
    fun `待处理切换按快照取文案与方向`() {
        val registry = BrowserActions.Registry()
        BrowserIntents.registerPendingToggle(registry)

        val target = file("a.txt")
        val added = BrowserActionSnapshot(pendingUris = setOf(target.uri.toString()))
        val actions = registry.menuActions(target, added)
        check(actions.size == 1)
        check(actions[0].labelRes(target, added) == R.string.context_remove_from_pending)

        val empty = BrowserActionSnapshot()
        check(actions[0].labelRes(target, empty) == R.string.context_add_to_pending)
    }

    @Test
    fun `ZIP 编辑动作依赖目标快照`() {
        val registry = BrowserActions.Registry()
        com.kenny.localmanager.zipedit.ZipEditIntents.registerAll(registry)

        val zip = file("b.zip")
        val other = file("c.txt")
        val noTarget = BrowserActionSnapshot()
        check(registry.menuActions(other, noTarget).none { it.id == "zipedit.add.to.target" })

        val withTarget = BrowserActionSnapshot(zipEditTargetIsZip = true, zipEditTargetUri = zip.uri.toString())
        check(registry.menuActions(other, withTarget).any { it.id == "zipedit.add.to.target" })
        // 目标自身不可加入自身
        check(registry.menuActions(zip, withTarget).none { it.id == "zipedit.add.to.target" })
    }

    @Test
    fun `菜单动作序与迁移前 if 链一致`() {
        val registry = BrowserActions.Registry()
        com.kenny.localmanager.crypto.CryptoIntents.registerAll(registry)
        com.kenny.localmanager.config.ConfigIntents.registerAll(registry)
        com.kenny.localmanager.obfuscate.QuickObfuscateIntents.registerAll(registry)
        com.kenny.localmanager.archiveops.ArchiveOpsIntents.registerAll(registry)
        BrowserIntents.registerPendingToggle(registry)
        com.kenny.localmanager.zipedit.ZipEditIntents.registerAll(registry)

        val actions = registry.menuActions(file("a.md"), BrowserActionSnapshot())
        val ids = actions.map { it.id }
        // 文件菜单序（迁移前 if 链）：加密→密码保护→(直接编辑/导入不适用)→…→遮罩→压缩→待处理→设为目标→(添加到目标不适用)→(编辑内容不适用)
        check(ids == listOf(
            "crypto.gpg.encrypt", "crypto.pass.protect",
            "obfuscate.quick.mask", "archiveops.compress",
            "browser.pending.toggle", "zipedit.set.target",
        )) { "实得 $ids" }
    }

    @Test
    fun `压缩包阅读意图优先于解压意图（100 槽小于 400 槽）`() {
        val registry = BrowserActions.Registry()
        com.kenny.localmanager.archivereader.ArchiveReaderIntents.registerAll(registry)
        com.kenny.localmanager.archiveops.ArchiveOpsIntents.registerAll(registry)

        val match = registry.matchIntent(file("a.md.zip"))
        checkNotNull(match)
        check(match.id == "archivereader.mdzip") { "a.md.zip 应选压缩包阅读而非解压，实得 ${match.id}" }

        val plain = registry.matchIntent(file("b.zip"))
        checkNotNull(plain)
        check(plain.id == "archiveops.unzip") { "普通 zip 应选解压，实得 ${plain.id}" }
    }
}
