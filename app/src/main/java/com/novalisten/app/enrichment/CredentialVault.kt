package com.novalisten.app.enrichment

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

enum class CredentialStatus { ABSENT, STORED, UNREADABLE }

/** No plaintext credentials in preferences, saved state, logs, resources or BuildConfig. */
class CredentialVault(context: Context, namespace: String = "api_credentials_v1") {
    private val preferences = context.getSharedPreferences(namespace, Context.MODE_PRIVATE)
    private val alias = "${context.packageName}.$namespace.aes.v1"
    private val mutex = Mutex()
    private val _status = MutableStateFlow(SecretSlot.entries.associateWith { CredentialStatus.ABSENT })
    val status = _status.asStateFlow()
    private fun store() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private fun key(create: Boolean): SecretKey {
        val existing = store().getKey(alias, null) as? SecretKey
        if (existing != null) return existing
        check(create) { "Clé de chiffrement indisponible" }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).setRandomizedEncryptionRequired(true).build())
        }.generateKey()
    }
    private fun decrypt(slot: SecretSlot): String? {
        val stored = preferences.getString(slot.name, null) ?: return null
        val pieces = stored.split(':')
        require(pieces.size == 3 && pieces[0] == "1")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(false), GCMParameterSpec(128, Base64.decode(pieces[1], Base64.NO_WRAP)))
        cipher.updateAAD("$alias/${slot.name}".toByteArray(Charsets.UTF_8))
        return String(cipher.doFinal(Base64.decode(pieces[2], Base64.NO_WRAP)), Charsets.UTF_8)
    }
    private fun refreshLocked() {
        _status.value = SecretSlot.entries.associateWith { slot ->
            if (!preferences.contains(slot.name)) CredentialStatus.ABSENT
            else try { decrypt(slot); CredentialStatus.STORED } catch (_: Exception) { CredentialStatus.UNREADABLE }
        }
    }
    suspend fun refresh() = withContext(Dispatchers.IO) { mutex.withLock { refreshLocked() } }
    suspend fun get(slot: SecretSlot): String? = withContext(Dispatchers.IO) {
        mutex.withLock {
            try { decrypt(slot) } catch (_: Exception) { refreshLocked(); throw RemoteFailure("Identifiant chiffré illisible. Ressaisissez-le dans Réglages → API et clés. Aucun appel n’a été envoyé.") }
        }
    }
    suspend fun save(slot: SecretSlot, raw: String) = withContext(Dispatchers.IO) {
        val value = slot.validate(raw)
        mutex.withLock {
            try {
                if (value.isEmpty()) {
                    check(preferences.edit().remove(slot.name).commit())
                } else {
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    cipher.init(Cipher.ENCRYPT_MODE, key(true))
                    cipher.updateAAD("$alias/${slot.name}".toByteArray(Charsets.UTF_8))
                    val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
                    val entry = "1:${Base64.encodeToString(cipher.iv, Base64.NO_WRAP)}:${Base64.encodeToString(encrypted, Base64.NO_WRAP)}"
                    check(preferences.edit().putString(slot.name, entry).commit())
                }
                refreshLocked()
            } catch (_: Exception) { throw RemoteFailure("Stockage sécurisé indisponible. Identifiant non enregistré ; aucun repli en texte clair.") }
        }
    }
    suspend fun remove(slot: SecretSlot) = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!preferences.edit().remove(slot.name).commit()) throw RemoteFailure("La suppression de cet identifiant a échoué.")
            refreshLocked()
        }
    }
    suspend fun clear() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!preferences.edit().clear().commit()) throw RemoteFailure("La suppression des identifiants a échoué.")
            store().deleteEntry(alias)
            refreshLocked()
        }
    }
}
