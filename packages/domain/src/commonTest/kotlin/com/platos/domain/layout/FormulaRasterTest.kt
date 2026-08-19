package com.platos.domain.layout

import com.platos.domain.exam.ExamDefinition
import com.platos.domain.fixtures.FormulaRasters
import com.platos.domain.fixtures.Fixtures
import com.platos.domain.text.TextStyle
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Amarra a conversao em `tools/math` ao dominio, nos tres alvos (D-1.5.5).
 *
 * O ponto e o de sempre nesta base: **os dois renderizadores precisam desenhar os mesmos bytes**.
 * Enquanto o web ler do disco e o Android ler de assets, a igualdade so existe se alguem afirmar
 * que os dois caminhos levam ao mesmo arquivo. E isto aqui.
 */
class FormulaRasterTest {

    private val manifest: JsonObject =
        Json.parseToJsonElement(Fixtures.FORMULAS_MANIFEST_JSON) as JsonObject

    private val exam: ExamDefinition = Json.decodeFromString(
        ExamDefinition.serializer(),
        Fixtures.PROVA_REFERENCIA_JSON,
    )

    private val formulas = manifest["formulas"]!!.jsonArray.map { it as JsonObject }

    private fun texto(entry: JsonObject, field: String) = entry[field]!!.jsonPrimitive.content
    private fun inteiro(entry: JsonObject, field: String) = entry[field]!!.jsonPrimitive.content.toInt()

    /**
     * A formula e composta no mesmo corpo do texto ao redor.
     *
     * A conversao roda em Node e nao tem como ler `TextStyle`, entao ela **declara** o corpo que
     * usou e este teste confere. Sem isto, mudar o corpo do texto de um lado so faria a matematica
     * sair com peso optico diferente do enunciado — visivel na folha, invisivel na suite.
     */
    @Test
    fun `a conversao usou o corpo do texto da prova`() {
        assertEquals(
            TextStyle.BODY_SIZE.raw,
            manifest["body_size_um"]!!.jsonPrimitive.content.toInt(),
            "a conversao compos a formula num corpo diferente do texto da prova",
        )
    }

    @Test
    fun `o raster e derivado a 600 dpi`() {
        assertEquals(600, manifest["raster_dpi"]!!.jsonPrimitive.content.toInt())
    }

    /** Os bytes embutidos sao os do PNG versionado — mesma constatacao nos tres alvos. */
    @Test
    fun `os bytes embutidos sao os que o manifesto declara`() {
        assertTrue(formulas.isNotEmpty(), "o manifesto nao descreve formula nenhuma")

        for (entry in formulas) {
            val id = texto(entry, "id")
            val bytes = assertNotNull(
                FormulaRasters.bytesOf(id),
                "o raster de `$id` nao foi embutido para este alvo",
            )
            assertTrue(bytes.isNotEmpty(), "o raster de `$id` foi embutido vazio")
            assertEquals(
                texto(entry, "sha256"),
                FormulaRasters.sha256Of(id),
                "o raster embutido de `$id` nao e o que o manifesto declara",
            )
            // Assinatura PNG: os oito primeiros bytes. Se o transporte corrompesse o binario —
            // por Base64, por encoding de texto —, ela seria a primeira coisa a cair.
            val assinatura = byteArrayOf(
                0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            )
            assertTrue(
                bytes.size > 8 && bytes.copyOfRange(0, 8).contentEquals(assinatura),
                "o raster de `$id` nao chegou como PNG neste alvo",
            )
        }
    }

    @Test
    fun `todo raster versionado esta embutido e vice-versa`() {
        assertEquals(
            formulas.map { texto(it, "id") }.sorted(),
            FormulaRasters.ids.sorted(),
            "manifesto e rasters embutidos descrevem conjuntos diferentes",
        )
    }

    /**
     * As dimensoes que a prova declara sao as que a conversao mediu.
     *
     * Sao dois arquivos versionados em separado, e e `tools/math/build.mjs` que preenche o segundo
     * a partir do primeiro. Uma edicao a mao em qualquer um dos dois cai aqui, em vez de deslocar
     * a caixa da formula na folha impressa.
     */
    @Test
    fun `as dimensoes da prova batem com o manifesto`() {
        val porId = formulas.associateBy { texto(it, "id") }
        val comFormula = exam.questions.mapNotNull { it.formula }
        assertTrue(comFormula.isNotEmpty(), "a fixture nao exercita formula")

        for (formula in comFormula) {
            val declarada = assertNotNull(
                porId[formula.reference],
                "a prova referencia `${formula.reference}`, ausente do manifesto",
            )
            assertEquals(inteiro(declarada, "width_um"), formula.width, formula.reference)
            assertEquals(inteiro(declarada, "height_um"), formula.height, formula.reference)
        }
    }

    /** A caixa declarada tem de ser exatamente o retangulo do raster a 600 dpi. */
    @Test
    fun `a dimensao declarada corresponde ao numero de pixels`() {
        for (entry in formulas) {
            val id = texto(entry, "id")
            for ((eixo, pixels, micrometros) in listOf(
                Triple("largura", inteiro(entry, "width_px"), inteiro(entry, "width_um")),
                Triple("altura", inteiro(entry, "height_px"), inteiro(entry, "height_um")),
            )) {
                // 25400 um por polegada, 600 pixels por polegada, arredondado ao micrometro.
                val esperado = (pixels.toLong() * 25_400 + 300) / 600
                assertEquals(
                    esperado.toInt(),
                    micrometros,
                    "a $eixo declarada de `$id` nao e a do raster a 600 dpi",
                )
            }
        }
    }

    /** Nenhuma formula da fixture pode exceder a coluna, senao o golden nem existiria. */
    @Test
    fun `toda formula da fixture cabe na coluna`() {
        for (entry in formulas) {
            val largura = inteiro(entry, "width_um")
            assertTrue(
                largura <= QuestionBlockBuilder.TEXT_WIDTH.raw,
                "`${texto(entry, "id")}` tem $largura um e a coluna oferece " +
                    "${QuestionBlockBuilder.TEXT_WIDTH.raw} um",
            )
        }
    }
}
