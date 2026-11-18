package com.example.sync_up.data.remote

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.exception.NoSessionFoundException
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Stores the session encrypted by an Android Keystore key that never leaves the device. */
internal class SecureSessionManager(context: Context, projectUrl: String) : SessionManager {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val sessionKey = MessageDigest.getInstance("SHA-256")
        .digest(projectUrl.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    override suspend fun saveSession(session: UserSession) = withContext(Dispatchers.IO) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key(create = true))
        val ciphertext = cipher.doFinal(json.encodeToString(session).toByteArray(Charsets.UTF_8))
        val stored = Base64.encodeToString(cipher.iv + ciphertext, Base64.NO_WRAP)
        if (!preferences.edit().putString(sessionKey, stored).commit()) {
            throw IOException("Could not store the session")
        }
    }

    override suspend fun loadSession(): UserSession = withContext(Dispatchers.IO) {
        val stored = preferences.getString(sessionKey, null) ?: throw NoSessionFoundException()
        try {
            val payload = Base64.decode(stored, Base64.NO_WRAP)
            require(payload.size > IV_LENGTH)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                key(create = false),
                GCMParameterSpec(128, payload.copyOfRange(0, IV_LENGTH))
            )
            val plaintext = cipher.doFinal(payload.copyOfRange(IV_LENGTH, payload.size))
            json.decodeFromString<UserSession>(plaintext.toString(Charsets.UTF_8))
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // A restored file without its device key cannot be decrypted; require sign-in again.
            preferences.edit().remove(sessionKey).commit()
            throw NoSessionFoundException()
        }
    }

    override suspend fun deleteSession() = withContext(Dispatchers.IO) {
        if (!preferences.edit().remove(sessionKey).commit()) {
            throw IOException("Could not remove the session")
        }
    }

    @Synchronized
    private fun key(create: Boolean): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        if (!create) throw NoSessionFoundException()
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val PREFERENCES = "syncup_auth_session"
        const val KEY_ALIAS = "syncup_auth_session_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
    }
}
