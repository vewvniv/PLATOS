package com.platos.domain.layout

import com.platos.domain.exam.ExamDefinition
import com.platos.domain.exam.Question
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LayoutMapValidationTest {

    private val exam = ExamDefinition(
        id = "prova-validacao",
        title = "Prova",
        questions = (1..8).map {
            Question(
                id = "q$it",
                statement = "Enunciado da questao $it com algum texto.",
                options = listOf("a", "b", "c", "d"),
            )
        },
    )

    private val valid = LayoutEngine().layout(exam)

    private fun problemsOf(map: LayoutMap): List<String> {
        val result = map.validate()
        assertTrue(result is ValidationResult.Invalid, "esperava mapa invalido")
        return result.problems
    }

    @Test
    fun `mapa produzido pelo engine e aceito sem efeito colateral`() {
        val antes = valid.toCanonicalJson()
        assertEquals(ValidationResult.Valid, valid.validate())
        assertEquals(antes, valid.toCanonicalJson(), "a validacao alterou o mapa")
    }

    @Test
    fun `identificador de elemento repetido e apontado`() {
        val page = valid.pages.first()
        val duplicated = page.copy(primitives = page.primitives + page.primitives.first())
        val problems = problemsOf(valid.copy(pages = listOf(duplicated) + valid.pages.drop(1)))

        assertTrue(
            problems.any { it.contains("repetido") && it.contains(page.primitives.first().id) },
            problems.toString(),
        )
    }

    @Test
    fun `regioes sobrepostas sao apontadas`() {
        val region = valid.regions.single()
        val overlapping = region.copy(index = 1, markerIds = listOf(4, 5, 6, 7))
        val problems = problemsOf(valid.copy(regions = listOf(region, overlapping)))

        assertTrue(problems.any { it.contains("se sobrepoem") }, problems.toString())
    }

    @Test
    fun `coordenada fora da faixa e apontada`() {
        val region = valid.regions.single()
        val broken = region.copy(
            bubbles = region.bubbles.mapIndexed { index, bubble ->
                if (index == 0) bubble.copy(v = 1_000_001) else bubble
            },
        )
        val problems = problemsOf(valid.copy(regions = listOf(broken)))

        assertTrue(
            problems.any { it.contains("fora do intervalo unitario") && it.contains("1000001") },
            problems.toString(),
        )
    }

    @Test
    fun `versao ausente e apontada`() {
        assertTrue(
            problemsOf(valid.copy(layoutEngineVersion = 0))
                .any { it.contains("layout_engine_version") },
        )
        assertTrue(
            problemsOf(valid.copy(minRendererVersion = 0))
                .any { it.contains("min_renderer_version") },
        )
    }

    @Test
    fun `marcadores fora da convencao 4k sao apontados`() {
        val region = valid.regions.single()
        val problems = problemsOf(
            valid.copy(regions = listOf(region.copy(markerIds = listOf(7, 8, 9, 10)))),
        )
        assertTrue(problems.any { it.contains("deveria usar os marcadores") }, problems.toString())
    }

    @Test
    fun `regiao apontando para pagina inexistente e apontada`() {
        val region = valid.regions.single()
        val problems = problemsOf(valid.copy(regions = listOf(region.copy(page = 99))))
        assertTrue(problems.any { it.contains("que nao existe") }, problems.toString())
    }

    /** Poe uma primitiva na primeira pagina, sem mexer no resto do mapa. */
    private fun comPrimitiva(primitive: Primitive): LayoutMap {
        val page = valid.pages.first()
        return valid.copy(
            pages = listOf(page.copy(primitives = page.primitives + primitive)) +
                valid.pages.drop(1),
        )
    }

    private fun faixa(fill: Int?) = DrawRect(
        id = "faixa-de-teste",
        x = 20_000,
        y = 20_000,
        width = 10_000,
        height = 6_000,
        stroke = 0,
        fill = fill,
    )

    private fun letra(tone: Int?) = DrawText(
        id = "letra-de-teste",
        x = 20_000,
        baseline = 30_000,
        size = 3_000,
        text = "A",
        tone = tone,
    )

    @Test
    fun `trama acima do teto de oito por cento e apontada`() {
        val problems = problemsOf(comPrimitiva(faixa(fill = 200)))
        assertTrue(
            problems.any { it.contains("acima do teto") && it.contains("200") },
            problems.toString(),
        )
    }

    @Test
    fun `trama no teto e aceita`() {
        // O caso positivo ao lado da recusa: sem ele, a guarda poderia estar recusando tudo — e a
        // trama de 45 por mil que a folha usa passa a ser uma afirmacao, e nao uma esperanca.
        assertEquals(ValidationResult.Valid, comPrimitiva(faixa(fill = 80)).validate())
        assertEquals(ValidationResult.Valid, comPrimitiva(faixa(fill = 45)).validate())
        assertEquals(ValidationResult.Valid, comPrimitiva(faixa(fill = null)).validate())
    }

    @Test
    fun `trama fora da faixa de permilagem e apontada`() {
        assertTrue(
            problemsOf(comPrimitiva(faixa(fill = -1))).any { it.contains("fora da faixa") },
        )
        assertTrue(
            problemsOf(comPrimitiva(faixa(fill = 1_001))).any { it.contains("fora da faixa") },
        )
    }

    @Test
    fun `tom de texto fora da faixa de permilagem e apontado`() {
        assertTrue(
            problemsOf(comPrimitiva(letra(tone = 1_001)))
                .any { it.contains("tom") && it.contains("fora da faixa") },
        )
        assertTrue(
            problemsOf(comPrimitiva(letra(tone = -1)))
                .any { it.contains("tom") && it.contains("fora da faixa") },
        )
    }

    @Test
    fun `tom de texto dentro da faixa e aceito, inclusive mais escuro que o teto de trama`() {
        // O teto de 8% e de **trama chapada** (§7). Um glifo cinza nao e trama: a letra dentro do
        // circulo precisa ser legivel, e recusa-la pelo teto da faixa seria aplicar a regra errada.
        assertEquals(ValidationResult.Valid, comPrimitiva(letra(tone = 400)).validate())
        assertEquals(ValidationResult.Valid, comPrimitiva(letra(tone = 0)).validate())
        assertEquals(ValidationResult.Valid, comPrimitiva(letra(tone = 1_000)).validate())
        assertEquals(ValidationResult.Valid, comPrimitiva(letra(tone = null)).validate())
    }

    /** Uma bolha desenhada da folha, para pôr tinta em cima dela. */
    private val bolha = valid.pages[0].primitives.filterIsInstance<DrawCircle>().first()

    @Test
    fun `orcamento de tinta viaja no mapa`() {
        val budget = valid.regions.single().inkBudget
        assertEquals(120, budget.decorativeMax)
        assertEquals(200, budget.thresholdFloor)
        assertEquals(400, budget.thresholdCeiling)
    }

    @Test
    fun `trama dentro da bolha acima do orcamento e recusada`() {
        // Com o orcamento padrao de 120 por mil, o teto de trama chapada de §7 — 80 — e mais
        // apertado e dispara antes. Quem exercita **este** ramo e uma regiao que declara orcamento
        // menor que o teto, que e exatamente o caso em que ele deixa de ser redundante.
        val regiao = valid.regions.single()
        val apertada = regiao.copy(inkBudget = regiao.inkBudget.copy(decorativeMax = 30))
        val comFaixa = comPrimitiva(
            DrawRect(
                id = "faixa-na-bolha",
                x = bolha.centerX - 10_000,
                y = bolha.centerY - 3_000,
                width = 20_000,
                height = 6_000,
                stroke = 0,
                fill = 45,
            ),
        )
        val problems = problemsOf(comFaixa.copy(regions = listOf(apertada)))
        assertTrue(
            problems.any {
                it.contains("orcamento decorativo") && it.contains("faixa-na-bolha")
            },
            problems.toString(),
        )
    }

    @Test
    fun `texto em preto pleno dentro da bolha e recusado`() {
        val problems = problemsOf(
            comPrimitiva(
                DrawText(
                    id = "letra-opaca",
                    x = bolha.centerX - 500,
                    baseline = bolha.centerY + 500,
                    size = 2_000,
                    text = "A",
                    tone = null,
                ),
            ),
        )
        assertTrue(
            problems.any { it.contains("preto pleno dentro dela") && it.contains("letra-opaca") },
            problems.toString(),
        )
    }

    @Test
    fun `tom acima do teto decorativo dentro da bolha e recusado`() {
        val problems = problemsOf(
            comPrimitiva(
                DrawText(
                    id = "letra-escura",
                    x = bolha.centerX - 500,
                    baseline = bolha.centerY + 500,
                    size = 2_000,
                    text = "A",
                    tone = 600,
                ),
            ),
        )
        assertTrue(
            problems.any { it.contains("teto decorativo") && it.contains("letra-escura") },
            problems.toString(),
        )
    }

    @Test
    fun `tinta decorativa longe das bolhas nao e cobrada do orcamento`() {
        // O caso positivo do par: sem ele, a guarda poderia estar recusando qualquer tinta na
        // pagina, e a folha inteira viraria refem do orcamento da regiao.
        assertEquals(
            ValidationResult.Valid,
            comPrimitiva(
                DrawText(
                    id = "texto-longe",
                    x = 20_000,
                    baseline = 280_000,
                    size = 3_000,
                    text = "rodape em preto pleno",
                    tone = null,
                ),
            ).validate(),
        )
    }

    @Test
    fun `orcamento que invade o corredor do limiar e recusado`() {
        val regiao = valid.regions.single()
        val invadindo = regiao.copy(
            inkBudget = regiao.inkBudget.copy(decorativeMax = 250),
        )
        val problems = problemsOf(valid.copy(regions = listOf(invadindo)))
        assertTrue(
            problems.any { it.contains("invade o corredor do limiar") },
            problems.toString(),
        )
    }

    @Test
    fun `corredor invertido e recusado`() {
        val regiao = valid.regions.single()
        val invertido = regiao.copy(
            inkBudget = regiao.inkBudget.copy(thresholdFloor = 400, thresholdCeiling = 200),
        )
        assertTrue(
            problemsOf(valid.copy(regions = listOf(invertido)))
                .any { it.contains("orcamento de tinta incoerente") },
        )
    }

    @Test
    fun `todos os problemas sao reportados de uma vez`() {
        val problems = problemsOf(
            valid.copy(layoutEngineVersion = 0, minRendererVersion = 0, fontSha256 = ""),
        )
        assertTrue(problems.size >= 3, "esperava varios problemas, veio $problems")
    }
}
