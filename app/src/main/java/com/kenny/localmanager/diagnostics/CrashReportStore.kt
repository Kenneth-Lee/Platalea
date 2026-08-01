package com.kenny.localmanager.diagnostics

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.kenny.localmanager.BuildConfig
import com.kenny.localmanager.data.Preferences
import com.kenny.localmanager.file.findChildByName
import com.kenny.localmanager.file.listFilesSafe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

data class CrashReportSnapshot(
    val fileName: String,
    val modifiedAt: Long,
    val preview: String,
    val content: String
)

object CrashReportStore {
    private const val CRASH_DIR_NAME = "崩溃记录"
    private const val CRASH_SUFFIX = ".crash"
    private const val PREFS = "crash_report_store"
    private const val KEY_LAST_RECORDED = "last_recorded"
    private const val KEY_LAST_SEEN = "last_seen"
    private val installed = AtomicBoolean(false)

    fun install(context: Context) {
        if (!installed.compareAndSet(false, true)) return
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                writeCrashReport(appContext, thread, throwable)
                appContext
                    .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_LAST_RECORDED, "written")
                    .apply()
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun hasUnseenCrash(context: Context): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val recorded = prefs.getString(KEY_LAST_RECORDED, null)
        val seen = prefs.getString(KEY_LAST_SEEN, null)
        return recorded == "written" && recorded != seen
    }

    fun markLatestSeen(context: Context) {
        val appContext = context.applicationContext
        appContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_SEEN, "written")
            .apply()
    }

    fun latestSummary(context: Context): String? {
        val snapshot = latestSnapshot(context) ?: return null
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val whenText = formatter.format(Date(snapshot.modifiedAt))
        return "$whenText · ${snapshot.fileName}"
    }

    fun latestSnapshot(context: Context): CrashReportSnapshot? {
        val rootUri = runBlocking { Preferences(context).rootUri.first() } ?: return null
        val rootDocumentUri = try {
            Uri.parse(rootUri)
        } catch (_: Exception) {
            return null
        }
        val parentUri = resolveParentDocumentUri(rootDocumentUri, rootDocumentUri) ?: return null
        val crashDirUri = findChildByName(context, parentUri, CRASH_DIR_NAME) ?: return null
        val crashDoc = DocumentFile.fromTreeUri(context, crashDirUri)
            ?: DocumentFile.fromSingleUri(context, crashDirUri)
            ?: return null
        val latestDoc = crashDoc.listFilesSafe()
            .filter { it.isFile && it.name?.startsWith("crash-") == true && it.name?.endsWith(CRASH_SUFFIX) == true }
            .maxByOrNull { it.lastModified() }
            ?: return null
        val text = runCatching {
            context.contentResolver.openInputStream(latestDoc.uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
        }.getOrNull() ?: return null
        val preview = text.lineSequence().take(8).joinToString("\n").trim()
        return CrashReportSnapshot(
            fileName = latestDoc.name ?: "crash-report",
            modifiedAt = latestDoc.lastModified(),
            preview = preview,
            content = text
        )
    }

    fun clearAll(context: Context) {
        val appContext = context.applicationContext
        appContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_LAST_RECORDED)
            .remove(KEY_LAST_SEEN)
            .apply()
    }

    fun buildShareIntent(context: Context): Intent? {
        val snapshot = latestSnapshot(context) ?: return null
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Platalea crash report: ${snapshot.fileName}")
            putExtra(Intent.EXTRA_TEXT, snapshot.content)
        }
    }

    private fun writeCrashReport(context: Context, thread: Thread, throwable: Throwable): File? {
        val now = Date()
        val nameTime = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(now)
        val readableTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSZ", Locale.US).format(now)
        val stack = stackTraceOf(throwable)
        val report = buildString {
            appendLine("time=$readableTime")
            appendLine("thread=${thread.name}(${thread.id})")
            appendLine("app_version=${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE})")
            appendLine("package=${BuildConfig.APPLICATION_ID}")
            appendLine("device=${Build.MANUFACTURER}/${Build.MODEL}")
            appendLine("android=${Build.VERSION.RELEASE} sdk=${Build.VERSION.SDK_INT}")
            appendLine("fingerprint=${Build.FINGERPRINT}")
            appendLine("--- stacktrace ---")
            appendLine(stack)
        }

        val rootUri = runBlocking { Preferences(context).rootUri.first() }
        val targetRoot = rootUri?.takeIf { it.isNotBlank() } ?: return null
        val rootDocumentUri = try {
            Uri.parse(targetRoot)
        } catch (_: Exception) {
            return null
        }
        val parentUri = resolveParentDocumentUri(rootDocumentUri, rootDocumentUri) ?: return null
        val cr = context.contentResolver
        val crashDirUri = createOrFindCrashDir(context, parentUri) ?: return null
        val fileName = "crash-$nameTime$CRASH_SUFFIX"
        val createdUri = try {
            DocumentsContract.createDocument(cr, crashDirUri, "text/plain", fileName)
        } catch (_: Exception) {
            null
        } ?: return null
        return try {
            cr.openOutputStream(createdUri)?.use { it.write(report.toByteArray(Charsets.UTF_8)) }
            File(fileName)
        } catch (_: Exception) {
            null
        }
    }

    private fun createOrFindCrashDir(context: Context, parentUri: Uri): Uri? {
        val cr = context.contentResolver
        val existing = findChildByName(context, parentUri, CRASH_DIR_NAME)
        if (existing != null) return existing
        return try {
            DocumentsContract.createDocument(cr, parentUri, DocumentsContract.Document.MIME_TYPE_DIR, CRASH_DIR_NAME)
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveParentDocumentUri(targetParentUri: Uri, treeUri: Uri?): Uri? {
        val s = targetParentUri.toString()
        return when {
            s.contains("/tree/") && s.contains("/document/") -> try {
                val treeUriParsed = Uri.parse(s.substringBefore("/document/"))
                val docId = DocumentsContract.getDocumentId(targetParentUri)
                DocumentsContract.buildDocumentUriUsingTree(treeUriParsed, docId)
            } catch (_: Exception) {
                targetParentUri
            }
            DocumentsContract.isTreeUri(targetParentUri) -> try {
                DocumentsContract.buildDocumentUriUsingTree(
                    targetParentUri,
                    DocumentsContract.getTreeDocumentId(targetParentUri)
                )
            } catch (_: Exception) {
                targetParentUri
            }
            treeUri != null -> try {
                val docId = DocumentsContract.getDocumentId(targetParentUri)
                DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
            } catch (_: Exception) {
                targetParentUri
            }
            else -> targetParentUri
        }
    }

    private fun stackTraceOf(throwable: Throwable): String {
        val writer = StringWriter()
        PrintWriter(writer).use { printer ->
            throwable.printStackTrace(printer)
        }
        return writer.toString()
    }

    private fun reportsDir(context: Context): File {
        return File(context.cacheDir, CRASH_DIR_NAME).apply { mkdirs() }
    }

    private fun latestReportFile(context: Context): File? {
        return reportsDir(context)
            .listFiles()
            ?.filter { it.isFile && it.name.startsWith("crash-") && it.name.endsWith(CRASH_SUFFIX) }
            ?.maxByOrNull { it.lastModified() }
    }

    private fun pruneOldReports(context: Context, keep: Int = 20) {
        val files = reportsDir(context)
            .listFiles()
            ?.filter { it.isFile && it.name.startsWith("crash-") && it.name.endsWith(CRASH_SUFFIX) }
            ?.sortedByDescending { it.lastModified() }
            ?: return
        files.drop(keep).forEach { file ->
            runCatching { file.delete() }
        }
    }
}
