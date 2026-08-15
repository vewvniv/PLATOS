package com.platos.domain.text

/** Falha ao interpretar o programa de fonte. Nunca ha fallback: medir com outra fonte muda a folha. */
class FontFormatException(message: String) : IllegalStateException(message)

/**
 * Leitor big-endian sobre os bytes do TTF.
 *
 * Toda leitura e checada: um TTF truncado precisa falhar com mensagem propria, e nao com
 * `IndexOutOfBounds` vindo de algum ponto arbitrario do parse.
 */
private class FontBytes(val data: ByteArray) {

    fun u8(at: Int): Int {
        require(at)
        return data[at].toInt() and 0xFF
    }

    fun u16(at: Int): Int = (u8(at) shl 8) or u8(at + 1)

    fun i16(at: Int): Int {
        val raw = u16(at)
        return if (raw >= 0x8000) raw - 0x10000 else raw
    }

    fun u32(at: Int): Long =
        (u16(at).toLong() shl 16) or u16(at + 2).toLong()

    fun ascii(at: Int, length: Int): String {
        require(at + length - 1)
        return buildString { for (i in 0 until length) append(u8(at + i).toChar()) }
    }

    /** UTF-16BE, que e como as strings da tabela `name` da plataforma 3 sao codificadas. */
    fun utf16(at: Int, byteLength: Int): String {
        require(at + byteLength - 1)
        return buildString { for (i in 0 until byteLength step 2) append(u16(at + i).toChar()) }
    }

    private fun require(index: Int) {
        if (index < 0 || index >= data.size) {
            throw FontFormatException(
                "leitura fora dos limites do arquivo de fonte: byte $index de ${data.size}",
            )
        }
    }
}

/** Um segmento da tabela `cmap` formato 4. */
private class CmapSegment(
    val startCode: Int,
    val endCode: Int,
    val idDelta: Int,
    val idRangeOffset: Int,
    val idRangeOffsetAt: Int,
)

/**
 * Programa de fonte lido do TTF: o suficiente para medir, e nada alem disso.
 *
 * Le `head` (unitsPerEm), `cmap` formato 4 (caractere -> glifo), `hhea`/`hmtx` (avanco por glifo),
 * `name` (identificacao) e, quando presente, `kern` formato 0 (pares). Nao interpreta contornos:
 * desenhar e problema do renderizador, que recebe o TTF inteiro.
 */
