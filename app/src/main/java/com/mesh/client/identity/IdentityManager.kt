package com.mesh.client.identity

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import net.i2p.crypto.eddsa.EdDSAPrivateKey
import net.i2p.crypto.eddsa.EdDSAPublicKey
import net.i2p.crypto.eddsa.Utils
import net.i2p.crypto.eddsa.spec.EdDSAPrivateKeySpec
import net.i2p.crypto.eddsa.spec.EdDSAPublicKeySpec
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable
import java.io.IOException
import java.security.KeyPair
import javax.crypto.AEADBadTagException

class IdentityManager(private val context: Context) {

    companion object {
        private const val TAG = "IdentityManager"
        private const val PREFS_NAME = "mesh_secure_prefs"
        private const val KEY_SEED = "identity_seed"
        private const val KEY_MNEMONIC = "identity_mnemonic"
        private const val KEY_NICKNAME = "identity_nickname"
    }

    // Base58 characters
    private val ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz".toCharArray()
    private val ENCODED_ZERO = ALPHABET[0]

    // Use the default alias so we can delete it if needed
    private val masterKeyAlias = MasterKey.DEFAULT_MASTER_KEY_ALIAS

    private val sharedPreferences: SharedPreferences = createEncryptedPreferences()

    private var cachedKeyPair: KeyPair? = null
    private var cachedMeshId: String? = null

    /**
     * Creates EncryptedSharedPreferences with error recovery for corrupted keystore data.
     * If decryption fails, it clears the corrupted preferences, deletes the keystore entry, 
     * and creates a fresh instance.
     */
    private fun createEncryptedPreferences(): SharedPreferences {
        try {
            return buildEncryptedPreferences()
        } catch (e: Exception) {
            // Check if the exception is related to decryption failure
            val isDecryptionFailure = e is AEADBadTagException ||
                    e.cause is AEADBadTagException ||
                    e is java.security.GeneralSecurityException ||
                    e.message?.contains("decrypt", ignoreCase = true) == true
            
            if (isDecryptionFailure) {
                Log.e(TAG, "EncryptedSharedPreferences decryption failed. Clearing corrupted data and recreating.", e)
                
                // 1. Delete the corrupted preferences file
                try {
                    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
                    context.deleteSharedPreferences(PREFS_NAME)
                } catch (ex: Exception) {
                    Log.w(TAG, "Failed to delete corrupted preferences", ex)
                }
                
                // 2. Delete the Master Key from Android KeyStore
                try {
                    val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore")
                    keyStore.load(null)
                    keyStore.deleteEntry(masterKeyAlias)
                } catch (ex: Exception) {
                    Log.w(TAG, "Failed to delete corrupted MasterKey", ex)
                }
                
                // 3. Retry creating fresh encrypted preferences (will generate new MasterKey)
                try {
                    return buildEncryptedPreferences()
                } catch (retryEx: Exception) {
                    Log.e(TAG, "Failed to recreate EncryptedSharedPreferences after clearing", retryEx)
                    throw RuntimeException("Unable to initialize secure storage", retryEx)
                }
            } else {
                throw e
            }
        }
    }

    private fun buildEncryptedPreferences(): SharedPreferences {
         val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun hasIdentity(): Boolean {
        return sharedPreferences.contains(KEY_SEED)
    }

    fun getMeshId(): String {
        return cachedMeshId ?: getOrCreateIdentity().let {
             cachedMeshId = publicKeyToBase58(it.public as EdDSAPublicKey)
             cachedMeshId!!
        }
    }

    fun getIdentityKeyPair(): KeyPair {
        return cachedKeyPair ?: getOrCreateIdentity()
    }

    @Synchronized
    private fun getOrCreateIdentity(): KeyPair {
        val existingSeedHex = sharedPreferences.getString(KEY_SEED, null)
        val seed = if (existingSeedHex != null) {
             BackupManager.hexToSeed(existingSeedHex)
        } else {
             val newSeed = BackupManager.generateSeed()
             sharedPreferences.edit().putString(KEY_SEED, BackupManager.seedToHex(newSeed)).apply()
             newSeed
        }
        return deriveKeyPair(seed).also { cachedKeyPair = it }
    }
    
    /**
     * Create identity from mnemonic phrase
     */
    fun createFromMnemonic(mnemonic: String) {
        require(BackupManager.validateMnemonic(mnemonic)) { "Invalid mnemonic phrase" }
        val seed = BackupManager.mnemonicToSeed(mnemonic)
        val seedHex = BackupManager.seedToHex(seed)
        sharedPreferences.edit()
            .putString(KEY_SEED, seedHex)
            .putString(KEY_MNEMONIC, mnemonic)
            .apply()
        cachedKeyPair = null
        cachedMeshId = null
        getOrCreateIdentity()
    }

    fun restoreIdentity(seedHex: String) {
        val seed = BackupManager.hexToSeed(seedHex) // validate hex
        sharedPreferences.edit().putString(KEY_SEED, seedHex).apply()
        cachedKeyPair = null
        cachedMeshId = null
        getOrCreateIdentity() // regenerate
    }
    
    /**
     * Restore identity from mnemonic phrase
     */
    fun restoreFromMnemonic(mnemonic: String) {
        require(BackupManager.validateMnemonic(mnemonic)) { "Invalid mnemonic phrase" }
        val seed = BackupManager.mnemonicToSeed(mnemonic)
        val seedHex = BackupManager.seedToHex(seed)
        sharedPreferences.edit()
            .putString(KEY_SEED, seedHex)
            .putString(KEY_MNEMONIC, mnemonic)
            .apply()
        cachedKeyPair = null
        cachedMeshId = null
        getOrCreateIdentity() // regenerate
    }
    
    fun exportSeedHex(): String {
        return sharedPreferences.getString(KEY_SEED, null) ?: throw IllegalStateException("Identity not created")
    }
    
    /**
     * Export identity as mnemonic phrase
     */
    fun exportMnemonic(): String? {
        return sharedPreferences.getString(KEY_MNEMONIC, null)
    }

    fun getSeed(): ByteArray? {
        val seedHex = sharedPreferences.getString(KEY_SEED, null) ?: return null
        return BackupManager.hexToSeed(seedHex)
    }



    fun setLocalNickname(nickname: String) {
        sharedPreferences.edit().putString(KEY_NICKNAME, nickname).apply()
    }

    fun getLocalNickname(): String {
        return sharedPreferences.getString(KEY_NICKNAME, "User") ?: "User"
    }

    private fun deriveKeyPair(seed: ByteArray): KeyPair {
        val spec = EdDSANamedCurveTable.getByName(EdDSANamedCurveTable.ED_25519)
        // Ed25519 private key is just the seed/scalar
        val privateKeySpec = EdDSAPrivateKeySpec(seed, spec)
        val privateKey = EdDSAPrivateKey(privateKeySpec)
        
        // Derive public key from private key spec
        val publicKeySpec = EdDSAPublicKeySpec(privateKeySpec.a, spec)
        val publicKey = EdDSAPublicKey(publicKeySpec)
        
        return KeyPair(publicKey, privateKey)
    }

    private fun publicKeyToBase58(publicKey: EdDSAPublicKey): String {
        return com.mesh.client.utils.Utils.encodeBase58(publicKey.abyte)
    }
}
