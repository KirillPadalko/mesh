package com.mesh.client.utils

object Utils {
    private const val ALPHABET_STRING = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    private val ALPHABET = ALPHABET_STRING.toCharArray()
    private val ENCODED_ZERO = ALPHABET[0]
    private val INDEXES = IntArray(128) { -1 }

    init {
        for (i in ALPHABET.indices) {
            INDEXES[ALPHABET[i].code] = i
        }
    }

    fun encodeBase58(input: ByteArray): String {
        if (input.isEmpty()) return ""
        var inputCopy = input.copyOf(input.size)
        var zeros = 0
        while (zeros < inputCopy.size && inputCopy[zeros] == 0.toByte()) {
            zeros++
        }
        val encoded = CharArray(inputCopy.size * 2)
        var outputStart = encoded.size
        var start = zeros
        while (start < inputCopy.size) {
            val decoded = inputCopy
            var carry = 0
            var i = start
            while (i < decoded.size) {
                var current = decoded[i].toInt() and 0xFF
                current += carry * 256
                decoded[i] = (current / 58).toByte()
                carry = current % 58
                i++
            }
            encoded[--outputStart] = ALPHABET[carry]
            if (inputCopy[start] == 0.toByte()) {
                start++
            }
        }
        while (outputStart < encoded.size && encoded[outputStart] == ENCODED_ZERO) {
            outputStart++
        }
        while (zeros > 0) {
            encoded[--outputStart] = ENCODED_ZERO
            zeros--
        }
        return String(encoded, outputStart, encoded.size - outputStart)
    }

    fun decodeBase58(input: String): ByteArray {
        if (input.isEmpty()) return ByteArray(0)
        
        // Convert to base-58 digits
        val input58 = ByteArray(input.length)
        for (i in input.indices) {
            val c = input[i]
            val digit = if (c.code < 128) INDEXES[c.code] else -1
            if (digit < 0) throw IllegalArgumentException("Invalid Base58 character: $c")
            input58[i] = digit.toByte()
        }

        // Count leading zeros
        var zeros = 0
        while (zeros < input58.size && input58[zeros] == 0.toByte()) {
            zeros++
        }

        // Decode
        val decoded = ByteArray(input.length)
        var outputStart = decoded.size
        
        var inputStart = zeros
        while (inputStart < input58.size) {
            var remainder = 0
            var i = inputStart
            while (i < input58.size) {
                val digit = input58[i].toInt() and 0xFF
                val temp = remainder * 58 + digit
                input58[i] = (temp / 256).toByte()
                remainder = temp % 256
                i++
            }
            if (outputStart == 0) {
                 // Should not happen as 58 < 256, but safe guard
                 throw IllegalArgumentException("Output buffer too small")
            }
            decoded[--outputStart] = remainder.toByte()
            
            if (input58[inputStart] == 0.toByte()) {
                inputStart++
            }
        }

        // Ignore extra leading zeros in decoded buffer that we didn't use
        while (outputStart < decoded.size && decoded[outputStart] == 0.toByte()) {
            outputStart++
        }

        // Copy result: we want exactly 'zeros' leading zero bytes + the decoded data
        val actualDataLen = decoded.size - outputStart
        val out = ByteArray(zeros + actualDataLen)
        System.arraycopy(decoded, outputStart, out, zeros, actualDataLen)
        return out
    }
}
