package com.platos.domain.hash

/**
 * SHA-256 em Kotlin puro (FIPS 180-4).
 *
 * **Por que escrito aqui, e nao tomado de uma biblioteca.** O `content_hash` do `ExamPackage`
 * precisa ser calculavel nos tres alvos: o servidor o produz, e o dispositivo que recebe o pacote
 * precisa poder verifica-lo. `java.security.MessageDigest` nao existe em Kotlin/JS, e trazer uma
 * dependencia multiplataforma de criptografia exigiria justificativa em `design.md` por uma funcao
 * de 100 linhas cujo resultado e fixado por norma. A base ja tem precedente: `QrEncoder` e
 * `FontProgram` sao implementacoes proprias pelo mesmo motivo — o valor precisa ser identico nos
 * tres alvos, e a unica forma de garantir isso e haver um caminho so.
 *
 * A correcao nao depende de confianca nesta implementacao: `Sha256Test` confere contra os vetores
 * publicados da norma, que sao oracle independente de qualquer codigo deste repositorio.
 *
 * Aritmetica em `Int` de 32 bits com estouro, que e exatamente o que a norma especifica — em
 * Kotlin/JS o `Int` tem a mesma semantica, entao o resultado nao depende do alvo.
 */
object Sha256 {

    fun hex(bytes: ByteArray): String {
        val digest = digest(bytes)
        return buildString(digest.size * 2) {
            for (byte in digest) {
                val value = byte.toInt() and 0xFF
                append(HEX[value ushr 4])
                append(HEX[value and 0x0F])
            }
        }
    }

    fun hex(text: String): String = hex(text.encodeToByteArray())

    fun digest(message: ByteArray): ByteArray {
        // Preenchimento: um bit 1, zeros ate 56 mod 64, e o comprimento em bits como big-endian.
        val bitLength = message.size.toLong() * 8
        val padded = ByteArray(((message.size + 8) / 64 + 1) * 64)
        message.copyInto(padded)
        padded[message.size] = 0x80.toByte()
        for (i in 0 until 8) {
            padded[padded.size - 1 - i] = ((bitLength ushr (8 * i)) and 0xFF).toByte()
        }

        val h = INITIAL.copyOf()
        val w = IntArray(64)

        var offset = 0
        while (offset < padded.size) {
            for (i in 0 until 16) {
                val at = offset + i * 4
                w[i] = ((padded[at].toInt() and 0xFF) shl 24) or
                    ((padded[at + 1].toInt() and 0xFF) shl 16) or
                    ((padded[at + 2].toInt() and 0xFF) shl 8) or
                    (padded[at + 3].toInt() and 0xFF)
            }
            for (i in 16 until 64) {
                val s0 = rotr(w[i - 15], 7) xor rotr(w[i - 15], 18) xor (w[i - 15] ushr 3)
                val s1 = rotr(w[i - 2], 17) xor rotr(w[i - 2], 19) xor (w[i - 2] ushr 10)
                w[i] = w[i - 16] + s0 + w[i - 7] + s1
            }

            var a = h[0]; var b = h[1]; var c = h[2]; var d = h[3]
            var e = h[4]; var f = h[5]; var g = h[6]; var hh = h[7]

            for (i in 0 until 64) {
                val s1 = rotr(e, 6) xor rotr(e, 11) xor rotr(e, 25)
                val ch = (e and f) xor (e.inv() and g)
                val temp1 = hh + s1 + ch + K[i] + w[i]
                val s0 = rotr(a, 2) xor rotr(a, 13) xor rotr(a, 22)
                val maj = (a and b) xor (a and c) xor (b and c)
                val temp2 = s0 + maj

                hh = g; g = f; f = e; e = d + temp1
                d = c; c = b; b = a; a = temp1 + temp2
            }

            h[0] += a; h[1] += b; h[2] += c; h[3] += d
            h[4] += e; h[5] += f; h[6] += g; h[7] += hh
            offset += 64
        }

        val out = ByteArray(32)
        for (i in 0 until 8) {
            out[i * 4] = (h[i] ushr 24).toByte()
            out[i * 4 + 1] = (h[i] ushr 16).toByte()
            out[i * 4 + 2] = (h[i] ushr 8).toByte()
            out[i * 4 + 3] = h[i].toByte()
        }
        return out
    }

    private fun rotr(value: Int, bits: Int): Int = (value ushr bits) or (value shl (32 - bits))

    private const val HEX = "0123456789abcdef"

    private val INITIAL = intArrayOf(
        0x6a09e667, -0x4498517b, 0x3c6ef372, -0x5ab00ac6,
        0x510e527f, -0x64fa9774, 0x1f83d9ab, 0x5be0cd19,
    )

    private val K = intArrayOf(
        0x428a2f98, 0x71374491, -0x4a3f0431, -0x164a245b,
        0x3956c25b, 0x59f111f1, -0x6dc07d5c, -0x54e3a12b,
        -0x27f85568, 0x12835b01, 0x243185be, 0x550c7dc3,
        0x72be5d74, -0x7f214e02, -0x6423f959, -0x3e640e8c,
        -0x1b64963f, -0x1041b87a, 0x0fc19dc6, 0x240ca1cc,
        0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
        -0x67c1aeae, -0x57ce3993, -0x4ffcd838, -0x40a68039,
        -0x391ff40d, -0x2a586eb9, 0x06ca6351, 0x14292967,
        0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13,
        0x650a7354, 0x766a0abb, -0x7e3d36d2, -0x6d8dd37b,
        -0x5d40175f, -0x57e599b5, -0x3db47490, -0x3893ae5d,
        -0x2e6d17e7, -0x2966f9dc, -0xbf1ca7b, 0x106aa070,
        0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5,
        0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
        0x748f82ee, 0x78a5636f, -0x7b3787ec, -0x7338fdf8,
        -0x6f410006, -0x5baf9315, -0x41065c09, -0x398e870e,
    )
}
