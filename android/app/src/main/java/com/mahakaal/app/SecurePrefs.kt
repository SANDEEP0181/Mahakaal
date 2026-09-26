package com.mahakaal.app

import android.content.Context
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object SecurePrefs {
    private const val PREFS = "mahakaal_secure"
    private const val KEY_ALIAS = "mahakaal_token_key"
    private const val TOKEN = "token"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = ks.getKey(KEY_ALIAS, null)
        if (existing is SecretKey) return existing
        val generator = KeyGenerator.getInstance("AES", "AndroidKeyStore")
        generator.init(android.security.keystore.KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or
                android.security.keystore.KeyProperties.PURPOSE_DECRYPT
        ).setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
            .build())
        return generator.generateKey()
    }

    fun saveToken(context: Context, token: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val value = Base64.encodeToString(cipher.doFinal(token.toByteArray(StandardCharsets.UTF_8)), Base64.NO_WRAP)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("iv", iv).putString(TOKEN, value).apply()
    }

    fun getToken(context: Context): String? = try {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val iv = Base64.decode(prefs.getString("iv", null) ?: return null, Base64.NO_WRAP)
        val value = Base64.decode(prefs.getString(TOKEN, null) ?: return null, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        String(cipher.doFinal(value), StandardCharsets.UTF_8)
    } catch (_: Exception) { null }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    fun migrateLegacy(context: Context) {
        val legacy = context.getSharedPreferences("mahakaal", Context.MODE_PRIVATE)
        val old = legacy.getString(TOKEN, null)
        if (!old.isNullOrBlank()) {
            saveToken(context, old)
            legacy.edit().remove(TOKEN).apply()
        }
    }
}
