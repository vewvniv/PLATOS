package com.platos.domain.capture

/** Matriz de modulos de um QR. `true` e modulo preto. */
data class QrMatrix(
    val size: Int,
    val modules: List<List<Boolean>>,
)

/** O payload nao cabe nas versoes suportadas. */
class QrCapacityException(message: String) : IllegalArgumentException(message)

/**
 * Codificador QR em Kotlin comum: modo byte, correcao nivel M, versoes 1 a 10.
 *
 * Existe pela mesma razao que [ArucoDictionary]: se cada renderizador gerasse o QR com a
 * biblioteca da sua plataforma, duas implementacoes poderiam escolher versao, mascara ou
 * segmentacao diferentes e produzir matrizes distintas para o mesmo texto. O teste de paridade
 * acusaria — corretamente — uma divergencia que nao deveria existir. Aqui a matriz nasce uma vez,
 * no KMP, e viaja dentro do LayoutMap.
 *
 * Nivel M e o meio-termo usual para impressao: ~15% de recuperacao, sem inflar a matriz como o
 * nivel Q faria. As versoes param em 10 porque o payload de uma regiao e curto por construcao
 * (§8) e capacidade nao usada so vira codigo sem teste.
 */
internal object QrEncoder {

    private const val ECC_LEVEL_BITS = 0b00 // nivel M

    /** Por versao: codewords de ECC por bloco, blocos do grupo 1, dados por bloco do grupo 1, idem grupo 2. */
    private val BLOCKS = arrayOf(
        //          ecc  g1  d1  g2  d2
        intArrayOf(10, 1, 16, 0, 0), // 1
        intArrayOf(16, 1, 28, 0, 0), // 2
        intArrayOf(26, 1, 44, 0, 0), // 3
        intArrayOf(18, 2, 32, 0, 0), // 4
        intArrayOf(24, 2, 43, 0, 0), // 5
        intArrayOf(16, 4, 27, 0, 0), // 6
        intArrayOf(18, 4, 31, 0, 0), // 7
        intArrayOf(22, 2, 38, 2, 39), // 8
        intArrayOf(22, 3, 36, 2, 37), // 9
        intArrayOf(26, 4, 43, 1, 44), // 10
    )

    /** Centros dos padroes de alinhamento por versao. A versao 1 nao tem nenhum. */
    private val ALIGNMENT = arrayOf(
        intArrayOf(), // 1
        intArrayOf(6, 18),
        intArrayOf(6, 22),
        intArrayOf(6, 26),
        intArrayOf(6, 30),
        intArrayOf(6, 34),
        intArrayOf(6, 22, 38),
        intArrayOf(6, 24, 42),
        intArrayOf(6, 26, 46),
        intArrayOf(6, 28, 50),
    )

    /** Bits de sobra depois dos codewords, por versao. */
    private fun remainderBits(version: Int): Int = if (version in 2..6) 7 else 0

    fun encode(payload: String): QrMatrix {
        val data = payload.encodeToByteArray().map { it.toInt() and 0xFF }
        val version = versionFor(data.size)
        val size = 17 + 4 * version

        val codewords = buildCodewords(data, version)
        val modules = Array(size) { arrayOfNulls<Boolean>(size) }
        // Modulos funcionais e areas reservadas de formato/versao. Precisa ser uma matriz propria:
        // depois que os dados sao colocados, "ja tem valor" deixa de distinguir dado de funcao, e
        // a mascara acabaria nao sendo aplicada em lugar nenhum.
        val reserved = Array(size) { BooleanArray(size) }

        placeFunctionPatterns(modules, reserved, version, size)
        placeData(modules, reserved, codewords, size, remainderBits(version))

        val (mask, masked) = chooseMask(modules, reserved, size, version)
        placeFormatInfo(masked, size, mask)
        if (version >= 7) placeVersionInfo(masked, size, version)

        return QrMatrix(
            size = size,
            modules = masked.map { row -> row.map { it ?: false } },
        )
    }

    private fun versionFor(byteCount: Int): Int {
        for (version in 1..BLOCKS.size) {
            val header = 4 + if (version >= 10) 16 else 8
            val capacityBits = totalDataCodewords(version) * 8
            if (header + byteCount * 8 <= capacityBits) return version
        }
        throw QrCapacityException(
            "payload de $byteCount bytes excede a versao 10 no nivel M; " +
                "o QR de regiao e curto por construcao",
        )
    }

    private fun totalDataCodewords(version: Int): Int {
        val spec = BLOCKS[version - 1]
        return spec[1] * spec[2] + spec[3] * spec[4]
    }

    // ---------------------------------------------------------------- codewords

