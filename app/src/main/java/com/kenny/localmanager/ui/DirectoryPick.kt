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
