package com.platos.domain.layout

import com.platos.domain.geometry.Um

/** O layout nao pode ser produzido para a entrada dada. Nenhum mapa parcial e emitido. */
class LayoutException(message: String) : IllegalStateException(message)

/**
 * Um bloco indivisivel: enunciado e alternativas de uma questao andam juntos e nunca sao partidos
 * entre paginas (D34).
 *
 * A altura ja chega alinhada a grade — ver [snapToGrid].
 */
data class Block(
    val id: String,
    val height: Um,
) {
    init {
        require(height > Um.ZERO) { "bloco `$id` sem altura" }
    }
}

/** Onde um bloco foi colocado. */
data class Placement(
    val blockId: String,
    val page: Int,
    val column: Int,
    val top: Um,
)

/** Um slot de coluna: uma coluna de uma pagina, com a altura que sobrou para conteudo. */
data class ColumnSlot(
    val page: Int,
    val column: Int,
    val top: Um,
    val capacity: Um,
)

/** Resultado da paginacao. */
data class Pagination(
    val placements: List<Placement>,
    val slots: List<ColumnSlot>,
    val pageCount: Int,
)

/**
 * Distribui blocos indivisiveis por slots de coluna minimizando `Sigma(sobra_do_slot)^2` (D-1.4).
 *
 * Por que o quadrado, e por que por slot: o total de sobra e fixo assim que o numero de slots
 * esta fixo, entao o quadrado nao escolhe *quantas* paginas — ele escolhe *como espalhar* o vazio
 * entre elas, que e exatamente o que §7 pede. E medir por slot, e nao por pagina, evita empatar
 * "uma coluna cheia e uma vazia" com "duas meias colunas", que é visualmente pior.
 *
 * O primeiro slot pode ser mais curto que os demais quando a regiao de gabarito ocupa o topo da
 * pagina 1 — ver [reservedOnFirstPage].
 */
class Paginator(
    private val profile: LayoutProfile = LayoutProfile.DEFAULT,
    private val reservedOnFirstPage: Um = Um.ZERO,
    private val maxPages: Int = 40,
) {

    fun paginate(blocks: List<Block>): Pagination {
        if (blocks.isEmpty()) {
            throw LayoutException("nenhum bloco para paginar")
        }

        val foraDaGrade = blocks.filterNot { it.height.isMultipleOf(profile.grid) }
        if (foraDaGrade.isNotEmpty()) {
            // A grade e do perfil, entao quem confere e quem conhece o perfil. Antes isto vivia
            // no `init` de Block, afirmado contra uma constante global.
            throw LayoutException(
                "altura de bloco fora da grade de ${profile.grid}: " +
                    foraDaGrade.joinToString { "`${it.id}` com ${it.height}" },
            )
        }

        val maxSlots = maxPages * profile.columns
        val capacities = (0 until maxSlots).map { capacityOf(it) }

        val tallest = blocks.maxBy { it.height }
        val largestCapacity = capacities.max()
        if (tallest.height > largestCapacity) {
            throw LayoutException(
                "bloco `${tallest.id}` tem ${tallest.height} e nao cabe na area util de uma " +
                    "coluna, que e $largestCapacity",
            )
        }

        val best = solve(blocks, capacities) ?: throw LayoutException(
            "os ${blocks.size} blocos nao cabem em $maxPages paginas",
        )

        return materialize(blocks, best, capacities)
    }

    /**
     * Programacao dinamica: `cost[i][s]` e o menor custo de colocar os `i` primeiros blocos em
     * exatamente `s` slots. Com N <= 60 e poucos slots isso e O(N^2 * S) e roda em milissegundos.
     */
    private fun solve(blocks: List<Block>, capacities: List<Um>): IntArray? {
        val n = blocks.size
        val maxSlots = capacities.size
        val infinity = Long.MAX_VALUE / 4

        val cost = Array(n + 1) { LongArray(maxSlots + 1) { infinity } }
        val from = Array(n + 1) { IntArray(maxSlots + 1) { -1 } }
        cost[0][0] = 0

        for (slot in 1..maxSlots) {
            val capacity = capacities[slot - 1].raw.toLong()
            for (end in 0..n) {
                var used = 0L
                // `start` anda para tras: os blocos `start until end` vao para este slot.
                for (start in end downTo 0) {
                    if (start < end) {
                        used += blocks[start].height.raw.toLong()
                        if (used > capacity) break
                    }
                    val previous = cost[start][slot - 1]
                    if (previous >= infinity) continue
                    // Slot vazio so e aceitavel quando ainda nao ha nada colocado; caso contrario
                    // deixar uma coluna em branco no meio da prova seria uma solucao valida.
                    if (start == end && start != 0) continue
                    val slack = capacity - used
                    val candidate = previous + slack * slack
                    if (candidate < cost[end][slot]) {
                        cost[end][slot] = candidate
                        from[end][slot] = start
                    }
                }
            }
        }

        var bestSlots = -1
        var bestCost = infinity
        for (slot in 1..maxSlots) {
            if (cost[n][slot] < bestCost) {
                bestCost = cost[n][slot]
                bestSlots = slot
            }
        }
        if (bestSlots < 0) return null

        // Reconstroi quantos blocos ficaram em cada slot.
        val boundaries = IntArray(bestSlots + 1)
        boundaries[bestSlots] = n
        var end = n
        for (slot in bestSlots downTo 1) {
            val start = from[end][slot]
            if (start < 0) return null
            boundaries[slot - 1] = start
            end = start
        }
        return boundaries
    }

    private fun materialize(
        blocks: List<Block>,
        boundaries: IntArray,
        capacities: List<Um>,
    ): Pagination {
        val placements = mutableListOf<Placement>()
        val slots = mutableListOf<ColumnSlot>()
        val slotCount = boundaries.size - 1

        for (slotIndex in 0 until slotCount) {
            val page = slotIndex / profile.columns
            val column = slotIndex % profile.columns
            val slotTop = topOf(slotIndex)
            slots += ColumnSlot(
                page = page,
                column = column,
                top = slotTop,
                capacity = capacities[slotIndex],
            )

            var cursor = slotTop
            for (blockIndex in boundaries[slotIndex] until boundaries[slotIndex + 1]) {
                val block = blocks[blockIndex]
                placements += Placement(
                    blockId = block.id,
                    page = page,
                    column = column,
                    top = cursor,
                )
                cursor += block.height
            }
        }

        return Pagination(
            placements = placements,
            slots = slots,
            pageCount = (slotCount + profile.columns - 1) / profile.columns,
        )
    }

    private fun capacityOf(slotIndex: Int): Um {
        val reserved = if (slotIndex < profile.columns) reservedOnFirstPage else Um.ZERO
        // A capacidade tambem desce ate a grade: um resto de 1 mm no fim da coluna nao pode
        // deslocar o proximo bloco para fora do ritmo vertical.
        val usable = profile.contentHeight - reserved
        return usable.divFloor(profile.grid.raw) * profile.grid.raw
    }

    private fun topOf(slotIndex: Int): Um =
        if (slotIndex < profile.columns) profile.marginTop + reservedOnFirstPage
        else profile.marginTop
}
