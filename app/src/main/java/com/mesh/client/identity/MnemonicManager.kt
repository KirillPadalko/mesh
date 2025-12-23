package com.mesh.client.identity

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * BIP-39 Mnemonic implementation for generating and validating 12-word seed phrases.
 * Uses English wordlist and PBKDF2 for seed derivation.
 */
object MnemonicManager {
    
    // BIP-39 English wordlist (first 100 words for MVP - full list should be 2048)
    // For production, use complete BIP-39 wordlist
    private val WORDLIST = listOf(
        "abandon", "ability", "able", "about", "above", "absent", "absorb", "abstract",
        "absurd", "abuse", "access", "accident", "account", "accuse", "achieve", "acid",
        "acoustic", "acquire", "across", "act", "action", "actor", "actress", "actual",
        "adapt", "add", "addict", "address", "adjust", "admit", "adult", "advance",
        "advice", "aerobic", "affair", "afford", "afraid", "again", "age", "agent",
        "agree", "ahead", "aim", "air", "airport", "aisle", "alarm", "album",
        "alcohol", "alert", "alien", "all", "alley", "allow", "almost", "alone",
        "alpha", "already", "also", "alter", "always", "amateur", "amazing", "among",
        "amount", "amused", "analyst", "anchor", "ancient", "anger", "angle", "angry",
        "animal", "ankle", "announce", "annual", "another", "answer", "antenna", "antique",
        "anxiety", "any", "apart", "apology", "appear", "apple", "approve", "april",
        "arch", "arctic", "area", "arena", "argue", "arm", "armed", "armor",
        "army", "around", "arrange", "arrest", "arrive", "arrow", "art", "artefact"
    )
    
    private const val PBKDF2_ROUNDS = 2048
    private const val ENTROPY_BITS = 128 // 12 words
    
    /**
     * Generate a 12-word mnemonic phrase from entropy
     */
    fun generateMnemonic(): String {
        val entropy = ByteArray(ENTROPY_BITS / 8)
        SecureRandom().nextBytes(entropy)
        return entropyToMnemonic(entropy)
    }
    
    /**
     * Convert entropy bytes to mnemonic words
     */
    private fun entropyToMnemonic(entropy: ByteArray): String {
        require(entropy.size == 16) { "Entropy must be 128 bits (16 bytes)" }
        
        // Calculate checksum
        val hash = MessageDigest.getInstance("SHA-256").digest(entropy)
        val checksumBits = hash[0].toInt() and 0xF0 // First 4 bits
        
        // Combine entropy + checksum
        val bits = entropy.toBinaryString() + (checksumBits shr 4).toString(2).padStart(4, '0')
        
        // Split into 11-bit chunks and convert to words
        val words = mutableListOf<String>()
        for (i in 0 until 12) {
            val startBit = i * 11
            val endBit = startBit + 11
            val chunk = bits.substring(startBit, endBit)
            val index = chunk.toInt(2)
            words.add(WORDLIST[index % WORDLIST.size])
        }
        
        return words.joinToString(" ")
    }
    
    /**
     * Convert mnemonic to seed bytes using PBKDF2
     */
    fun mnemonicToSeed(mnemonic: String, passphrase: String = ""): ByteArray {
        val normalizedMnemonic = mnemonic.trim().lowercase()
        val salt = "mnemonic$passphrase"
        
        val spec = PBEKeySpec(
            normalizedMnemonic.toCharArray(),
            salt.toByteArray(Charsets.UTF_8),
            PBKDF2_ROUNDS,
            512 // BIP-39 specifies 512-bit output
        )
        
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
        val fullSeed = factory.generateSecret(spec).encoded
        
        // Return first 32 bytes for Ed25519
        return fullSeed.copyOf(32)
    }
    
    /**
     * Validate mnemonic phrase
     */
    fun validateMnemonic(mnemonic: String): Boolean {
        val words = mnemonic.trim().lowercase().split("\\s+".toRegex())
        if (words.size != 12) return false
        
        return words.all { it in WORDLIST }
    }
    
    /**
     * Convert ByteArray to binary string representation
     */
    private fun ByteArray.toBinaryString(): String {
        return this.joinToString("") { byte ->
            byte.toInt().and(0xFF).toString(2).padStart(8, '0')
        }
    }
}
