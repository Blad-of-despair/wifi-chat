package com.example

import com.example.network.HomeLinkCrypto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun pinHashing_isConsistentAndSecure() {
        val pin = "1234"
        val hash1 = HomeLinkCrypto.hashPin(pin)
        val hash2 = HomeLinkCrypto.hashPin(pin)
        val hashWrong = HomeLinkCrypto.hashPin("9999")

        assertEquals(hash1, hash2)
        assertNotEquals(hash1, hashWrong)
        assertEquals(64, hash1.length) // SHA-256 hex is 64 chars
    }

    @Test
    fun payloadEncryption_roundtripWorks() {
        val originalText = "Hello family on local Wi-Fi!"
        val pin = "5678"
        val encrypted = HomeLinkCrypto.encryptPayload(originalText, pin)
        assertNotEquals(originalText, encrypted)

        val decrypted = HomeLinkCrypto.decryptPayload(encrypted, pin)
        assertEquals(originalText, decrypted)
    }
}
