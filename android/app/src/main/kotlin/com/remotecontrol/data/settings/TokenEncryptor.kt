package com.remotecontrol.data.settings

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.util.Base64
import javax.crypto.BadPaddingException

private const val KEY_ALIAS = "remote_control_token_key"
private const val TRANSFORMATION = "AES/GCM/NoPadding"
private const val IV_SIZE = 12
private const val TAG_LENGTH = 128

interface TokenCrypto {
    fun encrypt(plaintext: String): String
    fun decrypt(encoded: String): String
}

object KeystoreTokenCrypto : TokenCrypto {

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        keyStore.getKey(KEY_ALIAS, null)?.let { return it as SecretKey }
        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        kg.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return kg.generateKey()
    }

    override fun encrypt(plaintext: String): String {
        if (plaintext.isEmpty()) return ""
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val combined = ByteArray(IV_SIZE + ciphertext.size)
        System.arraycopy(iv, 0, combined, 0, IV_SIZE)
        System.arraycopy(ciphertext, 0, combined, IV_SIZE, ciphertext.size)
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    override fun decrypt(encoded: String): String {
        if (encoded.isEmpty()) return ""
        return try {
            val combined = Base64.decode(encoded, Base64.NO_WRAP)
            val iv = combined.copyOfRange(0, IV_SIZE)
            val ciphertext = combined.copyOfRange(IV_SIZE, combined.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_LENGTH, iv))
            cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
        } catch (_: BadPaddingException) {
            ""
        } catch (_: AEADBadTagException) {
            ""
        } catch (_: IllegalArgumentException) {
            ""
        } catch (_: Exception) {
            ""
        }
    }
}

object TokenEncryptor : TokenCrypto {
    var delegate: TokenCrypto = KeystoreTokenCrypto
    override fun encrypt(plaintext: String) = delegate.encrypt(plaintext)
    override fun decrypt(encoded: String) = delegate.decrypt(encoded)
}

class TestTokenCrypto(private val key: SecretKey) : TokenCrypto {

    override fun encrypt(plaintext: String): String {
        if (plaintext.isEmpty()) return ""
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val combined = ByteArray(IV_SIZE + ciphertext.size)
        System.arraycopy(iv, 0, combined, 0, IV_SIZE)
        System.arraycopy(ciphertext, 0, combined, IV_SIZE, ciphertext.size)
        return java.util.Base64.getEncoder().encodeToString(combined)
    }

    override fun decrypt(encoded: String): String {
        if (encoded.isEmpty()) return ""
        return try {
            val combined = java.util.Base64.getDecoder().decode(encoded)
            val iv = combined.copyOfRange(0, IV_SIZE)
            val ciphertext = combined.copyOfRange(IV_SIZE, combined.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH, iv))
            cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
        } catch (_: BadPaddingException) {
            ""
        } catch (_: AEADBadTagException) {
            ""
        } catch (_: IllegalArgumentException) {
            ""
        } catch (_: Exception) {
            ""
        }
    }
}
