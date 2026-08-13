package com.kenny.localmanager.family

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BulletinMessageEditMetaTest {
    @Test
    fun `editor label marks a message as edited`() {
        val original = BulletinMessage(
            id = "m1",
            seq = 1L,
            authorLabel = "Alice",
            content = "hello",
            createdAt = 1000L,
            updatedAt = 2000L
        )

        assertFalse(original.isEdited)

        val updated = original.copy(editorLabel = "Bob")

        assertEquals("Bob", updated.editorLabel)
        assertTrue(updated.isEdited)
        assertEquals("hello", updated.content)
    }

    @Test
    fun `recorded editor label means the message was modified`() {
        val message = BulletinMessage(
            id = "m2",
            seq = 2L,
            authorLabel = "Alice",
            content = "original",
            createdAt = 1000L,
            updatedAt = 1000L,
            editorLabel = "Alice"
        )

        assertTrue(message.isEdited)
    }
}
