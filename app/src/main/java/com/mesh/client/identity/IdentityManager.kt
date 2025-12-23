package com.mesh.client.identity

import android.content.Context
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

class IdentityManager(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "mesh_secure_prefs"
        private const val KEY_SEED = "identity_seed"
        private const val KEY_MNEMONIC = "identity_mnemonic"
    }

    // Base58 characters
    private val ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz".toCharArray()
    private val ENCODED_ZERO = ALPHABET[0]

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private var cachedKeyPair: KeyPair? = null
    private var cachedMeshId: String? = null

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
