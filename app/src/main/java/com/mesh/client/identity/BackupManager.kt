package com.mesh.client.identity

import java.security.SecureRandom

object BackupManager {
    private const val SEED_SIZE_BYTES = 32

    fun generateSeed(): ByteArray {
        val secureRandom = SecureRandom()
        val seed = ByteArray(SEED_SIZE_BYTES)
        secureRandom.nextBytes(seed)
        return seed
    }
    
    /**
     * Generate a new mnemonic phrase (12 words)
     */
    fun generateMnemonic(): String {
        return MnemonicManager.generateMnemonic()
    }
    
    /**
     * Convert mnemonic to seed
     */
    fun mnemonicToSeed(mnemonic: String): ByteArray {
        return MnemonicManager.mnemonicToSeed(mnemonic)
    }
    
    /**
     * Validate mnemonic phrase
     */
    fun validateMnemonic(mnemonic: String): Boolean {
        return MnemonicManager.validateMnemonic(mnemonic)
    }

    fun seedToHex(seed: ByteArray): String {
        return seed.joinToString("") { "%02x".format(it) }
    }

    fun hexToSeed(hex: String): ByteArray {
        check(hex.length % 2 == 0) { "Invalid hex string length" }
        return hex.chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }
}
