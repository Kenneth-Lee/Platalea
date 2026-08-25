package com.kenny.localmanager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.kenny.localmanager.R

data class FamilyAvatarPattern(
    val id: String,
    val iconResId: Int
)

data class FamilyAvatarColor(
    val id: String,
    val color: Color
)

data class FamilyAvatarEntry(
    val id: String,
    val patternId: String,
    val colorId: String
)

object FamilyAvatarCatalog {
    private const val SEPARATOR = "@"

    val patterns: List<FamilyAvatarPattern> = listOf(
        FamilyAvatarPattern("person", R.drawable.family_avatar_pattern_person),
        FamilyAvatarPattern("animal", R.drawable.family_avatar_pattern_paw),
        FamilyAvatarPattern("plant", R.drawable.family_avatar_pattern_leaf),
        FamilyAvatarPattern("flower", R.drawable.family_avatar_pattern_flower),
        FamilyAvatarPattern("fish", R.drawable.family_avatar_pattern_fish),
        FamilyAvatarPattern("object", R.drawable.family_avatar_pattern_box),
        FamilyAvatarPattern("star", R.drawable.family_avatar_pattern_star),
        FamilyAvatarPattern("bolt", R.drawable.family_avatar_pattern_bolt)
    )

    val colors: List<FamilyAvatarColor> = listOf(
        FamilyAvatarColor("coral", Color(0xFFE57373)),
        FamilyAvatarColor("rose", Color(0xFFF06292)),
        FamilyAvatarColor("violet", Color(0xFFBA68C8)),
        FamilyAvatarColor("indigo", Color(0xFF7986CB)),
        FamilyAvatarColor("blue", Color(0xFF64B5F6)),
        FamilyAvatarColor("teal", Color(0xFF4DB6AC)),
        FamilyAvatarColor("green", Color(0xFF81C784)),
        FamilyAvatarColor("amber", Color(0xFFFFB74D))
    )

    val entries: List<FamilyAvatarEntry> = buildList {
        patterns.forEach { pattern ->
            colors.forEach { color ->
                add(
                    FamilyAvatarEntry(
                        id = "${pattern.id}$SEPARATOR${color.id}",
                        patternId = pattern.id,
                        colorId = color.id
                    )
                )
            }
        }
    }

    private val byId: Map<String, FamilyAvatarEntry> = entries.associateBy { it.id }
    private val patternsById: Map<String, FamilyAvatarPattern> = patterns.associateBy { it.id }
    private val colorsById: Map<String, FamilyAvatarColor> = colors.associateBy { it.id }

    val defaultId: String = entries.first().id

    private fun normalizeLegacyAvatarId(raw: String): String? {
        val value = raw.trim()
        if (!value.startsWith("avatar_", ignoreCase = true)) return null
        val index = value.removePrefix("avatar_").toIntOrNull() ?: return null
        if (index <= 0) return null
        val color = colors[(index - 1) % colors.size]
        return "person$SEPARATOR${color.id}"
    }

    fun normalizeAvatarId(raw: String?): String? {
        val value = raw?.trim()?.ifEmpty { null } ?: return null
        if (byId.containsKey(value)) return value
        val legacy = normalizeLegacyAvatarId(value)
        return legacy?.takeIf { byId.containsKey(it) }
    }

    fun resolveEntry(raw: String?): FamilyAvatarEntry {
        val normalized = normalizeAvatarId(raw)
        return if (normalized == null) entries.first() else byId[normalized] ?: entries.first()
    }

    fun resolvePattern(entry: FamilyAvatarEntry): FamilyAvatarPattern =
        patternsById[entry.patternId] ?: patterns.first()

    fun resolveColor(entry: FamilyAvatarEntry): FamilyAvatarColor =
        colorsById[entry.colorId] ?: colors.first()
}

@Composable
fun FamilyAvatarImage(
    avatarId: String?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    val entry = FamilyAvatarCatalog.resolveEntry(avatarId)
    val pattern = FamilyAvatarCatalog.resolvePattern(entry)
    val color = FamilyAvatarCatalog.resolveColor(entry)
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(color.color),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = ImageVector.vectorResource(id = pattern.iconResId),
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.fillMaxSize(0.62f)
        )
    }
}
