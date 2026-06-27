package com.kenny.localmanager.family

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Terminal
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

enum class ComposeInputTargetKind {
    AGENT,
    PARTICIPANT,
    COMMAND
}

data class ComposeInputTarget(
    val label: String,
    val kind: ComposeInputTargetKind,
    val description: String? = null
) {
    val insertText: String
        get() = when (kind) {
            ComposeInputTargetKind.COMMAND -> label
            else -> "@$label"
        }

    fun matches(query: String): Boolean {
        if (query.isEmpty()) return true
        return when (kind) {
            ComposeInputTargetKind.COMMAND -> {
                label.contains(query, ignoreCase = true) ||
                    label.removePrefix("/").contains(query, ignoreCase = true)
            }
            else -> label.contains(query, ignoreCase = true)
        }
    }
}

/** @deprecated 使用 [ComposeInputTarget] */
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

enum class ActiveInputTrigger {
    MENTION,
    COMMAND
}

data class ActiveInputQuery(
    val trigger: ActiveInputTrigger,
    val startIndex: Int,
    val query: String
)

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

    fun findActiveInputQuery(text: String, cursor: Int = text.length): ActiveInputQuery? {
        val safeCursor = cursor.coerceIn(0, text.length)
        val before = text.substring(0, safeCursor)
        val mention = findActiveMentionQuery(text, safeCursor)
        val command = findActiveCommandQuery(before)
        return when {
            mention != null && command != null -> {
                if (mention.first >= command.first) {
                    ActiveInputQuery(ActiveInputTrigger.MENTION, mention.first, mention.second)
                } else {
                    ActiveInputQuery(ActiveInputTrigger.COMMAND, command.first, command.second)
                }
            }
            mention != null -> ActiveInputQuery(ActiveInputTrigger.MENTION, mention.first, mention.second)
            command != null -> ActiveInputQuery(ActiveInputTrigger.COMMAND, command.first, command.second)
            else -> null
        }
    }

    fun buildTargets(agents: List<String>, participants: List<String>): List<MentionTarget> {
        val agentTargets = agents.map { MentionTarget(it, isAgent = true) }
        val participantTargets = participants
            .filter { name -> agentTargets.none { it.label.equals(name, ignoreCase = true) } }
            .map { MentionTarget(it, isAgent = false) }
        return agentTargets + participantTargets
    }

    fun buildComposeTargets(
        agents: List<String>,
        participants: List<String>,
        commands: List<String>,
        commandDescriptions: Map<String, String> = emptyMap()
    ): List<ComposeInputTarget> {
        val mentionTargets = buildTargets(agents, participants).map { target ->
            ComposeInputTarget(
                label = target.label,
                kind = if (target.isAgent) ComposeInputTargetKind.AGENT else ComposeInputTargetKind.PARTICIPANT
            )
        }
        val commandTargets = commands.map { command ->
            ComposeInputTarget(
                label = command,
                kind = ComposeInputTargetKind.COMMAND,
                description = commandDescriptions[command]
            )
        }
        return mentionTargets + commandTargets
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

    private fun findActiveCommandQuery(before: String): Pair<Int, String>? {
        var index = before.length - 1
        while (index >= 0) {
            if (before[index] == '/') {
                val atWordStart = index == 0 || before[index - 1].isWhitespace()
                if (atWordStart) {
                    val query = before.substring(index + 1)
                    if (query.contains('\n')) return null
                    return index to query
                }
            }
            index--
        }
        return null
    }

    fun applyTarget(
        value: TextFieldValue,
        startIndex: Int,
        target: ComposeInputTarget
    ): TextFieldValue {
        val cursor = value.selection.end.coerceIn(0, value.text.length)
        val prefix = value.text.substring(0, startIndex)
        val suffix = value.text.substring(cursor)
        val insertion = when (target.kind) {
            ComposeInputTargetKind.COMMAND -> target.insertText.trimEnd() + " "
            else -> target.insertText + " "
        }
        val newText = prefix + insertion + suffix
        val newCursor = (prefix + insertion).length
        return TextFieldValue(newText, TextRange(newCursor))
    }

    fun applyMention(
        value: TextFieldValue,
        atIndex: Int,
        mentionLabel: String
    ): TextFieldValue = applyTarget(
        value,
        atIndex,
        ComposeInputTarget(mentionLabel, ComposeInputTargetKind.AGENT)
    )
}

@Composable
fun BulletinMentionTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    composeTargets: List<ComposeInputTarget>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    maxLines: Int = 3,
    placeholder: @Composable (() -> Unit)? = null
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val activeQuery = remember(value.text, value.selection.end) {
        BulletinBoardMention.findActiveInputQuery(value.text, value.selection.end)
    }
    val filteredTargets = remember(activeQuery, composeTargets) {
        if (activeQuery == null) {
            emptyList()
        } else {
            val query = activeQuery.query
            composeTargets.filter { target ->
                when (activeQuery.trigger) {
                    ActiveInputTrigger.MENTION ->
                        target.kind != ComposeInputTargetKind.COMMAND && target.matches(query)
                    ActiveInputTrigger.COMMAND ->
                        target.kind == ComposeInputTargetKind.COMMAND && target.matches(query)
                }
            }
        }
    }

    Box(modifier = modifier) {
        TextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                menuExpanded = BulletinBoardMention.findActiveInputQuery(it.text, it.selection.end) != null
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
            expanded = menuExpanded && activeQuery != null && filteredTargets.isNotEmpty(),
            onDismissRequest = { menuExpanded = false }
        ) {
            filteredTargets.forEach { target ->
                DropdownMenuItem(
                    text = {
                        Row {
                            when (target.kind) {
                                ComposeInputTargetKind.COMMAND -> {
                                    Icon(
                                        Icons.Default.Terminal,
                                        contentDescription = null,
                                        modifier = Modifier.width(18.dp),
                                        tint = MaterialTheme.colorScheme.tertiary
                                    )
                                }
                                ComposeInputTargetKind.AGENT -> {
                                    Icon(
                                        Icons.Default.SmartToy,
                                        contentDescription = null,
                                        modifier = Modifier.width(18.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                ComposeInputTargetKind.PARTICIPANT -> Unit
                            }
                            if (target.kind != ComposeInputTargetKind.PARTICIPANT) {
                                Spacer(Modifier.width(6.dp))
                            }
                            Column {
                                Text(
                                    target.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1
                                )
                                target.description?.takeIf { it.isNotBlank() }?.let { description ->
                                    Text(
                                        description,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2
                                    )
                                }
                            }
                        }
                    },
                    onClick = {
                        val startIndex = activeQuery?.startIndex ?: return@DropdownMenuItem
                        onValueChange(BulletinBoardMention.applyTarget(value, startIndex, target))
                        menuExpanded = false
                    }
                )
            }
        }
    }
}
