package com.platos.domain.layout

import com.platos.domain.exam.ExamDefinition
import com.platos.domain.fixtures.Fixtures
import com.platos.domain.geometry.Um
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Cobre `LayoutProfile` (D-1.6.5) e a garantia de D-1.6.6.
 *
 * Esta fatia faz **duas** mudancas grandes na mesma medicao: o perfil e a formula em linha. Sem
 * separar as duas, regravar o golden vira ato de fe — nao daria para dizer qual delas mudou o que.
 * O teste do perfil padrao existe para fixar uma das metades em zero.
 */
class LayoutProfileTest {

    private val exam: ExamDefinition = Json.decodeFromString(
        ExamDefinition.serializer(),
        Fixtures.PROVA_REFERENCIA_JSON,
    )

    // --- D-1.6.6: o perfil padrão não muda nada ---

    @Test
    fun `perfil padrao produz exatamente o mapa de antes de o perfil existir`() {
        val semPerfil = LayoutEngine().layout(exam).toCanonicalJson()
        val comPadrao = LayoutEngine(profile = LayoutProfile.DEFAULT).layout(exam).toCanonicalJson()

        assertEquals(comPadrao, semPerfil, "declarar o perfil padrao nao pode mudar o mapa")
        assertEquals(
            Fixtures.PROVA_REFERENCIA_LAYOUT_JSON.trim(),
            comPadrao,
            "o perfil padrao precisa reproduzir o golden byte a byte; se este teste falhar, a " +
                "regravacao do golden nesta fatia esta misturando duas causas",
        )
    }

    @Test
    fun `o perfil padrao reproduz a geometria que o Sheet fixava`() {
        // Os numeros de §7, escritos a mao. Se alguem "ajustar" o perfil padrao, isto cai — e o
        // golden sozinho nao diria qual constante mudou.
        val p = LayoutProfile.DEFAULT
        assertEquals(Um.mm(210), p.pageWidth)
        assertEquals(Um.mm(297), p.pageHeight)
        assertEquals(Um.mm(14), p.marginTop)
        assertEquals(Um.mm(15), p.marginBottom)
        assertEquals(Um.mm(15), p.marginSide)
        assertEquals(Um.mm(8), p.stapleReserve)
        assertEquals(Um.mm(6), p.gutter)
        assertEquals(2, p.columns)
        assertEquals(Um.mm(3), p.grid)
        assertEquals(Um.mm(180), p.contentWidth)
        assertEquals(Um.mm(87), p.columnWidth)
        assertEquals(Um.mm(268), p.contentHeight)
        assertEquals(Um.mm(15), p.columnLeft(0))
        assertEquals(Um.mm(108), p.columnLeft(1))
    }

    // --- D-1.6.5: o perfil precisa realmente mandar ---

    @Test
    fun `perfil com corpo maior muda quebras e alturas`() {
        val ampliado = LayoutProfile.DEFAULT.copy(
            id = "a4-2col-ampliado",
            style = LayoutProfile.DEFAULT.style.copy(
                size = LayoutProfile.DEFAULT.style.size * 2,
                lineHeight = LayoutProfile.DEFAULT.style.lineHeight * 2,
            ),
            inlineHeightCeiling = LayoutProfile.DEFAULT.style.lineHeight * 4,
        )

        val padrao = LayoutEngine().layout(exam)
        val maior = LayoutEngine(profile = ampliado).layout(exam)

        assertNotEquals(
            padrao.toCanonicalJson(),
            maior.toCanonicalJson(),
            "um perfil de corpo maior que nao muda o mapa e parametrizacao decorativa",
        )
        assertTrue(
            maior.pages.size > padrao.pages.size,
            "corpo dobrado precisa ocupar mais paginas; padrao ${padrao.pages.size}, " +
                "ampliado ${maior.pages.size}",
        )
    }

    @Test
    fun `perfil nao mexe na geometria de captura`() {
        // ADR-0001 dimensionou bolha e marcador contra reescala de impressora. O perfil muda a
        // folha e NAO pode mexer neles — e o que separa D-1.6.5 de parametrizar tudo.
        val ampliado = LayoutProfile.DEFAULT.copy(
            id = "a4-2col-ampliado",
            style = LayoutProfile.DEFAULT.style.copy(
                size = LayoutProfile.DEFAULT.style.size * 2,
                lineHeight = LayoutProfile.DEFAULT.style.lineHeight * 2,
            ),
            inlineHeightCeiling = LayoutProfile.DEFAULT.style.lineHeight * 4,
        )

        val padrao = LayoutEngine().layout(exam).regions.first()
        val maior = LayoutEngine(profile = ampliado).layout(exam).regions.first()

        assertEquals(padrao.quadWidth, maior.quadWidth)
        assertEquals(padrao.quadHeight, maior.quadHeight)
        assertEquals(padrao.bubbles.size, maior.bubbles.size)
        assertEquals(padrao.bubbles.map { it.u to it.v }, maior.bubbles.map { it.u to it.v })
    }

