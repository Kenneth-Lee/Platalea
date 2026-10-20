package com.kenny.localmanager.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.kenny.localmanager.family.validateBoardCreationInput

class BoardCreateInputValidationTest {
    @Test
    fun acceptsUniqueName() {
        val result = validateBoardCreationInput("新留言板", setOf("旧留言板"))

        assertTrue(result.isValid)
        assertEquals("新留言板", result.trimmedName)
        assertNull(result.error)
    }

    @Test
    fun rejectsDuplicateName() {
        val result = validateBoardCreationInput("旧留言板", setOf("旧留言板"))

        assertFalse(result.isValid)
        assertEquals("旧留言板", result.trimmedName)
        assertEquals("duplicate", result.error)
    }

    @Test
    fun rejectsBlankName() {
        val result = validateBoardCreationInput("   ", emptySet())

        assertFalse(result.isValid)
        assertEquals("", result.trimmedName)
        assertNull(result.error)
    }
}