    private fun buildCodewords(data: List<Int>, version: Int): List<Int> {
        val spec = BLOCKS[version - 1]
        val eccPerBlock = spec[0]
        val totalData = totalDataCodewords(version)

        val bits = BitBuffer()
        bits.append(0b0100, 4) // modo byte
        bits.append(data.size, if (version >= 10) 16 else 8)
        for (byte in data) bits.append(byte, 8)

        // Terminador de ate 4 bits, depois alinhamento a byte.
        val capacity = totalData * 8
        repeat(minOf(4, capacity - bits.size)) { bits.append(0, 1) }
        while (bits.size % 8 != 0) bits.append(0, 1)

        // Preenchimento alternado prescrito pela norma.
        var pad = 0xEC
        while (bits.size < capacity) {
            bits.append(pad, 8)
            pad = if (pad == 0xEC) 0x11 else 0xEC
        }

        val dataCodewords = bits.toBytes()

        // Divide em blocos e calcula ECC de cada um.
        val blocks = mutableListOf<List<Int>>()
        val eccBlocks = mutableListOf<List<Int>>()
        var offset = 0
        for (group in 0..1) {
            val blockCount = spec[1 + group * 2]
            val dataPerBlock = spec[2 + group * 2]
            repeat(blockCount) {
                val block = dataCodewords.subList(offset, offset + dataPerBlock)
                offset += dataPerBlock
                blocks += block
                eccBlocks += ReedSolomon.encode(block, eccPerBlock)
            }
        }

        // Intercala: primeiro os dados, depois o ECC.
        val result = mutableListOf<Int>()
        val longestData = blocks.maxOf { it.size }
        for (index in 0 until longestData) {
            for (block in blocks) if (index < block.size) result += block[index]
        }
        for (index in 0 until eccPerBlock) {
            for (block in eccBlocks) result += block[index]
        }
        return result
    }

    // ---------------------------------------------------------------- padroes fixos

    private fun placeFunctionPatterns(
        modules: Array<Array<Boolean?>>,
        reserved: Array<BooleanArray>,
        version: Int,
        size: Int,
    ) {
        // Tres localizadores, com separador branco.
        for (corner in listOf(0 to 0, 0 to size - 7, size - 7 to 0)) {
            placeFinder(modules, reserved, corner.first, corner.second, size)
        }

        // Padroes de tempo.
        for (i in 8 until size - 8) {
            val dark = i % 2 == 0
            modules[6][i] = dark
            modules[i][6] = dark
            reserved[6][i] = true
            reserved[i][6] = true
        }

        // Alinhamento, exceto onde colidiria com um localizador.
        val centers = ALIGNMENT[version - 1]
        for (row in centers) {
            for (column in centers) {
                val nearFinder = (row <= 8 && column <= 8) ||
                    (row <= 8 && column >= size - 9) ||
                    (row >= size - 9 && column <= 8)
                if (nearFinder) continue
                for (dr in -2..2) {
                    for (dc in -2..2) {
                        val ring = maxOf(kotlin.math.abs(dr), kotlin.math.abs(dc))
                        modules[row + dr][column + dc] = ring != 1
                        reserved[row + dr][column + dc] = true
                    }
                }
            }
        }

        // Modulo escuro fixo.
        modules[size - 8][8] = true
        reserved[size - 8][8] = true

        // Reserva as areas de formato e de versao para que os dados nao as ocupem.
        for (i in 0..8) {
            reserved[8][i] = true
            reserved[i][8] = true
        }
        for (i in 0..7) {
            reserved[8][size - 1 - i] = true
            reserved[size - 1 - i][8] = true
        }
        if (version >= 7) {
            for (i in 0..5) {
                for (j in 0..2) {
                    reserved[size - 11 + j][i] = true
                    reserved[i][size - 11 + j] = true
                }
            }
        }
    }

    private fun placeFinder(
        modules: Array<Array<Boolean?>>,
        reserved: Array<BooleanArray>,
        row: Int,
        column: Int,
        size: Int,
    ) {
        for (dr in -1..7) {
            for (dc in -1..7) {
                val r = row + dr
                val c = column + dc
                if (r !in 0 until size || c !in 0 until size) continue
                val ring = maxOf(
                    kotlin.math.abs(dr - 3),
                    kotlin.math.abs(dc - 3),
                )
                modules[r][c] = ring != 2 && ring <= 3
                reserved[r][c] = true
            }
        }
    }

    // ---------------------------------------------------------------- dados