    @Test
    fun `perfil com coluna mais estreita muda a largura util do texto`() {
        val estreito = LayoutProfile.DEFAULT.copy(id = "estreito", gutter = Um.mm(20))
        assertTrue(
            QuestionBlockBuilder.textWidth(estreito) < QuestionBlockBuilder.textWidth(LayoutProfile.DEFAULT),
        )
    }

    // --- ADR-0004: o perfil viaja declarado no mapa ---

    @Test
    fun `o mapa declara o perfil que o produziu`() {
        val mapa = LayoutEngine().layout(exam)
        assertEquals(LayoutProfile.DEFAULT.id, mapa.profile.id)
        assertEquals(LayoutProfile.DEFAULT.style.size.raw, mapa.profile.bodySize)
        assertEquals(LayoutProfile.DEFAULT.style.lineHeight.raw, mapa.profile.lineHeight)
        assertEquals(LayoutProfile.DEFAULT.grid.raw, mapa.profile.grid)
    }

    @Test
    fun `perfis diferentes ficam distinguiveis pelo cabecalho`() {
        // O ponto do ADR-0004: distinguir sem precisar inferir a partir da geometria. Um mapa
        // publicado e hasheado precisa dizer sob que tipografia foi calculado, senao reconstitui-lo
        // depois vira adivinhacao.
        val ampliado = LayoutProfile.DEFAULT.copy(
            id = "a4-2col-ampliado",
            style = LayoutProfile.DEFAULT.style.copy(
                size = LayoutProfile.DEFAULT.style.size * 2,
                lineHeight = LayoutProfile.DEFAULT.style.lineHeight * 2,
            ),
            inlineHeightCeiling = LayoutProfile.DEFAULT.style.lineHeight * 4,
        )
        val padrao = LayoutEngine().layout(exam).profile
        val maior = LayoutEngine(profile = ampliado).layout(exam).profile

        assertNotEquals(padrao.id, maior.id)
        assertNotEquals(padrao.bodySize, maior.bodySize)
        assertEquals(padrao.grid, maior.grid, "so a tipografia mudou; a grade e a mesma")
    }

    @Test
    fun `o identificador sozinho nao bastaria`() {
        // Dois perfis podem coincidir no identificador por descuido de quem os cria e diferir no
        // resto; e podem coincidir no corpo e diferir na grade. Por isso o cabecalho traz os dois.
        val mesmaIdOutraGrade = LayoutProfile.DEFAULT.copy(grid = Um.mm(5))
        val a = LayoutEngine().layout(exam).profile
        val b = LayoutEngine(profile = mesmaIdOutraGrade).layout(exam).profile
        assertEquals(a.id, b.id)
        assertNotEquals(a.grid, b.grid, "identificador igual, geometria diferente: os valores denunciam")
    }

    // --- guardas do próprio perfil ---

    @Test
    fun `perfil incoerente e recusado na construcao`() {
        // Grampo maior que a margem superior desenharia dentro da faixa de exclusao.
        assertFailsWith<IllegalArgumentException> {
            LayoutProfile.DEFAULT.copy(stapleReserve = Um.mm(20))
        }
        // Teto menor que a entrelinha recusaria ate texto comum.
        assertFailsWith<IllegalArgumentException> {
            LayoutProfile.DEFAULT.copy(inlineHeightCeiling = Um.mm(1))
        }
        assertFailsWith<IllegalArgumentException> { LayoutProfile.DEFAULT.copy(columns = 0) }
        assertFailsWith<IllegalArgumentException> { LayoutProfile.DEFAULT.copy(grid = Um.ZERO) }
    }

    @Test
    fun `teto de linha padrao sao duas entrelinhas`() {
        assertEquals(
            LayoutProfile.DEFAULT.style.lineHeight * 2,
            LayoutProfile.DEFAULT.inlineHeightCeiling,
        )
    }
}
