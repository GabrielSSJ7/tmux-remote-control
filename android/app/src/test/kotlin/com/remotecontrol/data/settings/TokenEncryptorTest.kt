package com.remotecontrol.data.settings

import org.junit.Test
import org.junit.Assert.*
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class TokenEncryptorTest {

    private fun newKey(): SecretKey {
        val kg = KeyGenerator.getInstance("AES")
        kg.init(256)
        return kg.generateKey()
    }

    @Test
    fun `encrypt returns non-empty string different from plaintext`() {
        val crypto = TestTokenCrypto(newKey())
        val result = crypto.encrypt("my-secret-token")
        assertNotEquals("", result)
        assertNotEquals("my-secret-token", result)
    }

    @Test
    fun `roundtrip encrypt then decrypt returns original`() {
        val crypto = TestTokenCrypto(newKey())
        val original = "my-secret-token"
        assertEquals(original, crypto.decrypt(crypto.encrypt(original)))
    }

    @Test
    fun `encrypt empty string returns empty string`() {
        val crypto = TestTokenCrypto(newKey())
        assertEquals("", crypto.encrypt(""))
    }

    @Test
    fun `decrypt invalid base64 returns empty string`() {
        val crypto = TestTokenCrypto(newKey())
        assertEquals("", crypto.decrypt("not-valid-base64-blob!!!"))
    }

    @Test
    fun `decrypt ciphertext from different key returns empty string`() {
        val encryptor = TestTokenCrypto(newKey())
        val decryptor = TestTokenCrypto(newKey())
        val ciphertext = encryptor.encrypt("my-secret-token")
        assertEquals("", decryptor.decrypt(ciphertext))
    }
}
