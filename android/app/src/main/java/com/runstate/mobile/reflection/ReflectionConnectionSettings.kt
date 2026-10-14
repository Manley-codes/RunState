package com.runstate.mobile.reflection

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.net.URI
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** A runner-supplied hosted endpoint and an opaque RunState access code, never a provider API key. */
internal class ReflectionConnectionConfig(val endpoint: String, val accessCode: String) {
    init {
        val uri = try {
            URI(endpoint)
        } catch (invalid: Exception) {
            throw IllegalArgumentException("Enter a valid HTTPS reflection address.")
        }
        val host = uri.host ?: ""
        val label = "[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?"
        require(
            endpoint == endpoint.trim() && !uri.isOpaque &&
                uri.scheme.equals("https", ignoreCase = true) &&
                host.length <= 253 && host.lowercase(Locale.ROOT).matches(
                    Regex("$label(?:\\.$label)+")
                ) && !host.matches(Regex("[0-9.]+")) &&
                !host.endsWith(".localhost", ignoreCase = true) &&
                (uri.port == -1 || uri.port == 443) &&
                (uri.rawAuthority.equals(host, ignoreCase = true) ||
                    uri.rawAuthority.equals("$host:443", ignoreCase = true)) &&
                uri.rawPath == "/api/reflections/candidates" &&
                uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null
        ) {
            "Enter an HTTPS DNS address ending in /api/reflections/candidates."
        }
        require(
            accessCode.matches(Regex("[A-Za-z0-9_-]{43,128}")) &&
                !accessCode.startsWith("sk-ant-", ignoreCase = true)
        ) {
            "Enter a RunState access code, not a provider API key."
        }
    }

    override fun toString(): String = "ReflectionConnectionConfig[connection=redacted]"
}

/** The connection can be absent, saved, or explicitly cleared. A damaged save never falls back. */
internal interface ReflectionConnectionStore {
    suspend fun load(): ReflectionConnectionConfig?
    suspend fun save(config: ReflectionConnectionConfig)
    suspend fun clear()
}

/**
 * Encrypts the address and access code together with a device-bound AndroidKeyStore AES-GCM key.
 * This SharedPreferences file must be excluded from cloud and device-transfer backups: a restored
 * ciphertext cannot use the original device's Keystore key.
 */
internal class AndroidReflectionConnectionStore(
    context: Context,
    /** Override only in isolated tests; production always uses the default non-backed-up file. */
    private val preferencesName: String = PREFERENCES_NAME,
    /** Override with a unique alias in tests so no test opens a runner's production key. */
    private val keyAlias: String = KEY_ALIAS
) : ReflectionConnectionStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        preferencesName,
        Context.MODE_PRIVATE
    )

    override suspend fun load(): ReflectionConnectionConfig? = withContext(Dispatchers.IO) {
        storeMutex.withLock {
            try {
                if (!preferences.contains(ENCRYPTED_ENTRY)) return@withLock null
                val encoded = requireNotNull(preferences.getString(ENCRYPTED_ENTRY, null))
                val packed = Base64.decode(encoded, Base64.NO_WRAP)
                require(packed.size >= IV_BYTES + GCM_TAG_BYTES)
                val key = requireNotNull(existingKey()) // Never create a key for orphaned ciphertext.
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(
                    Cipher.DECRYPT_MODE,
                    key,
                    GCMParameterSpec(GCM_TAG_BITS, packed.copyOfRange(0, IV_BYTES))
                )
                cipher.updateAAD(ASSOCIATED_DATA)
                val decoded = String(cipher.doFinal(packed.copyOfRange(IV_BYTES, packed.size)),
                    StandardCharsets.UTF_8)
                val separator = decoded.indexOf('\n')
                require(separator > 0 && separator < decoded.lastIndex)
                ReflectionConnectionConfig(
                    decoded.substring(0, separator),
                    decoded.substring(separator + 1)
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                // Keystore loss, corrupted ciphertext and malformed saved data all fail closed.
                throw IllegalStateException(STORAGE_ERROR)
            }
        }
    }

    override suspend fun save(config: ReflectionConnectionConfig) = withContext(Dispatchers.IO) {
        storeMutex.withLock {
            try {
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.ENCRYPT_MODE, existingKey() ?: createKey())
                cipher.updateAAD(ASSOCIATED_DATA)
                val plaintext = "${config.endpoint}\n${config.accessCode}"
                    .toByteArray(StandardCharsets.UTF_8)
                val encrypted = cipher.doFinal(plaintext)
                val iv = cipher.iv
                require(iv.size == IV_BYTES)
                val encoded = Base64.encodeToString(iv + encrypted, Base64.NO_WRAP)
                check(preferences.edit().putString(ENCRYPTED_ENTRY, encoded).commit())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                throw IllegalStateException(STORAGE_ERROR)
            }
        }
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        storeMutex.withLock {
            try {
                check(preferences.edit().remove(ENCRYPTED_ENTRY).commit())
                val store = KeyStore.getInstance("AndroidKeyStore")
                store.load(null)
                if (store.containsAlias(keyAlias)) store.deleteEntry(keyAlias)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                throw IllegalStateException(STORAGE_ERROR)
            }
        }
    }

    private fun existingKey(): SecretKey? {
        val store = KeyStore.getInstance("AndroidKeyStore")
        store.load(null)
        return store.getKey(keyAlias, null) as? SecretKey
    }

    private fun createKey(): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val PREFERENCES_NAME = "reflection_connection"
        const val ENCRYPTED_ENTRY = "connection_v1"
        const val KEY_ALIAS = "com.runstate.mobile.reflection.connection.aes.v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
        const val GCM_TAG_BYTES = 16
        const val IV_BYTES = 12
        const val STORAGE_ERROR = "Saved reflection connection is unavailable."
        val ASSOCIATED_DATA = "RunState/reflection_connection/v1".toByteArray(StandardCharsets.US_ASCII)
        val storeMutex = Mutex()
    }
}
