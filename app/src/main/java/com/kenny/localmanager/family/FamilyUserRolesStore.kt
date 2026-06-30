package com.kenny.localmanager.family

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

data class FamilyUserRole(
    val roleId: String,
    val password: String,
    val label: String,
) {
    fun displayLabel(): String = label.ifBlank { roleId }
}

class FamilyUserRolesStore(context: Context) {
    private val appContext = context.applicationContext
    private val configFile = File(appContext.filesDir, "family_network/user_roles.json")
    private val lock = ReentrantReadWriteLock()

    fun listRoles(): List<FamilyUserRole> = lock.read {
        readRolesLocked().sortedBy { it.roleId }
    }

    fun replaceRoles(roles: List<FamilyUserRole>): Result<Unit> = lock.write {
        writeRolesLocked(roles)
    }

    fun upsertRole(role: FamilyUserRole): Result<Unit> = lock.write {
        val roles = readRolesLocked().toMutableList()
        val index = roles.indexOfFirst { it.roleId == role.roleId }
        if (index >= 0) {
            roles[index] = role
        } else {
            roles += role
        }
        writeRolesLocked(roles)
    }

    fun deleteRole(roleId: String): Result<Unit> = lock.write {
        writeRolesLocked(readRolesLocked().filterNot { it.roleId == roleId })
    }

    fun migrateFromLegacyExtraRolesText(text: String?): Boolean = lock.write {
        if (configFile.exists()) return@write false
        val parsed = FamilyNetworkRoles.parseLegacyExtraRolesText(text)
        if (parsed.isEmpty()) return@write false
        writeRolesLocked(
            parsed.map { FamilyUserRole(it.roleId, it.password, it.label ?: it.roleId) }
        ).isSuccess
    }

    private fun writeRolesLocked(roles: List<FamilyUserRole>): Result<Unit> {
        val normalized = mutableListOf<FamilyUserRole>()
        val seenIds = mutableSetOf<String>()
        val seenPasswords = mutableSetOf<String>()
        for (role in roles) {
            val roleId = role.roleId.trim()
            val password = role.password.trim()
            val label = role.label.trim()
            if (roleId.isEmpty() || password.isEmpty()) {
                return Result.failure(IllegalArgumentException("role_id and password required"))
            }
            if (roleId == FamilyNetworkRoles.ADMIN_ROLE_ID) {
                return Result.failure(IllegalArgumentException("reserved role_id: admin"))
            }
            if (roleId in seenIds) {
                return Result.failure(IllegalArgumentException("duplicate role_id: $roleId"))
            }
            if (password in seenPasswords) {
                return Result.failure(IllegalArgumentException("duplicate password for role: $roleId"))
            }
            seenIds += roleId
            seenPasswords += password
            normalized += FamilyUserRole(roleId, password, label.ifEmpty { roleId })
        }
        configFile.parentFile?.mkdirs()
        val root = JSONObject().apply {
            put(
                "roles",
                JSONObject().apply {
                    normalized.forEach { role ->
                        put(
                            role.roleId,
                            JSONObject().apply {
                                put("password", role.password)
                                put("label", role.label)
                            }
                        )
                    }
                }
            )
        }
        configFile.writeText(root.toString())
        return Result.success(Unit)
    }

    private fun readRolesLocked(): List<FamilyUserRole> {
        if (!configFile.exists()) return emptyList()
        return runCatching {
            val root = JSONObject(configFile.readText())
            val rolesObj = root.optJSONObject("roles") ?: return emptyList()
            buildList {
                val keys = rolesObj.keys()
                while (keys.hasNext()) {
                    val roleId = keys.next()
                    val value = rolesObj.optJSONObject(roleId) ?: continue
                    val password = value.optString("password").trim()
                    if (password.isEmpty()) continue
                    val label = value.optString("label", roleId).trim().ifEmpty { roleId }
                    add(FamilyUserRole(roleId, password, label))
                }
            }
        }.getOrDefault(emptyList())
    }
}
