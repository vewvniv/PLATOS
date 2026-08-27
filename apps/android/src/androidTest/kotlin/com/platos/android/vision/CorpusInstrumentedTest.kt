package com.platos.android.vision

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.platos.domain.capture.InterpretationOutcome
import com.platos.domain.capture.OmrReading
import com.platos.domain.capture.OmrThreshold
import com.platos.domain.exam.ExamPackage
import com.platos.domain.layout.LayoutMap
import com.platos.domain.scoring.ObjectiveScoring
import com.platos.domain.scoring.ScoringOutcome
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import java.io.File
import kotlin.math.abs

/**
 * O corpus fotografado da fatia 3b, medido pela implementacao oficial.
 *
 * Nove fotos de camera: duas folhas da prova e a folha de teste de impressao, em tres condicoes
 * cada — luz frontal, sombra e angulo. Duas perguntas, e so duas.
 *
 * A primeira: o pipeline de §8 le **foto de celular**, e nao so digitalizacao de mesa. Perspectiva,
 * sombra e reflexo sao o meio em que o produto vai viver, e foi este corpus que mostrou que o QR
 * nunca teve zona de silencio — ver `RegionDetector.qrCanvas`.
 *
 * A segunda: a medicao oficial concorda com `papel.mjs` dentro do que os dois metodos se afastam.
 *
 * **Nao ha limiar aqui.** Este teste mede e compara; quem escolhe o numero e ADR-0011, por
 * aritmetica declarada antes da medicao, sobre os vetores que [entrega_o_vetor_de_cada_foto] grava.
 */
