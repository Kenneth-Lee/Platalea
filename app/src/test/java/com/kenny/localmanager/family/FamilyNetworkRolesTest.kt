package com.kenny.localmanager.family

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FamilyNetworkRolesTest {
    @Test
    fun parseLegacyExtraRolesText_ignoresComments() {
        val roles = FamilyNetworkRoles.parseLegacyExtraRolesText(
            """
            family:secret:家人
            # comment
            friend:pass2:朋友
            """.trimIndent()
        )
        assertEquals(2, roles.size)
    }

    @Test
    fun resolveAuth_matchesAdminPassword() {
        val auth = FamilyNetworkRoles.resolveAuth(
            providedPassword = "admin-pass",
            adminPassword = "admin-pass",
            userRoles = listOf(FamilyUserRole("friend", "friend-pass", "朋友")),
            adminLabel = "管理员"
        )
        assertNotNull(auth)
        assertTrue(auth!!.isAdmin)
    }

    @Test
    fun resolveAuth_matchesUserRole() {
        val auth = FamilyNetworkRoles.resolveAuth(
            providedPassword = "friend-pass",
            adminPassword = "admin",
            userRoles = listOf(FamilyUserRole("friend", "friend-pass", "朋友")),
            adminLabel = null
        )
        assertNotNull(auth)
        assertEquals("friend", auth!!.roleId)
        assertEquals(false, auth.isAdmin)
    }

    @Test
    fun validateUserRolesAgainstAdmin_rejectsDuplicatePassword() {
        val result = FamilyNetworkRoles.validateUserRolesAgainstAdmin(
            adminPassword = "same",
            userRoles = listOf(FamilyUserRole("family", "same", "家人"))
        )
        assertTrue(result.isFailure)
    }
}