class FontProgram private constructor(
    private val bytes: FontBytes,
    val unitsPerEm: Int,
    val familyName: String,
    val versionName: String,
    private val segments: List<CmapSegment>,
    private val advances: IntArray,
    private val kerningPairs: Map<Long, Int>,
) {

    /** Verdadeiro quando a fonte traz tabela `kern` legada com ao menos um par. */
    val hasKerning: Boolean get() = kerningPairs.isNotEmpty()

    /** Indice do glifo para [codePoint], ou 0 (`.notdef`) quando a fonte nao o cobre. */
    fun glyphOf(codePoint: Int): Int {
        if (codePoint > 0xFFFF) return 0
        var low = 0
        var high = segments.size - 1
        while (low <= high) {
            val mid = (low + high) / 2
            val segment = segments[mid]
            when {
                codePoint > segment.endCode -> low = mid + 1
                codePoint < segment.startCode -> high = mid - 1
                else -> return glyphIn(segment, codePoint)
            }
        }
        return 0
    }

    /** Avanco horizontal do glifo, em unidades de fonte. */
    fun advanceOf(glyph: Int): Int = when {
        advances.isEmpty() -> 0
        glyph < advances.size -> advances[glyph]
        // Fontes monoespacadas na cauda repetem o ultimo avanco de `hmtx`.
        else -> advances[advances.size - 1]
    }

    /** Ajuste de kerning entre dois glifos, em unidades de fonte. Zero quando nao ha par. */
    fun kerningBetween(left: Int, right: Int): Int =
        kerningPairs[(left.toLong() shl 32) or right.toLong()] ?: 0

    private fun glyphIn(segment: CmapSegment, codePoint: Int): Int {
        if (segment.idRangeOffset == 0) {
            return (codePoint + segment.idDelta) and 0xFFFF
        }
        val at = segment.idRangeOffsetAt + segment.idRangeOffset + 2 * (codePoint - segment.startCode)
        val glyph = bytes.u16(at)
        return if (glyph == 0) 0 else (glyph + segment.idDelta) and 0xFFFF
    }

    companion object {
        private const val SFNT_TRUE_TYPE = 0x00010000L
        private const val SFNT_OPEN_TYPE = 0x4F54544FL // 'OTTO'

        fun parse(data: ByteArray): FontProgram {
            if (data.size < 12) {
                throw FontFormatException("arquivo de fonte com ${data.size} bytes e curto demais")
            }
            val bytes = FontBytes(data)
            val sfnt = bytes.u32(0)
            if (sfnt != SFNT_TRUE_TYPE && sfnt != SFNT_OPEN_TYPE) {
                throw FontFormatException("assinatura sfnt desconhecida: 0x${sfnt.toString(16)}")
            }

            val tables = mutableMapOf<String, Int>()
            val tableCount = bytes.u16(4)
            for (i in 0 until tableCount) {
                val record = 12 + i * 16
                tables[bytes.ascii(record, 4)] = bytes.u32(record + 8).toInt()
            }

            val head = tables["head"] ?: throw FontFormatException("tabela `head` ausente")
            val unitsPerEm = bytes.u16(head + 18)
            if (unitsPerEm <= 0) {
                throw FontFormatException("unitsPerEm invalido: $unitsPerEm")
            }

            val hhea = tables["hhea"] ?: throw FontFormatException("tabela `hhea` ausente")
            val hmtx = tables["hmtx"] ?: throw FontFormatException("tabela `hmtx` ausente")
            val metricCount = bytes.u16(hhea + 34)
            if (metricCount <= 0) {
                throw FontFormatException("numberOfHMetrics invalido: $metricCount")
            }
            val advances = IntArray(metricCount) { bytes.u16(hmtx + it * 4) }

            val cmap = tables["cmap"] ?: throw FontFormatException("tabela `cmap` ausente")
            val segments = parseCmapFormat4(bytes, cmap)

            val name = tables["name"]
            val familyName = name?.let { readName(bytes, it, NAME_FAMILY) } ?: ""
            val versionName = name?.let { readName(bytes, it, NAME_VERSION) } ?: ""

            val kerningPairs = tables["kern"]?.let { parseKernFormat0(bytes, it) } ?: emptyMap()

            return FontProgram(
                bytes = bytes,
                unitsPerEm = unitsPerEm,
                familyName = familyName,
                versionName = versionName,
                segments = segments,
                advances = advances,
                kerningPairs = kerningPairs,
            )
        }

        private const val NAME_FAMILY = 1
        private const val NAME_VERSION = 5

        private fun parseCmapFormat4(bytes: FontBytes, cmap: Int): List<CmapSegment> {
            val subtableCount = bytes.u16(cmap + 2)
            var chosen = -1
            for (i in 0 until subtableCount) {
                val record = cmap + 4 + i * 8
                val platformId = bytes.u16(record)
                val encodingId = bytes.u16(record + 2)
                val offset = cmap + bytes.u32(record + 4).toInt()
                // Windows/BMP e Unicode/BMP sao os dois que carregam formato 4.
                val isBmpUnicode = (platformId == 3 && encodingId == 1) || (platformId == 0 && encodingId <= 4)
                if (isBmpUnicode && bytes.u16(offset) == 4) {
                    chosen = offset
                    if (platformId == 3) break
                }
            }
            if (chosen < 0) {
                throw FontFormatException("nenhuma subtabela `cmap` formato 4 para Unicode BMP")
            }

            val segCount = bytes.u16(chosen + 6) / 2
            val endCodes = chosen + 14
            val startCodes = endCodes + segCount * 2 + 2
            val idDeltas = startCodes + segCount * 2
            val idRangeOffsets = idDeltas + segCount * 2

            return (0 until segCount).map { i ->
                CmapSegment(
                    startCode = bytes.u16(startCodes + i * 2),
                    endCode = bytes.u16(endCodes + i * 2),
                    idDelta = bytes.i16(idDeltas + i * 2),
                    idRangeOffset = bytes.u16(idRangeOffsets + i * 2),
                    idRangeOffsetAt = idRangeOffsets + i * 2,
                )
            }
        }

        /**
         * Le a tabela `kern` legada, subtabela formato 0.
         *
         * D-1.3: GPOS nao entra nesta fatia. Quando a fonte so tem GPOS — o caso das duas
         * candidatas avaliadas — o ajuste e zero, identico nos tres alvos. O resultado e
         * deterministico, que e o que o OMR exige; a perda e estetica.
         */
        private fun parseKernFormat0(bytes: FontBytes, kern: Int): Map<Long, Int> {
            val version = bytes.u16(kern)
            if (version != 0) return emptyMap()
            val subtableCount = bytes.u16(kern + 2)
            val pairs = mutableMapOf<Long, Int>()
            var at = kern + 4
            for (i in 0 until subtableCount) {
                val length = bytes.u16(at + 2)
                val coverage = bytes.u16(at + 4)
                val format = coverage shr 8
                val isHorizontal = coverage and 0x1 == 1
                if (format == 0 && isHorizontal) {
                    val pairCount = bytes.u16(at + 6)
                    val firstPair = at + 14
                    for (p in 0 until pairCount) {
                        val record = firstPair + p * 6
                        val left = bytes.u16(record)
                        val right = bytes.u16(record + 2)
                        pairs[(left.toLong() shl 32) or right.toLong()] = bytes.i16(record + 4)
                    }
                }
                if (length <= 0) break
                at += length
            }
            return pairs
        }

        private fun readName(bytes: FontBytes, name: Int, nameId: Int): String {
            val count = bytes.u16(name + 2)
            val storage = name + bytes.u16(name + 4)
            for (i in 0 until count) {
                val record = name + 6 + i * 12
                val platformId = bytes.u16(record)
                val encodingId = bytes.u16(record + 2)
                if (bytes.u16(record + 6) != nameId) continue
                val length = bytes.u16(record + 8)
                val offset = storage + bytes.u16(record + 10)
                if (platformId == 3 && encodingId == 1) return bytes.utf16(offset, length)
                if (platformId == 1 && encodingId == 0) return bytes.ascii(offset, length)
            }
            return ""
        }
    }
}
