package com.kenny.localmanager.ui

import androidx.annotation.StringRes
import com.kenny.localmanager.R
import com.kenny.localmanager.file.DocumentFileModel

enum class DirectoryMode {
    NORMAL,
    PICK
}

enum class DirectoryPickPurpose(
    @StringRes val titleRes: Int,
    @StringRes val confirmLabelRes: Int
) {
    BULLETIN_ATTACHMENT(
        titleRes = R.string.directory_pick_purpose_bulletin_attachment,
        confirmLabelRes = R.string.directory_pick_confirm_bulletin_attachment
    ),
    BULLETIN_BOARDPACK(
        titleRes = R.string.directory_pick_purpose_generic,
        confirmLabelRes = R.string.directory_pick_confirm_generic
    ),
    GIT_SHARE(
        titleRes = R.string.directory_pick_purpose_git_share,
        confirmLabelRes = R.string.directory_pick_confirm_generic
    ),
    GENERIC(
        titleRes = R.string.directory_pick_purpose_generic,
        confirmLabelRes = R.string.directory_pick_confirm_generic
    )
}

data class DirectoryPickPolicy(
    val allowFiles: Boolean = true,
    /** 是否允许将目录作为拣选结果；为 false 时仍可单击目录进入浏览。 */
    val allowDirectories: Boolean = true,
    val multiSelect: Boolean = true,
    val maxItems: Int? = null,
    val allowedExtensions: Set<String>? = null
)

data class DirectoryPickResult(
    val requestId: String,
    val items: List<DocumentFileModel>,
    val purpose: DirectoryPickPurpose
)

fun DirectoryPickPurpose.defaultPolicy(): DirectoryPickPolicy = when (this) {
    DirectoryPickPurpose.BULLETIN_ATTACHMENT -> DirectoryPickPolicy(
        allowFiles = true,
        allowDirectories = true,
        multiSelect = true
    )
    DirectoryPickPurpose.BULLETIN_BOARDPACK -> DirectoryPickPolicy(
        allowFiles = true,
        allowDirectories = false,
        multiSelect = false,
        maxItems = 1,
        allowedExtensions = setOf("boardpack")
    )
    DirectoryPickPurpose.GIT_SHARE -> DirectoryPickPolicy(
        allowFiles = true,
        allowDirectories = false,
        multiSelect = true
    )
    DirectoryPickPurpose.GENERIC -> DirectoryPickPolicy()
}

fun DocumentFileModel.matchesDirectoryPickPolicy(policy: DirectoryPickPolicy): Boolean {
    if (isDirectory) return policy.allowDirectories
    if (!policy.allowFiles) return false
    val extensions = policy.allowedExtensions ?: return true
    val ext = name.substringAfterLast('.', "").lowercase()
    return ext in extensions
}

enum class PendingListMode {
    NORMAL,
    DIRECTORY_PICK
}
