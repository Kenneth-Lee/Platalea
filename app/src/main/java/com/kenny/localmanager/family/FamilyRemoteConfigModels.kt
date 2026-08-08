package com.kenny.localmanager.family

import org.json.JSONObject

data class FamilyRemoteRoleConfig(
    val roleId: String,
    val label: String?,
    val canCreateBoards: Boolean,
    val canManageBoards: Boolean,
    val allowedAgentModels: List<String>?,
    val passwordSet: Boolean,
)

data class FamilyRemoteConfigSnapshot(
    val revision: Long,
    val updatedAt: Long,
    val roles: List<FamilyRemoteRoleConfig>,
)

data class FamilyRemoteConfigPatchResult(
    val revision: Long,
    val updatedAt: Long,
    val changedDomains: List<String>,
    val changedPaths: List<String>,
    val snapshot: FamilyRemoteConfigSnapshot?,
)

data class FamilyRemoteConfigChangeEvent(
    val changed: Boolean,
    val revision: Long,
    val updatedAt: Long,
    val changedDomains: List<String>,
    val changedPaths: List<String>,
)

class FamilyRemoteConfigConflictException(
    message: String,
    val serverRevision: Long,
    val changedPaths: List<String>,
    val serverSnapshot: FamilyRemoteConfigSnapshot?,
) : IllegalStateException(message)

object FamilyRemoteConfigParser {
    fun parseSnapshot(json: JSONObject): FamilyRemoteConfigSnapshot {
        val auth = json.optJSONObject("domains")?.optJSONObject("auth")
        val rolesObj = auth?.optJSONObject("roles")
        val roles = buildList {
            if (rolesObj != null) {
                val keys = rolesObj.keys()
                while (keys.hasNext()) {
                    val roleId = keys.next().trim()
                    if (roleId.isEmpty()) continue
                    val roleJson = rolesObj.optJSONObject(roleId) ?: JSONObject()
                    add(
                        FamilyRemoteRoleConfig(
                            roleId = roleId,
                            label = roleJson.optString("label").trim().takeIf { it.isNotEmpty() },
                            canCreateBoards = roleJson.optBoolean("can_create_boards", false),
                            canManageBoards = roleJson.optBoolean("can_manage_boards", false),
                            allowedAgentModels = when {
                                !roleJson.has("allowed_agent_models") -> null
                                roleJson.isNull("allowed_agent_models") -> null
                                else -> roleJson.optJSONArray("allowed_agent_models")?.let { arr ->
                                    buildList {
                                        for (i in 0 until arr.length()) {
                                            arr.optString(i).trim().takeIf { it.isNotEmpty() }?.let { add(it) }
                                        }
                                    }.distinct()
                                } ?: emptyList()
                            },
                            passwordSet = roleJson.optBoolean("password_set", false)
                        )
                    )
                }
            }
        }.sortedBy { it.roleId }
        return FamilyRemoteConfigSnapshot(
            revision = json.optLong("revision"),
            updatedAt = json.optLong("updated_at"),
            roles = roles
        )
    }

    fun parsePatchResult(json: JSONObject): FamilyRemoteConfigPatchResult {
        return FamilyRemoteConfigPatchResult(
            revision = json.optLong("revision"),
            updatedAt = json.optLong("updated_at"),
            changedDomains = parseStringArray(json.optJSONArray("changed_domains")),
            changedPaths = parseStringArray(json.optJSONArray("changed_paths")),
            snapshot = json.optJSONObject("snapshot")?.let { parseSnapshot(it) }
        )
    }

    fun parseChangeEvent(json: JSONObject): FamilyRemoteConfigChangeEvent {
        return FamilyRemoteConfigChangeEvent(
            changed = json.optBoolean("changed", false),
            revision = json.optLong("revision"),
            updatedAt = json.optLong("updated_at"),
            changedDomains = parseStringArray(json.optJSONArray("changed_domains")),
            changedPaths = parseStringArray(json.optJSONArray("changed_paths"))
        )
    }

    private fun parseStringArray(array: org.json.JSONArray?): List<String> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val value = array.optString(i).trim()
                if (value.isNotEmpty()) add(value)
            }
        }
    }
}
