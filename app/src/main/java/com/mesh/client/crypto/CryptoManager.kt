package com.mesh.client.crypto

import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.mesh.client.data.EncryptedMessage
import com.mesh.client.identity.IdentityManager
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.math.ec.rfc8032.Ed25519
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.nio.charset.StandardCharsets

class CryptoManager(private val identityManager: IdentityManager) {

    private val gson = Gson()
    private val secureRandom = SecureRandom()

    // AES-GCM constants
    private val AES_MODE = "AES/GCM/NoPadding"
    private val GCM_TAG_LENGTH_BIT = 128
    private val GCM_IV_LENGTH_BYTE = 12

    /**
     * Encrypts a plaintext string for a specific peer.
     * Derives or retrieves the session key for the peer (Mesh-ID).
     */
    fun encryptMessage(plaintext: String, peerMeshId: String): EncryptedMessage {
        val sessionKey = deriveSessionKey(peerMeshId)
        
        val iv = ByteArray(GCM_IV_LENGTH_BYTE)
        secureRandom.nextBytes(iv)

        val cipher = Cipher.getInstance(AES_MODE)
        val keySpec = SecretKeySpec(sessionKey, "AES")
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BIT, iv)
        
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)
        val ciphertextBytes = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))

        return EncryptedMessage(
            ciphertext = Base64.encodeToString(ciphertextBytes, Base64.NO_WRAP),
            nonce = Base64.encodeToString(iv, Base64.NO_WRAP),
            timestamp = System.currentTimeMillis()
        )
    }

    /**
     * Decrypts an encrypted message from a peer.
     */
    fun decryptMessage(encryptedMessage: EncryptedMessage, peerMeshId: String): String {
        val sessionKey = deriveSessionKey(peerMeshId)

        val ciphertextBytes = Base64.decode(encryptedMessage.ciphertext, Base64.NO_WRAP)
        val iv = Base64.decode(encryptedMessage.nonce, Base64.NO_WRAP)

        val cipher = Cipher.getInstance(AES_MODE)
        val keySpec = SecretKeySpec(sessionKey, "AES")
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BIT, iv)

        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
        val plaintextBytes = cipher.doFinal(ciphertextBytes)

        return String(plaintextBytes, StandardCharsets.UTF_8)
    }

    /**
     * Derives a session key using static-static ECDH + HKDF.
     * Converts Ed25519 Identity keys to X25519 keys for the handshake.
     * 
     * Formula:
     * shared_secret = X25519(my_x_priv, peer_x_pub)
     * session_key = HKDF(shared_secret)
     */
    /**
     * Derives a session key using ECDH (X25519) + HKDF.
     * Converts Ed25519 identity keys to X25519 for the handshake.
     */
    @Synchronized
    private fun deriveSessionKey(peerMeshId: String): ByteArray {
        val mySeed = identityManager.getSeed() ?: throw IllegalStateException("No Identity seed")
        val peerEdPubKey = decodeBase58(peerMeshId)

        // 1. Derive my X25519 Private Key from my Ed25519 seed
        // Standard way: X25519_priv = SHA512(seed)[0..31] + clamping
        val myX25519Priv = java.security.MessageDigest.getInstance("SHA-512").digest(mySeed).copyOfRange(0, 32)
        myX25519Priv[0] = myX25519Priv[0].toInt().and(248).toByte()
        myX25519Priv[31] = myX25519Priv[31].toInt().and(127).toByte()
        myX25519Priv[31] = myX25519Priv[31].toInt().or(64).toByte()

        // 2. Convert Peer Ed25519 Public Key to X25519 Public Key
        // Formula: u = (1 + y) / (1 - y) mod p
        val peerX25519Pub = convertEd25519ToX25519(peerEdPubKey)

        // 3. Calculate X25519 Shared Secret
        val sharedSecret = ByteArray(32)
        org.bouncycastle.math.ec.rfc7748.X25519.scalarMult(myX25519Priv, 0, peerX25519Pub, 0, sharedSecret, 0)

        // 4. Run through HKDF to get final AES key
        // Salt: 32 bytes of zeros (explicit)
        // Info: "MESH_SESSION_V1" (explicit versioning)
        val salt = ByteArray(32) // Defaults to zeros
        val info = "MESH_SESSION_V1".toByteArray(StandardCharsets.UTF_8)
        
        val sessionKey = hkdf(sharedSecret, salt, info)

        // Debug logging
        Log.d("CRYPTO_DEBUG", "My Priv: ${bytesToHex(myX25519Priv).take(8)}...")
        Log.d("CRYPTO_DEBUG", "Peer Pub: ${bytesToHex(peerX25519Pub).take(8)}...")
        Log.d("CRYPTO_DEBUG", "Shared Secret: ${bytesToHex(sharedSecret).take(8)}...")
        Log.d("CRYPTO_DEBUG", "Session Key: ${bytesToHex(sessionKey).take(8)}...")

        return sessionKey
    }

    private fun hkdf(inputKeyMaterial: ByteArray, salt: ByteArray, info: ByteArray): ByteArray {
        val hkdf = HKDFBytesGenerator(SHA256Digest())
        hkdf.init(HKDFParameters(inputKeyMaterial, salt, info))
        val output = ByteArray(32)
        hkdf.generateBytes(output, 0, 32)
        return output
    }

    private fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun convertEd25519ToX25519(edPubKey: ByteArray): ByteArray {
        // Ed25519 pub is the Y coordinate (31 bytes) + sign bit of X (1 bit)
        // We only need Y for the Montgomery curve (X25519)
        val yBytes = edPubKey.copyOf(32)
        yBytes[31] = yBytes[31].toInt().and(0x7F).toByte() // Clear sign bit
        
        val y = bytesToBigInt(yBytes)
        val p = java.math.BigInteger.valueOf(2).pow(255).subtract(java.math.BigInteger.valueOf(19))
        val one = java.math.BigInteger.ONE
        
        // u = (1 + y) * inv(1 - y) mod p
        val num = one.add(y).mod(p)
        val den = one.subtract(y).mod(p)
        val u = num.multiply(den.modInverse(p)).mod(p)
        
        return bigIntTo32Bytes(u)
    }

    private fun bytesToBigInt(bytes: ByteArray): java.math.BigInteger {
        val reversed = bytes.reversedArray() // Little-endian to Big-endian
        return java.math.BigInteger(1, reversed)
    }

    private fun bigIntTo32Bytes(n: java.math.BigInteger): ByteArray {
        val bytes = n.toByteArray()
        val result = ByteArray(32)
        val len = Math.min(bytes.size, 32)
        for (i in 0 until len) {
            result[i] = bytes[bytes.size - 1 - i] // Big-endian to Little-endian
        }
        return result
    }

    private fun decodeBase58(input: String): ByteArray {
        return com.mesh.client.utils.Utils.decodeBase58(input)
    }
}
