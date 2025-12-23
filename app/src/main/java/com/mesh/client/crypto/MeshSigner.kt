package com.mesh.client.crypto

import com.mesh.client.identity.IdentityManager
import net.i2p.crypto.eddsa.EdDSAEngine
import net.i2p.crypto.eddsa.EdDSAPublicKey
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable
import net.i2p.crypto.eddsa.spec.EdDSAPublicKeySpec
import java.security.MessageDigest
import java.security.Signature
import java.util.HexFormat

class MeshSigner(private val identityManager: IdentityManager) {

    private val ed25519Spec = EdDSANamedCurveTable.getByName(EdDSANamedCurveTable.ED_25519)

    /**
     * Signs the given data (bytes) using the local identity private key.
     * Returns Hex string of the signature.
     */
    fun sign(data: ByteArray): String {
        val keyPair = identityManager.getIdentityKeyPair()
        val sgr = EdDSAEngine(MessageDigest.getInstance("SHA-512"))
        sgr.initSign(keyPair.private)
        sgr.update(data)
        return toHex(sgr.sign())
    }

    /**
     * Verifies the signature of the data against a public key (Mesh-ID).
     */
    fun verify(data: ByteArray, signatureHex: String, meshId: String): Boolean {
        return try {
            val pubKeyBytes = com.mesh.client.utils.Utils.decodeBase58(meshId)
            val pubKeySpec = EdDSAPublicKeySpec(pubKeyBytes, ed25519Spec)
            val pubKey = EdDSAPublicKey(pubKeySpec)

            val sgr = EdDSAEngine(MessageDigest.getInstance("SHA-512"))
            sgr.initVerify(pubKey)
            sgr.update(data)
            sgr.verify(fromHex(signatureHex))
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * SHA-256 Hash of data. Returns Hex string.
     */
    fun hash(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return toHex(digest.digest(data))
    }

    // Hex helpers (Available in Java 17+, but likely using older JDK or Android, so manual impl if needed)
    // Actually Android might not have HexFormat if minSdk < 34.
    // I'll use a simple helper to be safe for minSdk 24.
    
    private fun toHex(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun fromHex(hex: String): ByteArray {
        check(hex.length % 2 == 0) { "Must have an even length" }
        return hex.chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }
}
