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
        val input58 = ByteArray(input.length)
        for (i in input.indices) {
            val c = input[i]
            val digit = if (c.code < 128) INDEXES[c.code] else -1
            if (digit < 0) throw IllegalArgumentException("Invalid Base58 character: $c")
            input58[i] = digit.toByte()
        }
        var zeros = 0
        while (zeros < input58.size && input58[zeros] == 0.toByte()) {
            zeros++
        }
        val decoded = ByteArray(input.length)
        var outputStart = decoded.size
        var start = zeros
        while (start < input58.size) {
            val decodedByte = input58
            var carry = 0
            var i = start
            while (i < decodedByte.size) {
                var current = decodedByte[i].toInt() and 0xFF
                current = current * 58 + carry
                decodedByte[i] = (current % 256).toByte()
                carry = current / 256
                i++
            }
            decoded[--outputStart] = carry.toByte()
            if (input58[start] == 0.toByte()) {
                start++
            }
        }
        while (outputStart < decoded.size && decoded[outputStart] == 0.toByte()) {
            outputStart++
        }
        val data = decoded.copyOfRange(outputStart, decoded.size)
        // Add implicit zeros
        val out = ByteArray(zeros + data.size)
        System.arraycopy(data, 0, out, zeros, data.size)
        return out
    }
}
