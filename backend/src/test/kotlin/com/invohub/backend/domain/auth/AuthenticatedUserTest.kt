package com.invohub.backend.domain.auth

import com.invohub.backend.domain.user.User
import java.io.ByteArrayOutputStream
import java.io.ObjectOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class AuthenticatedUserTest {
    private val user = User("jane@example.com")

    @Test
    fun `holds the public ID and derives the internal one`() {
        val principal = AuthenticatedUser.of(user)

        assertEquals(user.getPublicId(), principal.publicId)
        assertEquals(user.getPublicId(), principal.name)
        assertEquals(user.id, principal.userId)
    }

    @Test
    fun `the internal ID is not part of what is stored in the session`() {
        val serialized =
            ByteArrayOutputStream().use { bytes ->
                ObjectOutputStream(bytes).use { it.writeObject(AuthenticatedUser.of(user)) }
                bytes.toByteArray()
            }

        // A Long field would be written as 8 raw bytes; the public ID string is what's there instead
        assertFalse(serialized.toList().windowed(8).contains(user.id.toBigEndianBytes()))
        assertEquals(true, String(serialized, Charsets.ISO_8859_1).contains(user.getPublicId()))
    }

    private fun Long.toBigEndianBytes() = (7 downTo 0).map { (this shr (8 * it)).toByte() }
}
