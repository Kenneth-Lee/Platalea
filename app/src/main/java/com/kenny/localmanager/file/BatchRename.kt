package com.kenny.localmanager.file

// ============================================================================
// 批量重命名 —— 纯逻辑（正则捕获组模板展开与预览计算）
//
// 输入：用户提供的正则模式（匹配文件名的部分）与替换模板（\0 为整个
// 匹配段，\1 至 \9 为捕获组，\\ 为字面反斜杠）。
// 输出：逐文件预览（原名 -> 新名 / 跳过 / 错误），由 UI 呈现并经用户
// 确认后执行。改名动作用 SAF renameDocument，不在本文件。
// ============================================================================

/**
 * 编译结果：非法正则或模板引用了不存在的捕获组时给出可展示的原因。
 *
 * 隐式语义（渐进式预览）：空正则 = 匹配整个文件名；空模板 = 保留匹配段
 * （等效 \0）。用户什么都不输时预览即完整文件名列表，只输模板即可在
 * 原名前后加内容（xxx\0yyy），只输正则则文件名变为匹配到的段。
 */
internal sealed interface BatchRenameCompileResult {
    data class Ok(val plan: BatchRenamePlan) : BatchRenameCompileResult
    data class InvalidPattern(val message: String) : BatchRenameCompileResult
    data class BadGroupReference(val group: Int, val available: Int) : BatchRenameCompileResult
}

/** 单文件预览错误（阻止整批提交的问题；跳过类不算错误）。 */
internal enum class BatchRenameItemError {
    /** 改名后为空串。 */
    EMPTY,

    /** 名字包含 /（SAF 文档名不允许）。 */
    SLASH,

    /** 改名后与本批其他文件（改名后名字）重名。 */
    CONFLICT
}

/**
 * 单文件预览项：newName 为 null 且 matched 为 true 表示匹配但无变化
 * （如空模板保留整名）；matched 为 false 表示不匹配（跳过）。
 * matchRanges 为原名中被正则命中的区间，groupRanges 为其中各捕获组
 * 的区间（预览高亮用；不匹配为空）。
 */
internal data class BatchRenamePreviewItem(
    val original: String,
    val newName: String?,
    val matched: Boolean,
    val error: BatchRenameItemError?,
    val matchRanges: List<IntRange> = emptyList(),
    val groupRanges: List<IntRange> = emptyList()
)

/**
 * 编译后的重命名规则。逐匹配替换（replaceAll 语义）：文件名中每一处
 * 匹配都按模板展开替换，\n 引用该次匹配的捕获组；空模式/空模板的隐式
 * 语义见 [BatchRenameCompileResult]。
 */
internal class BatchRenamePlan private constructor(
    private val regex: Regex,
    private val template: String
) {
    /** 是否匹配（不展开模板）。 */
    fun matches(name: String): Boolean = regex.containsMatchIn(name)

    /** 对单个文件名计算改名结果（seq 为本文件的顺序号）；不匹配返回 null。 */
    fun applyTo(name: String, seq: Int = 0, random: kotlin.random.Random = kotlin.random.Random.Default): String? {
        if (!regex.containsMatchIn(name)) return null
        return regex.replace(name) { m -> expandRenameTemplate(m, template, seq, random) }
    }

    /** 原名中被命中的区间（含头含尾），供预览高亮。 */
    fun matchRangesIn(name: String): List<IntRange> =
        regex.findAll(name).map { it.range }.toList()

    /** 各次命中中捕获组（组 1 起）的区间，供预览按组标色。 */
    fun groupRangesIn(name: String): List<IntRange> =
        regex.findAll(name).flatMap { m ->
            (1 until m.groupValues.size).mapNotNull { i -> m.groups[i]?.range }
        }.toList()

    companion object {
        /**
         * 编译模式与模板。空正则按匹配整个文件名处理，空模板按保留匹配段
         * （\0）处理；非法正则与越界组引用报可展示原因。
         */
        fun compile(pattern: String, template: String): BatchRenameCompileResult {
            val regex = try {
                if (pattern.isEmpty()) Regex(".*") else Regex(pattern)
            } catch (e: Exception) {
                return BatchRenameCompileResult.InvalidPattern(e.message ?: "")
            }
            val effectiveTemplate = template.ifEmpty { "\\0" }
            val groupCount = regex.toPattern().matcher("").groupCount()
            val maxRef = scanTemplateGroupReferences(effectiveTemplate)
            if (maxRef != null && maxRef > groupCount) {
                return BatchRenameCompileResult.BadGroupReference(maxRef, groupCount)
            }
            return BatchRenameCompileResult.Ok(BatchRenamePlan(regex, effectiveTemplate))
        }
    }
}

