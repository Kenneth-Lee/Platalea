package com.kenny.localmanager.file

import android.net.Uri
import android.provider.DocumentsContract

internal fun resolveParentDocumentUriShared(targetParentUri: Uri, treeUri: Uri?): Uri? {
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
