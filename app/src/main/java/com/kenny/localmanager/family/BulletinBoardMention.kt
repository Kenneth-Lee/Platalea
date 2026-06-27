package com.kenny.localmanager.family

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

data class MentionTarget(
    val label: String,
    val isAgent: Boolean
) {
    val insertText: String
        get() = "@$label"

    fun matches(query: String): Boolean {
        if (query.isEmpty()) return true
        return label.contains(query, ignoreCase = true)
    }
}

object BulletinBoardMention {
    private const val AGENT_DEVICE_PREFIX = "agent:"

    fun collectParticipants(messages: List<BulletinMessage>): List<String> {
        val seen = linkedSetOf<String>()
        messages.forEach { message ->
            if (!message.isConversationMessage) return@forEach
            val device = message.authorDevice?.trim().orEmpty()
            if (device.startsWith(AGENT_DEVICE_PREFIX)) return@forEach
            val label = message.authorLabel.trim()
            if (label.isNotEmpty()) {
                seen.add(label)
            }
        }
        return seen.sortedBy { it.lowercase() }
    }

    fun buildTargets(agents: List<String>, participants: List<String>): List<MentionTarget> {
        val agentTargets = agents.map { MentionTarget(it, isAgent = true) }
        val participantTargets = participants
            .filter { name -> agentTargets.none { it.label.equals(name, ignoreCase = true) } }
            .map { MentionTarget(it, isAgent = false) }
        return agentTargets + participantTargets
    }

    fun findActiveMentionQuery(text: String, cursor: Int = text.length): Pair<Int, String>? {
        val safeCursor = cursor.coerceIn(0, text.length)
        val before = text.substring(0, safeCursor)
        val atIndex = before.lastIndexOf('@')
        if (atIndex < 0) return null
        val query = before.substring(atIndex + 1)
        if (query.contains(' ') || query.contains('\n') || query.contains('\t')) {
            return null
        }
        return atIndex to query
    }

    fun applyMention(
        value: TextFieldValue,
        atIndex: Int,
        mentionLabel: String
    ): TextFieldValue {
        val cursor = value.selection.end.coerceIn(0, value.text.length)
        val prefix = value.text.substring(0, atIndex)
        val suffix = value.text.substring(cursor)
        val insertion = "@$mentionLabel "
        val newText = prefix + insertion + suffix
        val newCursor = (prefix + insertion).length
        return TextFieldValue(newText, TextRange(newCursor))
    }
}

@Composable
fun BulletinMentionTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    mentionTargets: List<MentionTarget>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    maxLines: Int = 3,
    placeholder: @Composable (() -> Unit)? = null
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val mentionQuery = remember(value.text, value.selection.end) {
        BulletinBoardMention.findActiveMentionQuery(value.text, value.selection.end)
    }
    val filteredTargets = remember(mentionQuery, mentionTargets) {
        val query = mentionQuery?.second.orEmpty()
        mentionTargets.filter { it.matches(query) }
    }

    Box(modifier = modifier) {
        TextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                menuExpanded = BulletinBoardMention.findActiveMentionQuery(it.text, it.selection.end) != null
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            placeholder = placeholder,
            singleLine = false,
            maxLines = maxLines,
            textStyle = MaterialTheme.typography.bodySmall,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface
            )
        )
        DropdownMenu(
            expanded = menuExpanded && mentionQuery != null && filteredTargets.isNotEmpty(),
            onDismissRequest = { menuExpanded = false }
        ) {
            filteredTargets.forEach { target ->
                DropdownMenuItem(
                    text = {
                        Row {
                            if (target.isAgent) {
                                Icon(
                                    Icons.Default.SmartToy,
                                    contentDescription = null,
                                    modifier = Modifier.width(18.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(
                                target.label,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1
                            )
                        }
                    },
                    onClick = {
                        val atIndex = mentionQuery?.first ?: return@DropdownMenuItem
                        onValueChange(BulletinBoardMention.applyMention(value, atIndex, target.label))
                        menuExpanded = false
                    }
                )
            }
        }
    }
}
