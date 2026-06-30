package com.kenny.localmanager.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.kenny.localmanager.R
import com.kenny.localmanager.family.FamilyNetworkManager
import com.kenny.localmanager.family.FamilyUserRole

@Composable
fun FamilyUserRolesDialog(
    manager: FamilyNetworkManager,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val roles = remember { mutableStateListOf<FamilyUserRole>().apply { addAll(manager.listUserRoles()) } }
    var showEditor by remember { mutableStateOf(false) }
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var saveError by remember { mutableStateOf<String?>(null) }

    fun persist(updated: List<FamilyUserRole>) {
        manager.saveUserRoles(updated).fold(
            onSuccess = {
                roles.clear()
                roles.addAll(updated)
                saveError = null
            },
            onFailure = { error ->
                saveError = error.message ?: error.javaClass.simpleName
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.family_user_roles_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.family_user_roles_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (roles.isEmpty()) {
                    Text(
                        stringResource(R.string.family_user_roles_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(roles.size, key = { roles[it].roleId }) { index ->
                            val role = roles[index]
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(role.displayLabel(), style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        role.roleId,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = {
                                    editingIndex = index
                                    showEditor = true
                                }) {
                                    Text(stringResource(R.string.common_edit))
                                }
                                IconButton(onClick = { persist(roles.filterIndexed { i, _ -> i != index }) }) {
                                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.common_delete))
                                }
                            }
                        }
                    }
                }
                saveError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    editingIndex = null
                    showEditor = true
                }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text(stringResource(R.string.family_user_roles_add))
                }
                Button(onClick = onDismiss) { Text(stringResource(R.string.common_close)) }
            }
        }
    )

    if (showEditor) {
        val existing = editingIndex?.let { roles[it] }
        FamilyUserRoleEditorDialog(
            initial = existing,
            reservedIds = roles.mapIndexedNotNull { index, role ->
                if (index == editingIndex) null else role.roleId
            }.toSet(),
            onDismiss = { showEditor = false },
            onSave = { role ->
                val updated = roles.toMutableList()
                val index = editingIndex
                if (index == null) {
                    updated += role
                } else {
                    updated[index] = role
                }
                persist(updated)
                if (saveError == null) showEditor = false
            }
        )
    }
}

@Composable
private fun FamilyUserRoleEditorDialog(
    initial: FamilyUserRole?,
    reservedIds: Set<String>,
    onDismiss: () -> Unit,
    onSave: (FamilyUserRole) -> Unit
) {
    val context = LocalContext.current
    var roleId by remember { mutableStateOf(initial?.roleId.orEmpty()) }
    var label by remember { mutableStateOf(initial?.label.orEmpty()) }
    var password by remember { mutableStateOf(initial?.password.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (initial == null) {
                    stringResource(R.string.family_user_roles_add)
                } else {
                    stringResource(R.string.family_user_roles_edit)
                }
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = roleId,
                    onValueChange = { roleId = it.trim().lowercase().filter { ch -> ch.isLetterOrDigit() || ch == '_' || ch == '-' } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.family_user_roles_id_label)) },
                    singleLine = true,
                    enabled = initial == null
                )
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.family_user_roles_label_label)) },
                    singleLine = true
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.family_user_roles_password_label)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val trimmedId = roleId.trim()
                val trimmedPassword = password.trim()
                val trimmedLabel = label.trim()
                when {
                    trimmedId.isEmpty() || trimmedPassword.isEmpty() -> {
                        error = context.getString(R.string.family_user_roles_required)
                    }
                    trimmedId in reservedIds -> {
                        error = context.getString(R.string.family_user_roles_id_duplicate, trimmedId)
                    }
                    else -> onSave(FamilyUserRole(trimmedId, trimmedPassword, trimmedLabel.ifEmpty { trimmedId }))
                }
            }) { Text(stringResource(R.string.common_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}

@Composable
fun BoardRoleAccessSelector(
    availableRoles: List<FamilyUserRole>,
    selectedRoleIds: Set<String>,
    onSelectionChange: (Set<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(R.string.family_board_access_roles_title),
            style = MaterialTheme.typography.labelLarge
        )
        Text(
            stringResource(R.string.family_board_access_roles_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (availableRoles.isEmpty()) {
            Text(
                stringResource(R.string.family_board_access_roles_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            availableRoles.forEach { role ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = role.roleId in selectedRoleIds,
                        onCheckedChange = { checked ->
                            onSelectionChange(
                                if (checked) selectedRoleIds + role.roleId else selectedRoleIds - role.roleId
                            )
                        }
                    )
                    Column(modifier = Modifier.padding(start = 4.dp)) {
                        Text(role.displayLabel())
                        Text(
                            role.roleId,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
        Text(
            stringResource(R.string.family_board_access_admin_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

fun collectKnownRoleOptions(
    manager: FamilyNetworkManager,
    serviceIsSelf: Boolean,
    boards: List<com.kenny.localmanager.family.BulletinBoardInfo>
): List<FamilyUserRole> {
    if (serviceIsSelf) return manager.listUserRoles()
    val knownIds = boards.mapNotNull { it.roleIds }.flatten().toSet()
    return knownIds.map { roleId -> FamilyUserRole(roleId, "", roleId) }
}
