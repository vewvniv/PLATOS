package com.platos.android.omr

import com.platos.domain.layout.LayoutMap
import java.io.File
import kotlin.math.abs
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * A medicao sobre **papel real**, conferida contra `papel.mjs`.
 *
 * Esta e a unica evidencia de papel que o repositorio tem: a folha impressa em 2026-08-22 nao
 * existe mais, e o dpi da digitalizacao nunca foi conhecido. O buffer vem de
 * `prova-referencia.recorte.pgm`, que e a regiao ja retificada — §13 poe a retificacao no OpenCV,
 * entao a fixture foi produzida uma vez por `tools/parity/recorte.mjs`, fora do caminho de
 * producao. **Isto nao prova o retificador**; quem prova ele e a tarefa 7.4, no emulador.
 *
 * A independencia contra `papel.mjs` e parcial e vale dizer: as duas implementacoes nao
 * compartilham uma linha — uma e Kotlin, a outra JavaScript —, mas compartilham desenho. Isso
 * pega erro de porte e divergencia numerica; nao pega um erro de concepcao cometido nas duas.
 */
class RealSheetTest {

    private val fixtures = File(
        System.getProperty("platos.fixtures")
            ?: error("propriedade `platos.fixtures` nao definida pelo build"),
    )
    private val map: LayoutMap = Json.decodeFromString(
        File(fixtures, "prova-referencia.layout.json").readText(),
    )
    private val region = map.regions.single()

    private fun crop(): RectifiedRegion {
        val bytes = File(fixtures, "prova-referencia.recorte.pgm").readBytes()
        return Pgm.parse(bytes)
    }

    /** O que `papel.mjs` mediu, em partes por mil, por bolha. */
    private fun reference(): Map<String, Int> {
        val root = Json.parseToJsonElement(
            File(fixtures, "prova-referencia.papel.json").readText(),
        ).jsonObject
        return root["bolhas"]!!.jsonArray.associate { element ->
            val bubble = element.jsonObject
            val coverage = bubble["cobertura"]!!.jsonPrimitive.content.toDouble()
            bubble["id"]!!.jsonPrimitive.content to (coverage * 1_000).toInt()
        }
    }

    @Test
    fun `o recorte versionado tem as dimensoes que a regiao declara`() {
        val image = crop()
        // 10 px/mm sobre 166x85 mm. Se a fixture for regerada noutra escala, este teste diz antes
        // que a comparacao abaixo comece a divergir por motivo errado.
        assertEquals(1_660, image.width)
        assertEquals(850, image.height)
    }

    @Test
    fun `mede as 160 bolhas da folha real`() {
        val outcome = BubbleMeter.measure(map, region, crop())
        val medida = outcome as? MeterOutcome.Measured
            ?: throw AssertionError("esperava medicao, veio $outcome")

        assertEquals(160, medida.measurements.size)
        assertEquals(region.bubbles.size, medida.measurements.size)
    }

    @Test
    fun `a medicao concorda com papel_mjs sobre a mesma folha`() {
        val outcome = BubbleMeter.measure(map, region, crop()) as MeterOutcome.Measured
        val esperado = reference()

        var maior = 0
        var pior = ""
        var soma = 0
        for (medicao in outcome.measurements) {
            val referencia = esperado[medicao.id]
                ?: throw AssertionError("`papel.mjs` nao mediu ${medicao.id}")
            val desvio = abs(medicao.coveragePerMille - referencia)
            soma += desvio
            if (desvio > maior) {
                maior = desvio
                pior = "${medicao.id}: oficial ${medicao.coveragePerMille}, papel.mjs $referencia"
            }
        }

        assertTrue(maior <= TOLERANCIA_PER_MILLE) {
            "divergencia de $maior por mil contra `papel.mjs` — $pior " +
                "(media ${soma / outcome.measurements.size})"
        }
    }

    @Test
    fun `a separacao entre caneta e bolha vazia sobrevive a medicao oficial`() {
        // Nao e reafirmar o numero da fatia 2b: e conferir que **esta** implementacao preserva a
        // propriedade que a fatia 3 vai herdar. Sem separacao larga nao ha onde por o limiar, e o
        // corredor declarado em ADR-0010 e de 200 a 400.
        val outcome = BubbleMeter.measure(map, region, crop()) as MeterOutcome.Measured
        val porQuestao = outcome.measurements.groupBy { it.questionId }

        var maiorVazia = 0
        var menorCaneta = OmrScale.FULL
        for ((_, bolhas) in porQuestao) {
            val ordenadas = bolhas.sortedByDescending { it.coveragePerMille }
            menorCaneta = minOf(menorCaneta, ordenadas.first().coveragePerMille)
            maiorVazia = maxOf(maiorVazia, ordenadas.drop(1).maxOf { it.coveragePerMille })
        }

        assertTrue(maiorVazia < CORREDOR_INICIO) {
            "bolha vazia chega a $maiorVazia por mil e invade o corredor, que comeca em $CORREDOR_INICIO"
        }
        assertTrue(menorCaneta > CORREDOR_FIM) {
            "caneta desce a $menorCaneta por mil e invade o corredor, que termina em $CORREDOR_FIM"
        }
    }

