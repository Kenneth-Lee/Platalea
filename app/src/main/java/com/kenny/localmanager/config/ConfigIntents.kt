package com.kenny.localmanager.config

import android.widget.Toast
import com.kenny.localmanager.R
import com.kenny.localmanager.browser.BrowserActionContext
import com.kenny.localmanager.browser.BrowserActionSnapshot
import com.kenny.localmanager.browser.BrowserActions
import com.kenny.localmanager.browser.BrowserFileAction
import com.kenny.localmanager.dict.importStarDict
import com.kenny.localmanager.dict.isStarDictImportCandidate
import com.kenny.localmanager.file.DocumentFileModel
import com.kenny.localmanager.browser.isConfigImportFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 配置域的浏览器动作声明（拆解设计 第三批）：导入应用配置（.json）
 * 与导入 StarDict 词典（.zip/.ifo 候选）。前者直呼本模块 service；
 * 后者是 IO 导入 + 结果提示的编排，经上下文 scope 与 appContext 执行。
 */
internal object ConfigIntents {

    private object ImportConfigAction : BrowserFileAction {
        override val id = "config.import"
        override fun labelRes(target: DocumentFileModel, snapshot: BrowserActionSnapshot) =
            R.string.context_import_config
        override fun applies(target: DocumentFileModel, snapshot: BrowserActionSnapshot) =
            !target.isDirectory && isConfigImportFile(target.name)
        override fun run(ctx: BrowserActionContext) {
            ConfigImportExportModule.service.startImportFromUri(ctx.target.uri)
        }
    }

    private object ImportStarDictAction : BrowserFileAction {
        override val id = "config.import.stardict"
        override fun labelRes(target: DocumentFileModel, snapshot: BrowserActionSnapshot) =
            R.string.context_import_stardict
        override fun applies(target: DocumentFileModel, snapshot: BrowserActionSnapshot) =
            !target.isDirectory && isStarDictImportCandidate(target.name)
        override fun run(ctx: BrowserActionContext) {
            val appContext = ctx.appContext
            ctx.scope.launch {
                val result = withContext(Dispatchers.IO) {
                    importStarDict(appContext, ctx.target.uri, ctx.target.name)
                }
                result.onSuccess {
                    val message = if (it.evicted != null) {
                        appContext.getString(R.string.browser_dict_imported_evicted, it.imported.name, it.imported.wordCount, it.evicted.name)
                    } else {
                        appContext.getString(R.string.browser_dict_imported, it.imported.name, it.imported.wordCount)
                    }
                    Toast.makeText(appContext, message, Toast.LENGTH_LONG).show()
                }.onFailure {
                    Toast.makeText(appContext, appContext.getString(R.string.browser_dict_import_failed, it.message ?: appContext.getString(R.string.common_unknown_error)), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun registerAll(registry: BrowserActions.Registry) {
        registry.register(ImportConfigAction)
        registry.register(ImportStarDictAction)
    }
}