@RunWith(AndroidJUnit4::class)
class CorpusInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().context

    private fun mapa(nome: String): LayoutMap = Json.decodeFromString(
        context.assets.open(nome).use { it.readBytes().decodeToString() },
    )

    private val prova by lazy { mapa("prova-referencia.layout.json") }
    private val teste by lazy { mapa("folha-de-teste.layout.json") }

    @Before
    fun carregaOpenCv() {
        assertTrue("o OpenCV nativo nao carregou", OpenCVLoader.initLocal())
    }

    private fun cinza(asset: String): Mat {
        val bytes = context.assets.open(asset).use { it.readBytes() }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: throw AssertionError("nao consegui decodificar $asset")
        val colorido = Mat()
        Utils.bitmapToMat(bitmap, colorido)
        val gray = Mat()
        Imgproc.cvtColor(colorido, gray, Imgproc.COLOR_RGBA2GRAY)
        return gray
    }

    private fun leOuFalha(asset: String, map: LayoutMap): OmrReading.Read {
        val leitura = SheetReader.read(cinza(asset), map, map.regions.single())
        return leitura as? OmrReading.Read
            ?: throw AssertionError("$asset: esperava leitura, veio $leitura")
    }

    /** O que `papel.mjs` mediu na mesma foto, em permilagem inteira. */
    private fun referencia(asset: String): Map<String, Int> {
        val texto = context.assets.open(asset).use { it.readBytes().decodeToString() }
        return Json.parseToJsonElement(texto).jsonObject["bolhas"]!!.jsonArray.associate {
            val b = it.jsonObject
            b["id"]!!.jsonPrimitive.content to
                (b["cobertura"]!!.jsonPrimitive.content.toDouble() * 1_000).toInt()
        }
    }

    private fun conferePapelMjs(foto: String, map: LayoutMap) {
        val medido = leOuFalha("$foto.jpg", map).measurements.associate { it.id to it.coveragePerMille }
        val esperado = referencia("$foto.papel.json")

        assertEquals("$foto: conjuntos de bolhas diferentes", esperado.keys, medido.keys)

        val diferencas = esperado.keys.map { abs(esperado.getValue(it) - medido.getValue(it)) }

        // Duas guardas, e a segunda e a que importa. Um teto por bolha sozinho aceita um desvio
        // **sistematico** de ate o proprio teto: as duas implementacoes poderiam divergir 29‰ em
        // toda bolha e o teste seguiria verde. A mediana pega isso, e no corpus ela e 6‰.
        val mediana = diferencas.sorted()[diferencas.size / 2]
        assertTrue(
            "$foto: mediana de ${mediana}‰ entre papel.mjs e SheetReader, teto $MEDIANA_MAXIMA‰ — " +
                "isso e desvio sistematico, e nao a cauda de sempre",
            mediana <= MEDIANA_MAXIMA,
        )

        val divergentes = esperado.keys
            .map { Triple(it, esperado.getValue(it), medido.getValue(it)) }
            .filter { (_, a, b) -> abs(a - b) > TOLERANCIA_CAMERA }
        assertTrue(
            "$foto: ${divergentes.size} bolha(s) fora de $TOLERANCIA_CAMERA‰ entre papel.mjs e " +
                "SheetReader — " + divergentes.take(5).joinToString {
                    "${it.first} ${it.second} vs ${it.third}"
                },
            divergentes.isEmpty(),
        )
    }

    @Test
    fun a_prova_1_de_frente_bate_com_papel_mjs() = conferePapelMjs("corpus-3b-prova1-frontal", prova)

    @Test
    fun a_prova_1_com_sombra_bate_com_papel_mjs() = conferePapelMjs("corpus-3b-prova1-sombra", prova)

    @Test
    fun a_prova_2_bate_com_papel_mjs() = conferePapelMjs("corpus-3b-prova2-c", prova)

    @Test
    fun a_folha_de_teste_de_frente_bate_com_papel_mjs() =
        conferePapelMjs("corpus-3b-teste-frontal", teste)

    @Test
    fun a_folha_de_teste_com_sombra_bate_com_papel_mjs() =
        conferePapelMjs("corpus-3b-teste-sombra", teste)

    @Test
    fun as_fotos_em_angulo_sao_lidas_pelo_pipeline_de_producao() {
        // `papel.mjs` nao mede estas: ele acha ArUco por componentes conexos sobre limiar, e
        // desiste com a folha em perspectiva sobre fundo estampado. O OpenCV acha por contorno e
        // casamento de dicionario, e le. E a diferenca de metodo que o `design.md` da 3a previu ao
        // manter as duas implementacoes vivas — aqui ela deixou de ser teorica.
        //
        // Sem oracle independente, o teste afirma o que pode: que a leitura acontece, que cobre as
        // bolhas declaradas e que os valores sao finitos e na escala.
        for ((foto, map) in listOf(
            "corpus-3b-prova1-angulo" to prova,
            "corpus-3b-teste-angulo" to teste,
        )) {
            val leitura = leOuFalha("$foto.jpg", map)
            assertEquals("$foto", map.regions.single().bubbles.size, leitura.measurements.size)
            assertTrue("$foto", leitura.measurements.all { it.coveragePerMille in 0..1_000 })
        }
    }

    @Test
    fun as_duas_folhas_se_identificam_pelo_qr() {
        // O QR e o unico canal que diz de qual prova a folha e. Antes da sangria do canvas, quatro
        // das nove eram recusadas aqui.
        for (foto in LEGIVEIS) {
            val map = if (foto.contains("teste")) teste else prova
            val leitura = leOuFalha("$foto.jpg", map)
            val esperado =
                if (foto.contains("teste")) "folha-de-teste-de-impressao" else "prova-referencia-slice-1"
            assertEquals(foto, esperado, leitura.payload.examShortId)
        }
    }

    @Test
    fun foto_distante_demais_e_recusada_no_qr() {
        // Nao e defeito, e limite: o QR tem 14 mm e cerca de 29 modulos por lado, entao cada modulo
        // e 0,48 mm. Abaixo de mais ou menos 11 px por milimetro de papel nao sobra pixel suficiente
        // por modulo, e o decodificador desiste. Medido neste corpus pelo lado do ArUco, que tem
        // 14 mm conhecidos:
        //
        // | foto | px/mm | QR |
        // |---|---|---|
        // | prova1-frontal, prova1-sombra, teste-frontal, teste-sombra | 12,7 a 12,9 | le |
        // | prova2-c | 11,4 | le |
        // | prova2-a | 9,3 | recusa |
        //
        // O teste existe para que o limite seja **afirmado** em vez de descoberto de novo: a fatia
        // da camera precisa guiar o enquadramento, e este e o numero que ela herda. Se um dia a
        // leitura melhorar e passar a ler estas duas, este teste fica vermelho — e ai a mudanca e
        // deliberada, e o numero acima e atualizado junto.
        for (foto in DISTANTES) {
            val leitura = SheetReader.read(cinza("$foto.jpg"), prova, prova.regions.single())
            val recusa = leitura as? OmrReading.Rejected
                ?: throw AssertionError("$foto: esperava recusa por resolucao, veio $leitura")
            assertTrue(
                "$foto: recusou por outro motivo — ${recusa.reason}",
                recusa.reason.contains("QR"),
            )
        }
    }

    @Test
    fun a_folha_2_vira_nota_com_o_limiar_medido() {
        // O caminho inteiro sobre uma foto do corpus, com o limiar que o corpus produziu: imagem,
        // cobertura, veredito, resposta, nota.
        //
        // O oracle e o gabarito anotado no papel — as 40 letras que foram marcadas —, cruzado com o
        // `answer_key` do pacote **fora deste alvo**. Deu 12 acertos de 40. Nenhum tipo desta fatia
        // participa dessa conta.
        //
        // A folha 2 foi preenchida inteira conforme a instrucao, sem o subgrupo fraco de proposito
        // que a folha 1 carrega — entao a nota tem de **fechar**, sem nenhuma pendencia. E o caso
        // que o produto vai ver numa turma que seguiu a instrucao.
        val pacote: ExamPackage = Json.decodeFromString(
            context.assets.open("prova-referencia.package.json").use {
                it.readBytes().decodeToString()
            },
        )
        val leitura = SheetReader.readInterpreted(
            cinza("corpus-3b-prova2-c.jpg"),
            prova,
            prova.regions.single(),
            OmrThreshold.MEDIDO_NA_FATIA_3B,
        )
        val interpretada = (leitura as? InterpretationOutcome.Interpreted)?.reading
            ?: throw AssertionError("esperava leitura interpretada, veio $leitura")

        val resultado = ObjectiveScoring.score(pacote, interpretada.payload, interpretada.answers)
        val nota = (resultado as? ScoringOutcome.Scored)?.score
            ?: throw AssertionError("esperava nota, veio $resultado")

        assertEquals("a folha 2 nao tem rasura: a nota tem de fechar", true, nota.closed)
        assertEquals("a nota divergiu do gabarito anotado no papel", 12, nota.points)
        assertEquals(40, nota.maxScore)
    }

    @Test
    fun entrega_o_vetor_de_cada_foto() {
        // Nao afirma numero nenhum: a escolha do limiar e aritmetica de ADR-0011 sobre o corpus
        // inteiro, e ela acontece fora daqui, contra o gabarito anotado no papel.
        val destino = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
            ?: return
        for (foto in LEGIVEIS) {
            val map = if (foto.contains("teste")) teste else prova
            val leitura = leOuFalha("$foto.jpg", map)
            File(destino, "$foto.sheetreader.json").writeText(
                leitura.measurements.joinToString(prefix = "{\"bolhas\":[", postfix = "]}") {
                    "{\"id\":\"${it.id}\",\"permilagem\":${it.coveragePerMille}}"
                },
            )
        }
    }

    private companion object {
        /** As sete fotos que o pipeline de producao le de ponta a ponta. */
        val LEGIVEIS = listOf(
            "corpus-3b-prova1-frontal", "corpus-3b-prova1-sombra", "corpus-3b-prova1-angulo",
            "corpus-3b-prova2-c",
            "corpus-3b-teste-frontal", "corpus-3b-teste-sombra", "corpus-3b-teste-angulo",
        )

        /** As duas fotos distantes demais para o QR. Ver [foto_distante_demais_e_recusada_no_qr]. */
        val DISTANTES = listOf("corpus-3b-prova2-a", "corpus-3b-prova2-b")

        /**
         * Distancia admitida por bolha entre `papel.mjs` e o `SheetReader`, em foto de camera.
         *
         * §10 do protocolo registra **20‰** entre os dois metodos na digitalizacao de mesa. Foto de
         * camera afasta os dois um pouco mais, e o numero aqui saiu do corpus da 3b: mediana 6‰,
         * p95 17‰, p99 25‰, maximo 29‰. O teto e 30, e nao 25, para nao reprovar por uma bolha na
         * cauda — e nao e mais que isso, porque folga que engole defeito e o modo de falha que a
         * fatia 3a registrou tres vezes. Para calibrar: deslocar as bolhas 2,6 mm derrubou uma
         * medicao de 514‰ para 220‰, e o vao entre caneta e vazia neste corpus passa de 400‰.
         *
         * A divergencia tem explicacao de metodo, e nao de defeito: `papel.mjs` amostra o circulo na
         * imagem original e o `SheetReader` retifica antes de amostrar, e a reamostragem suaviza.
         */
        const val TOLERANCIA_CAMERA = 30

        /**
         * Teto da **mediana** das diferencas, por foto.
         *
         * O teto por bolha nao distingue cauda de deslocamento: as duas implementacoes divergindo
         * 29‰ em toda bolha passariam por ele. No corpus a mediana fica em 6‰ e 7‰.
         */
        const val MEDIANA_MAXIMA = 12
    }
}