    private fun placeData(
        modules: Array<Array<Boolean?>>,
        reserved: Array<BooleanArray>,
        codewords: List<Int>,
        size: Int,
        remainder: Int,
    ) {
        val bits = BitBuffer()
        for (codeword in codewords) bits.append(codeword, 8)
        repeat(remainder) { bits.append(0, 1) }

        var index = 0
        var column = size - 1
        var upward = true
        while (column > 0) {
            if (column == 6) column-- // a coluna de tempo nao carrega dados
            val rows = if (upward) (size - 1) downTo 0 else 0 until size
            for (row in rows) {
                for (offset in 0..1) {
                    val c = column - offset
                    if (reserved[row][c]) continue
                    modules[row][c] = if (index < bits.size) bits[index] else false
                    index++
                }
            }
            upward = !upward
            column -= 2
        }
    }

    // ---------------------------------------------------------------- mascara

    private fun chooseMask(
        modules: Array<Array<Boolean?>>,
        reserved: Array<BooleanArray>,
        size: Int,
        version: Int,
    ): Pair<Int, Array<Array<Boolean?>>> {
        var bestMask = 0
        var bestPenalty = Int.MAX_VALUE
        var best: Array<Array<Boolean?>> = modules

        for (mask in 0..7) {
            val candidate = Array(size) { row -> Array<Boolean?>(size) { modules[row][it] } }
            applyMask(candidate, reserved, size, mask)
            placeFormatInfo(candidate, size, mask)
            if (version >= 7) placeVersionInfo(candidate, size, version)
            val penalty = penaltyOf(candidate, size)
            if (penalty < bestPenalty) {
                bestPenalty = penalty
                bestMask = mask
                best = candidate
            }
        }
        return bestMask to best
    }

    /** Aplica a mascara apenas onde ha dados: [reserved] marca modulo funcional ou reservado. */
    private fun applyMask(
        target: Array<Array<Boolean?>>,
        reserved: Array<BooleanArray>,
        size: Int,
        mask: Int,
    ) {
        for (row in 0 until size) {
            for (column in 0 until size) {
                if (reserved[row][column]) continue
                if (maskAt(mask, row, column)) {
                    target[row][column] = !(target[row][column] ?: false)
                }
            }
        }
    }

    private fun maskAt(mask: Int, row: Int, column: Int): Boolean = when (mask) {
        0 -> (row + column) % 2 == 0
        1 -> row % 2 == 0
        2 -> column % 3 == 0
        3 -> (row + column) % 3 == 0
        4 -> (row / 2 + column / 3) % 2 == 0
        5 -> (row * column) % 2 + (row * column) % 3 == 0
        6 -> ((row * column) % 2 + (row * column) % 3) % 2 == 0
        else -> ((row + column) % 2 + (row * column) % 3) % 2 == 0
    }

    // ---------------------------------------------------------------- formato e versao

    private fun placeFormatInfo(modules: Array<Array<Boolean?>>, size: Int, mask: Int) {
        val data = (ECC_LEVEL_BITS shl 3) or mask
        var bch = data shl 10
        while (highestBit(bch) >= 10) {
            bch = bch xor (0x537 shl (highestBit(bch) - 10))
        }
        val format = ((data shl 10) or bch) xor 0x5412

        for (i in 0..5) modules[8][i] = bitOf(format, 14 - i)
        modules[8][7] = bitOf(format, 8)
        modules[8][8] = bitOf(format, 7)
        modules[7][8] = bitOf(format, 6)
        for (i in 0..5) modules[5 - i][8] = bitOf(format, 5 - i)

        for (i in 0..7) modules[size - 1 - i][8] = bitOf(format, 14 - i)
        for (i in 0..6) modules[8][size - 7 + i] = bitOf(format, 6 - i)
        modules[size - 8][8] = true
    }

    private fun placeVersionInfo(modules: Array<Array<Boolean?>>, size: Int, version: Int) {
        var bch = version shl 12
        while (highestBit(bch) >= 12) {
            bch = bch xor (0x1F25 shl (highestBit(bch) - 12))
        }
        val info = (version shl 12) or bch

        for (i in 0..17) {
            val bit = bitOf(info, i)
            val row = i / 3
            val column = i % 3
            modules[size - 11 + column][row] = bit
            modules[row][size - 11 + column] = bit
        }
    }

    private fun highestBit(value: Int): Int {
        var bit = -1
        var rest = value
        while (rest != 0) {
            bit++
            rest = rest ushr 1
        }
        return bit
    }

    private fun bitOf(value: Int, index: Int): Boolean = (value ushr index) and 1 == 1

    // ---------------------------------------------------------------- penalidade

