package com.boory.smartflow

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object SecureStore {

    private const val ALIAS = "BOORY_SMART_FLOW_KEY"
    private const val PREFS = "boory_secure_settings"

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore")
        store.load(null)

        val existing = store.getKey(ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )

        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or
                    KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(
                    KeyProperties.ENCRYPTION_PADDING_NONE
                )
                .setRandomizedEncryptionRequired(true)
                .build()
        )

        return generator.generateKey()
    }

    fun save(context: Context, name: String, value: String) {
        val prefs = context.getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE
        )

        if (value.isBlank()) {
            prefs.edit().remove(name).apply()
            return
        }

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())

        val encrypted = cipher.doFinal(
            value.toByteArray(Charsets.UTF_8)
        )

        val iv = cipher.iv
        val combined = ByteArray(iv.size + encrypted.size)

        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(
            encrypted, 0,
            combined, iv.size,
            encrypted.size
        )

        prefs.edit()
            .putString(
                name,
                Base64.encodeToString(combined, Base64.NO_WRAP)
            )
            .apply()
    }

    fun get(context: Context, name: String): String {
        return try {
            val prefs = context.getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            )

            val stored = prefs.getString(name, null) ?: return ""
            val combined = Base64.decode(stored, Base64.NO_WRAP)

            require(combined.size > 12)

            val iv = combined.copyOfRange(0, 12)
            val encrypted = combined.copyOfRange(12, combined.size)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                secretKey(),
                GCMParameterSpec(128, iv)
            )

            String(
                cipher.doFinal(encrypted),
                Charsets.UTF_8
            )
        } catch (_: Exception) {
            ""
        }
    }
}