/** 扫描模板中的 \n 引用（忽略 \\ 转义），返回最大组号；无引用返回 null。 */
private fun scanTemplateGroupReferences(template: String): Int? {
    var max = -1
    var i = 0
    while (i < template.length) {
        if (template[i] == '\\' && i + 1 < template.length) {
            val n = template[i + 1]
            if (n == '\\') {
                i += 2
                continue
            }
            if (n in '0'..'9') {
                max = maxOf(max, n - '0')
                i += 2
                continue
            }
        }
        i++
    }
    return if (max >= 0) max else null
}

/** 随机字符池：字母与数字（文件名安全字符）。 */
internal const val BATCH_RENAME_RANDOM_CHARS =
    "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

/**
 * 展开模板：\0 至 \9 引用组（越界防御性取空串），\\ 为字面反斜杠，
 * 连续 # 为顺序号（个数为位数，不足补零，超出自然扩位；同一文件名中
 * 的各处匹配与各处 # 共用同一个号）；连续 & 为随机字符（个数为字符
 * 数，取自字母与数字；各处独立随机）。
 */
internal fun expandRenameTemplate(
    match: MatchResult,
    template: String,
    seq: Int,
    random: kotlin.random.Random = kotlin.random.Random.Default
): String {
    val sb = StringBuilder()
    var i = 0
    while (i < template.length) {
        val c = template[i]
        if (c == '\\' && i + 1 < template.length) {
            val n = template[i + 1]
            if (n == '\\') {
                sb.append('\\')
                i += 2
                continue
            }
            if (n in '0'..'9') {
                sb.append(match.groupValues.getOrNull(n - '0') ?: "")
                i += 2
                continue
            }
        }
        if (c == '#') {
            var k = 0
            while (i + k < template.length && template[i + k] == '#') k++
            sb.append(seq.toString().padStart(k, '0'))
            i += k
            continue
        }
        if (c == '&') {
            var k = 0
            while (i + k < template.length && template[i + k] == '&') k++
            repeat(k) { sb.append(BATCH_RENAME_RANDOM_CHARS[random.nextInt(BATCH_RENAME_RANDOM_CHARS.length)]) }
            i += k
            continue
        }
        sb.append(c)
        i++
    }
    return sb.toString()
}

/**
 * 构建整批预览：逐项计算新名并做批内冲突检测（改名后名字互撞，或与
 * 未参与改名的原名相撞，都标 CONFLICT）。跳过项（不匹配 / 新旧同名）
 * 不参与冲突检测的目标集合——它们保持原名，只有被撞时才标。
 */
internal fun buildBatchRenamePreview(
    names: List<String>,
    plan: BatchRenamePlan,
    random: kotlin.random.Random = kotlin.random.Random.Default
): List<BatchRenamePreviewItem> {
    // 顺序号按预览顺序对匹配项从 1 递增（不匹配不占号）；同一文件的
    // 全部匹配与 # 共用该号。
    var seq = 0
    val items = names.map { original ->
        val applied = if (plan.matches(original)) plan.applyTo(original, ++seq, random) else null
        val matched = applied != null
        val newName = applied?.takeIf { it != original }
        val error = when {
            newName == null -> null
            newName.isEmpty() -> BatchRenameItemError.EMPTY
            newName.contains('/') -> BatchRenameItemError.SLASH
            else -> null
        }
        BatchRenamePreviewItem(
            original, newName, matched, error,
            if (matched) plan.matchRangesIn(original) else emptyList(),
            if (matched) plan.groupRangesIn(original) else emptyList()
        )
    }
    // 冲突检测：改名后的目标名集合中计数 > 1 → 相关项标 CONFLICT；
    // 目标名若撞上其他保持原名的文件（跳过项 / 未改名成功项）同样算撞。
    val targetCounts = HashMap<String, Int>()
    items.filter { it.newName != null && it.error == null }.forEach {
        targetCounts[it.newName!!] = (targetCounts[it.newName!!] ?: 0) + 1
    }
    val keptOriginals = items.filter { it.newName == null }.map { it.original }.toHashSet()
    return items.map { item ->
        if (item.newName != null && item.error == null) {
            val clashes = (targetCounts[item.newName] ?: 0) > 1 || item.newName in keptOriginals
            if (clashes) item.copy(error = BatchRenameItemError.CONFLICT) else item
        } else item
    }
}