    private fun penaltyOf(modules: Array<Array<Boolean?>>, size: Int): Int {
        var penalty = 0
        val at = { r: Int, c: Int -> modules[r][c] ?: false }

        // N1: sequencias de 5 ou mais na mesma cor.
        for (line in 0 until size) {
            penalty += runPenalty(size) { at(line, it) }
            penalty += runPenalty(size) { at(it, line) }
        }

        // N2: blocos 2x2 de mesma cor.
        for (row in 0 until size - 1) {
            for (column in 0 until size - 1) {
                val value = at(row, column)
                if (value == at(row, column + 1) &&
                    value == at(row + 1, column) &&
                    value == at(row + 1, column + 1)
                ) {
                    penalty += 3
                }
            }
        }

        // N3: padrao 1:1:3:1:1 com quatro claros de um lado.
        val pattern = booleanArrayOf(true, false, true, true, true, false, true)
        for (line in 0 until size) {
            penalty += finderLikePenalty(size, pattern) { at(line, it) }
            penalty += finderLikePenalty(size, pattern) { at(it, line) }
        }

        // N4: desvio da proporcao de escuros.
        var dark = 0
        for (row in 0 until size) for (column in 0 until size) if (at(row, column)) dark++
        val percent = dark * 100 / (size * size)
        val deviation = kotlin.math.abs(percent - 50) / 5
        penalty += deviation * 10

        return penalty
    }

    private inline fun runPenalty(size: Int, value: (Int) -> Boolean): Int {
        var penalty = 0
        var run = 1
        for (i in 1 until size) {
            if (value(i) == value(i - 1)) {
                run++
            } else {
                if (run >= 5) penalty += 3 + (run - 5)
                run = 1
            }
        }
        if (run >= 5) penalty += 3 + (run - 5)
        return penalty
    }

    private inline fun finderLikePenalty(
        size: Int,
        pattern: BooleanArray,
        value: (Int) -> Boolean,
    ): Int {
        var penalty = 0
        for (start in 0..size - pattern.size) {
            var matches = true
            for (offset in pattern.indices) {
                if (value(start + offset) != pattern[offset]) {
                    matches = false
                    break
                }
            }
            if (!matches) continue
            val beforeClear = (1..4).all { start - it < 0 || !value(start - it) }
            val afterClear = (0..3).all {
                start + pattern.size + it >= size || !value(start + pattern.size + it)
            }
            if (beforeClear || afterClear) penalty += 40
        }
        return penalty
    }
}

/** Acumulador de bits, do mais significativo para o menos. */
private class BitBuffer {
    private val bits = mutableListOf<Boolean>()

    val size: Int get() = bits.size

    operator fun get(index: Int): Boolean = bits[index]

    fun append(value: Int, length: Int) {
        for (position in length - 1 downTo 0) {
            bits += (value ushr position) and 1 == 1
        }
    }

    fun toBytes(): MutableList<Int> {
        val bytes = mutableListOf<Int>()
        for (start in bits.indices step 8) {
            var byte = 0
            for (offset in 0..7) {
                byte = (byte shl 1) or if (bits[start + offset]) 1 else 0
            }
            bytes += byte
        }
        return bytes
    }
}

/** Reed-Solomon sobre GF(256) com polinomio primitivo 0x11D, como a norma do QR exige. */
private object ReedSolomon {

    private val exp = IntArray(512)
    private val log = IntArray(256)

    init {
        var value = 1
        for (i in 0 until 255) {
            exp[i] = value
            log[value] = i
            value = value shl 1
            if (value and 0x100 != 0) value = value xor 0x11D
        }
        for (i in 255 until 512) exp[i] = exp[i - 255]
    }

    private fun multiply(a: Int, b: Int): Int =
        if (a == 0 || b == 0) 0 else exp[log[a] + log[b]]

    private fun generator(degree: Int): IntArray {
        var poly = intArrayOf(1)
        for (i in 0 until degree) {
            val next = IntArray(poly.size + 1)
            for (j in poly.indices) {
                next[j] = next[j] xor multiply(poly[j], 1)
                next[j + 1] = next[j + 1] xor multiply(poly[j], exp[i])
            }
            poly = next
        }
        return poly
    }

    fun encode(data: List<Int>, eccCount: Int): List<Int> {
        val generator = generator(eccCount)
        val remainder = IntArray(eccCount)
        for (byte in data) {
            val factor = byte xor remainder[0]
            for (i in 0 until eccCount - 1) remainder[i] = remainder[i + 1]
            remainder[eccCount - 1] = 0
            for (i in 0 until eccCount) {
                remainder[i] = remainder[i] xor multiply(generator[i + 1], factor)
            }
        }
        return remainder.toList()
    }
}
