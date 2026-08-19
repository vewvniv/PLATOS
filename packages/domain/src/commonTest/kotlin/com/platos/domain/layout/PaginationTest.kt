package com.platos.domain.layout

import com.platos.domain.geometry.Um
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PaginationTest {

    private fun block(id: String, mm: Int) = Block(id, Um.mm(mm))

    @Test
    fun `altura sobe ao proximo multiplo da grade`() {
        assertEquals(Um(3_000), LayoutProfile.DEFAULT.snapToGrid(Um(1)))
        assertEquals(Um(3_000), LayoutProfile.DEFAULT.snapToGrid(Um(3_000)))
        assertEquals(Um(6_000), LayoutProfile.DEFAULT.snapToGrid(Um(3_001)))
    }

    @Test
    fun `bloco sem altura e recusado na construcao`() {
        assertFailsWith<IllegalArgumentException> { Block("q1", Um.ZERO) }
        assertFailsWith<IllegalArgumentException> { Block("q1", Um(-1)) }
    }

    @Test
    fun `bloco fora da grade e recusado ao paginar`() {
        // A verificacao mudou de lugar com o `LayoutProfile` (D-1.6.5), e nao de existencia: a
        // grade passou a ser do perfil, entao quem confere precisa ser quem conhece o perfil.
        // `Block` nao conhece, e afirmava contra uma constante global.
        val erro = assertFailsWith<LayoutException> {
            Paginator().paginate(listOf(Block("q1", Um(3_000)), Block("q2", Um(3_001))))
        }
        assertContains(erro.message!!, "q2")
        assertContains(erro.message!!, "fora da grade")
    }

    @Test
    fun `a grade cobrada ao paginar e a do perfil`() {
        // Um perfil com grade de 5 mm recusa o que a grade de 3 mm aceitava, e vice-versa. Sem
        // isto, a guarda poderia estar afirmando contra 3 mm fixos e ninguem notaria.
        val cincoMm = LayoutProfile.DEFAULT.copy(grid = Um.mm(5))
        assertFailsWith<LayoutException> {
            Paginator(profile = cincoMm).paginate(listOf(Block("q1", Um.mm(6))))
        }
        Paginator(profile = cincoMm).paginate(listOf(Block("q1", Um.mm(10))))
    }

    @Test
    fun `posicoes verticais ficam alinhadas a grade`() {
        val blocos = (1..8).map { block("q$it", 30) }
        val resultado = Paginator().paginate(blocos)

        for (colocacao in resultado.placements) {
            val relativo = colocacao.top - LayoutProfile.DEFAULT.marginTop
            assertTrue(
                relativo.isMultipleOf(LayoutProfile.DEFAULT.grid),
                "bloco ${colocacao.blockId} fora da grade: $relativo",
            )
        }
    }

    @Test
    fun `bloco nunca e partido entre colunas ou paginas`() {
        val blocos = (1..20).map { block("q$it", 33) }
        val resultado = Paginator().paginate(blocos)

        assertEquals(blocos.size, resultado.placements.size)
        assertEquals(blocos.size, resultado.placements.map { it.blockId }.toSet().size)

        // Nenhum bloco ultrapassa o fim util da sua coluna.
        val porSlot = resultado.placements.groupBy { it.page to it.column }
        for ((chave, colocacoes) in porSlot) {
            val slot = resultado.slots.single { it.page to it.column == chave }
            val ultimo = colocacoes.maxBy { it.top }
            val alturaUltimo = blocos.single { it.id == ultimo.blockId }.height
            assertTrue(
                ultimo.top + alturaUltimo <= slot.top + slot.capacity,
                "bloco ${ultimo.blockId} transborda o slot $chave",
            )
        }
    }

    @Test
    fun `sobra e distribuida em vez de empurrada para o fim`() {
        // Nove blocos de 60 mm em colunas de 268 mm: cabem 4 por coluna se empilhados ao maximo,
        // o que deixaria a ultima coluna com um bloco so. A DP prefere espalhar.
        val blocos = (1..9).map { block("q$it", 60) }
        val resultado = Paginator().paginate(blocos)

        val porSlot = resultado.placements.groupingBy { it.page to it.column }.eachCount()
        val ocupacoes = porSlot.values.sorted()
        assertTrue(
            ocupacoes.last() - ocupacoes.first() <= 1,
            "distribuicao desequilibrada entre slots: $ocupacoes",
        )
    }

    @Test
    fun `gabarito no topo encurta os slots da primeira pagina`() {
        val reserva = Um.mm(60)
        // Blocos suficientes para transbordar a pagina 1 e existir pagina 2 com que comparar.
        val resultado = Paginator(reservedOnFirstPage = reserva).paginate(
            (1..30).map { block("q$it", 30) },
        )

        val primeiraPagina = resultado.slots.filter { it.page == 0 }
        val demais = resultado.slots.filter { it.page > 0 }
        assertEquals(LayoutProfile.DEFAULT.columns, primeiraPagina.size)
        assertTrue(demais.isNotEmpty(), "o caso precisa de mais de uma pagina")

        for (slot in primeiraPagina) {
            assertTrue(slot.capacity < demais.first().capacity)
            assertEquals(LayoutProfile.DEFAULT.marginTop + reserva, slot.top)
        }
    }

    @Test
    fun `bloco maior que a area util e recusado com erro identificavel`() {
        val falha = assertFailsWith<LayoutException> {
            Paginator().paginate(listOf(block("gigante", 300)))
        }
        assertTrue(falha.message!!.contains("gigante"), falha.message!!)
        assertTrue(falha.message!!.contains("nao cabe"), falha.message!!)
    }

    @Test
    fun `bloco que so nao cabe por causa da reserva da primeira pagina ainda e paginado`() {
        // Cabe numa coluna normal, mas nao na coluna encurtada da pagina 1.
        // 201 mm sao 67 passos da grade; 200 mm nao seriam multiplo de 3 mm.
        val reserva = Um.mm(201)
        val resultado = Paginator(reservedOnFirstPage = reserva).paginate(listOf(block("q1", 201)))
        assertTrue(resultado.pageCount >= 2)
    }

    @Test
    fun `entrada vazia e recusada`() {
        assertFailsWith<LayoutException> { Paginator().paginate(emptyList()) }
    }

    @Test
    fun `paginacao e estavel entre execucoes`() {
        val blocos = (1..15).map { block("q$it", 42) }
        assertEquals(Paginator().paginate(blocos), Paginator().paginate(blocos))
    }
}
