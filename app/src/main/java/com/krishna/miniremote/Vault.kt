package com.krishna.miniremote

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts what's sensitive at rest (TV pairing tokens and certificate pins, which live in the
 * saved TV list) with an AES-256-GCM key kept in the Android Keystore. The key never leaves
 * the phone's secure hardware, so a copy of the app's files alone can't be read.
 */
object Vault {
    private const val ALIAS = "mini-remote-store"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
        }.generateKey()
    }

    /** "v1:" + base64(iv | ciphertext). */
    fun seal(plain: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val out = c.iv + c.doFinal(plain.toByteArray())
        return "v1:" + Base64.encodeToString(out, Base64.NO_WRAP)
    }

    /** The plain text, or null if it can't be opened (wrong format or the key is gone). */
    fun open(sealed: String): String? = runCatching {
        require(sealed.startsWith("v1:"))
        val all = Base64.decode(sealed.removePrefix("v1:"), Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, all, 0, 12))
        }
        String(c.doFinal(all, 12, all.size - 12))
    }.getOrNull()

    /** Deletes the key (used by "Erase all data"). */
    fun destroy() { runCatching { KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(ALIAS) } }
}