    @Test
    fun `bolhas deslocadas meio passo derrubam a cobertura da caneta`() {
        // A verificacao que existe para poder falhar. Uma medicao que devolve numero plausivel
        // qualquer que seja a geometria nao esta medindo geometria nenhuma — e o defeito seria
        // silencioso, porque cobertura errada continua sendo cobertura.
        //
        // Meio passo horizontal e 2,6 mm: o disco cai entre duas bolhas, onde ha papel.
        val meioPasso = (2_600.0 / region.quadWidth * 1_000_000).toInt()
        val deslocada = region.copy(
            bubbles = region.bubbles.map { it.copy(u = it.u + meioPasso) },
        )
        val deslocado = map.copy(regions = listOf(deslocada))

        val certo = (BubbleMeter.measure(map, region, crop()) as MeterOutcome.Measured).measurements
        val errado = (BubbleMeter.measure(deslocado, deslocada, crop()) as MeterOutcome.Measured)
            .measurements

        val canetaCerta = certo.groupBy { it.questionId }
            .map { (_, bolhas) -> bolhas.maxOf { it.coveragePerMille } }
            .min()
        val canetaErrada = errado.groupBy { it.questionId }
            .map { (_, bolhas) -> bolhas.maxOf { it.coveragePerMille } }
            .min()

        assertTrue(canetaCerta > CORREDOR_FIM) {
            "a geometria certa devia por a caneta acima de $CORREDOR_FIM, veio $canetaCerta"
        }
        assertTrue(canetaErrada < canetaCerta / 2) {
            "com as bolhas deslocadas 2,6 mm a caneta caiu so de $canetaCerta para $canetaErrada " +
                "por mil — a medicao nao esta olhando para onde o mapa manda"
        }
    }

    private object OmrScale {
        const val FULL = 1_000
    }

    private companion object {
        /**
         * Tolerancia contra `papel.mjs`, em partes por mil.
         *
         * Vinte, e a origem e conhecida: as duas medem **coisas ligeiramente diferentes**. A
         * oficial le a regiao retificada e reamostrada; `papel.mjs` le a imagem original e amostra
         * um circulo. A divergencia medida na fixture foi de 4,9 por mil em media e 18 no pior
         * caso — que e uma bolha vazia, onde a diferenca absoluta pesa menos.
         *
         * Nao e tolerancia para acomodar ruido: e a distancia entre dois metodos, medida e
         * declarada. Se ela crescer, alguma das duas mudou.
         */
        const val TOLERANCIA_PER_MILLE = 20

        /** Corredor do limiar declarado em ADR-0010, em partes por mil. */
        const val CORREDOR_INICIO = 200
        const val CORREDOR_FIM = 400
    }
}

/**
 * Leitor de PGM P5: cabecalho em texto, corpo em byte cru.
 *
 * A fixture e PGM justamente para nao precisar de decodificador de imagem — um JPEG exigiria um, e
 * decodificador de JPEG nao e deterministico entre plataformas.
 */
private object Pgm {

    fun parse(bytes: ByteArray): RectifiedRegion {
        var cursor = 0
        fun token(): String {
            while (cursor < bytes.size && bytes[cursor].toInt().toChar().isWhitespace()) cursor += 1
            val start = cursor
            while (cursor < bytes.size && !bytes[cursor].toInt().toChar().isWhitespace()) cursor += 1
            return String(bytes, start, cursor - start, Charsets.US_ASCII)
        }

        val magic = token()
        require(magic == "P5") { "PGM esperado em P5, veio '$magic'" }
        val width = token().toInt()
        val height = token().toInt()
        val maxValue = token().toInt()
        require(maxValue == 255) { "PGM de 8 bits esperado, veio maximo $maxValue" }
        cursor += 1 // o unico byte de espaco depois do cabecalho

        val pixels = bytes.copyOfRange(cursor, bytes.size)
        require(pixels.size == width * height) {
            "PGM diz ${width}x$height = ${width * height} bytes, e o corpo tem ${pixels.size}"
        }
        return RectifiedRegion(width, height, pixels)
    }
}
