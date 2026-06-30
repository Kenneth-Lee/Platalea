package com.kenny.localmanager.family

object FamilyNetworkRoles {
    const val ADMIN_ROLE_ID = "admin"

    fun parseLegacyExtraRolesText(text: String?): List<FamilyNetworkRoleDefinition> {
        if (text.isNullOrBlank()) return emptyList()
        val result = mutableListOf<FamilyNetworkRoleDefinition>()
        val seenIds = mutableSetOf<String>()
        val seenPasswords = mutableSetOf<String>()
        for ((index, rawLine) in text.lineSequence().withIndex()) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            val parts = line.split(':', limit = 3)
            if (parts.size < 2) continue
            val roleId = parts[0].trim()
            val password = parts[1].trim()
            val label = parts.getOrNull(2)?.trim()?.ifEmpty { null }
            if (roleId.isEmpty() || password.isEmpty()) continue
            if (roleId == ADMIN_ROLE_ID) continue
            if (roleId in seenIds || password in seenPasswords) continue
            seenIds += roleId
            seenPasswords += password
            result += FamilyNetworkRoleDefinition(roleId, password, label)
        }
        return result
    }

    fun validateUserRolesAgainstAdmin(
        adminPassword: String?,
        userRoles: List<FamilyUserRole>,
    ): Result<Unit> {
        val admin = adminPassword?.trim()?.takeIf { it.isNotEmpty() } ?: return Result.success(Unit)
        val seenPasswords = mutableSetOf(admin)
        for (role in userRoles) {
            if (role.password in seenPasswords) {
                return Result.failure(
                    IllegalArgumentException("role ${role.roleId} password duplicates admin or another role")
                )
            }
            seenPasswords += role.password
        }
        return Result.success(Unit)
    }

    fun hasAnyPassword(
        adminPassword: String?,
        userRoles: List<FamilyUserRole>,
    ): Boolean {
        if (!adminPassword.isNullOrBlank()) return true
        return userRoles.any { it.password.isNotBlank() }
    }

    fun resolveAuth(
        providedPassword: String,
        adminPassword: String?,
        userRoles: List<FamilyUserRole>,
        adminLabel: String?,
    ): FamilyNetworkSessionAuth? {
        if (!adminPassword.isNullOrBlank() && providedPassword == adminPassword) {
            return FamilyNetworkSessionAuth(ADMIN_ROLE_ID, adminLabel, isAdmin = true)
        }
        for (role in userRoles) {
            if (providedPassword == role.password) {
                return FamilyNetworkSessionAuth(role.roleId, role.displayLabel(), isAdmin = false)
            }
        }
        return null
    }
}

data class FamilyNetworkRoleDefinition(
    val roleId: String,
    val password: String,
    val label: String?,
)
