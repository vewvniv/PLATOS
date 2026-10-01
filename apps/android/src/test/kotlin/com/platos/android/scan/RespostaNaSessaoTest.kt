package com.platos.android.scan

import com.platos.android.vision.FrameOutcome
import com.platos.android.vision.RegiaoDiscursivaNoQuadro
import com.platos.domain.capture.CapturePayload
import com.platos.domain.capture.InterpretedReading
import com.platos.domain.capture.QuestionAnswer
import com.platos.domain.exam.ExamPackage
import java.io.File
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * A resposta guardada na sessao (`slice-5c-1-a-resposta-fica-no-aparelho`, tarefas 1.2 a 1.4): regiao
 * discursiva so e capturada com resposta guardada, e refazer devolve a regiao a nao vista.
 *
 * Os quadros e as respostas sao montados a mao, como em `ProvaComDiscursivaNaSessaoTest`: a pergunta e o
 * que a sessao decide com o que o analisador entregou, e nao como o analisador o produz.
 */
class RespostaNaSessaoTest {

    private val fixtures = File(
        System.getProperty("platos.fixtures")
            ?: error("propriedade `platos.fixtures` nao definida pelo build"),
    )

    private val pacote: ExamPackage =
        Json.decodeFromString(File(fixtures, "prova-discursiva.package.json").readText())

    private fun payload(regiao: Int, token: String = "tok-a") =
        CapturePayload(pacote.meta.examId, token, "v1", regiao)

    private fun gabarito(token: String = "tok-a") = InterpretedReading(
        payload(0, token),
        emptyList(),
        listOf(
            QuestionAnswer.Marcada("q1", "A"),
            QuestionAnswer.Marcada("q2", "C"),
            QuestionAnswer.Marcada("q4", "B"),
            QuestionAnswer.Marcada("q5", "B"),
        ),
    )

    private fun d1(token: String = "tok-a") = RegiaoDiscursivaNoQuadro.Reconhecida(1, "d1", payload(1, token))

    private fun d2(token: String = "tok-a") = RegiaoDiscursivaNoQuadro.Reconhecida(2, "d2", payload(2, token))

    private fun guardada(arquivo: String, sinalizada: Boolean = false) =
        RespostaGuardada(arquivo, capturadaEm = 1_000L, desvioSinalizado = sinalizada, foraPpm = if (sinalizada) 60_000 else 0)

    private fun entregue(arquivo: String) = RespostaDoQuadro.Guardada(guardada(arquivo))

    private fun sessaoAberta() = ScanSession(pacote).apply { onPermission(granted = true) }

    private fun caderno(sessao: ScanSession): Caderno = requireNotNull(sessao.cadernoAtual)

    private fun regiao(sessao: ScanSession, indice: Int): RegiaoDoCaderno =
        caderno(sessao).regioes.single { it.regionIndex == indice }

    /** O caderno de `tok-a` com o gabarito e `d1` capturados (2 de 3), cada um com a sua resposta. */
    private fun comGabaritoED1(): ScanSession = sessaoAberta().apply {
        onFrame(FrameOutcome.Read(gabarito(), listOf(d1())), mapOf(1 to entregue("d1.png")))
    }

    // --- 1.2: a tabela da decisao 3 ---

    /** Cenario "Reconhecida e guardada conta" e "Caderno comeca com tudo nao visto". */
    @Test
    fun `reconhecida e guardada, a regiao e capturada, com a resposta no caderno, e o contador a soma`() {
        val sessao = comGabaritoED1()

        assertEquals(EstadoDaRegiao.Capturada, regiao(sessao, 1).estado)
        assertEquals(guardada("d1.png"), regiao(sessao, 1).resposta)
        assertEquals(2, caderno(sessao).capturadas)
        assertEquals(3, caderno(sessao).esperadas)
    }

    /** Cenario "Reconhecida com recorte recusado nao conta": o motivo e o do recorte. */
    @Test
    fun `reconhecida com o recorte recusado, com problema, com o motivo do recorte, e nao conta`() {
        val sessao = sessaoAberta()
        val motivo = "o residuo do ajuste passa do teto"

        sessao.onFrame(
            FrameOutcome.Read(gabarito(), listOf(d1())),
            mapOf(1 to RespostaDoQuadro.Recusada(motivo)),
        )

        assertEquals(EstadoDaRegiao.ComProblema(motivo), regiao(sessao, 1).estado)
        assertNull(regiao(sessao, 1).resposta)
        assertEquals(1, caderno(sessao).capturadas, "so o gabarito conta")
    }

    /**
     * O contrato (P16): se a fiacao do analisador se perder, a regiao nao vira capturada sem imagem —
     * vira um problema visivel, com a frase da spec.
     */
    @Test
    fun `reconhecida sem entrada alguma e sem resposta, com problema, o recorte nao foi pedido`() {
        val sessao = sessaoAberta()

        sessao.onFrame(FrameOutcome.Read(gabarito(), listOf(d1())))

        assertEquals(EstadoDaRegiao.ComProblema("o recorte nao foi pedido"), regiao(sessao, 1).estado)
        assertNull(regiao(sessao, 1).resposta)
        assertEquals(1, caderno(sessao).capturadas)
    }

    /** Sem entrada e com resposta ja guardada: o analisador nao pediu porque ja havia (decisao 1). */
    @Test
    fun `reconhecida sem entrada e com resposta ja guardada, fica como estava`() {
        val sessao = comGabaritoED1()

        sessao.resume()
        sessao.onFrame(FrameOutcome.SoDiscursivas(listOf(d1())))

        assertEquals(EstadoDaRegiao.Capturada, regiao(sessao, 1).estado)
        assertEquals(guardada("d1.png"), regiao(sessao, 1).resposta)
    }

    /** A primeira resposta aceita fica; o arquivo novo e um orfao da eliminacao. */
    @Test
    fun `uma segunda resposta guardada para a mesma regiao nao troca a primeira`() {
        val sessao = comGabaritoED1()

        sessao.onFrame(FrameOutcome.SoDiscursivas(listOf(d1())), mapOf(1 to entregue("d1-de-novo.png")))

        assertEquals(guardada("d1.png"), regiao(sessao, 1).resposta)
    }

    /** Cenario "Caderno sem a ultima resposta nao completa": nenhuma apuracao sai. */
    @Test
    fun `todas as regioes lidas e a resposta de uma discursiva recusada, nao completa, nada e entregue`() {
        val sessao = comGabaritoED1()

        val entrega = sessao.onFrame(
            FrameOutcome.SoDiscursivas(listOf(d2())),
            mapOf(2 to RespostaDoQuadro.Recusada("captura escura demais")),
        )

        assertNull(entrega, "uma discursiva sem resposta guardada nao pode completar o caderno")
        assertEquals(2, caderno(sessao).capturadas)
        assertEquals(false, caderno(sessao).entregue)
    }

    /** Cenario "Regiao com problema volta a capturada": e completa se era a ultima. */
    @Test
    fun `a regiao com problema volta a capturada quando o recorte e aceito, e o caderno completa`() {
        val sessao = comGabaritoED1()
        sessao.onFrame(
            FrameOutcome.SoDiscursivas(listOf(d2())),
            mapOf(2 to RespostaDoQuadro.Recusada("captura escura demais")),
        )

        val entrega = sessao.onFrame(FrameOutcome.SoDiscursivas(listOf(d2())), mapOf(2 to entregue("d2.png")))

        assertTrue(entrega is ApuracaoNova.DeCaderno, "esperava a entrega do caderno completo, veio $entrega")
        assertEquals(EstadoDaRegiao.Capturada, regiao(sessao, 2).estado)
        assertEquals(3, caderno(sessao).capturadas)
    }

    /** Cenario "Capturada nao volta atras": uma recusa depois nao a desfaz, e a resposta fica. */
    @Test
    fun `uma regiao ja capturada continua capturada quando o quadro seguinte tem o recorte recusado`() {
        val sessao = comGabaritoED1()

        sessao.onFrame(
            FrameOutcome.SoDiscursivas(listOf(d1())),
            mapOf(1 to RespostaDoQuadro.Recusada("captura escura demais")),
        )

        assertEquals(EstadoDaRegiao.Capturada, regiao(sessao, 1).estado)
        assertEquals(guardada("d1.png"), regiao(sessao, 1).resposta)
    }

    /** Cenario "Outro aluno comeca outro caderno": a resposta do anterior nao vem junto. */
    @Test
    fun `a folha de outro aluno comeca outro caderno, sem a resposta do anterior`() {
        val sessao = comGabaritoED1()

        sessao.onFrame(FrameOutcome.SoDiscursivas(listOf(d2(token = "tok-b"))), mapOf(2 to entregue("b2.png")))

        assertEquals("tok-b", caderno(sessao).aluno)
        assertNull(regiao(sessao, 1).resposta, "a resposta de tok-a nao pode aparecer no caderno de tok-b")
        assertEquals(EstadoDaRegiao.NaoVista, regiao(sessao, 1).estado)
        assertEquals(guardada("b2.png"), regiao(sessao, 2).resposta)
    }

    // --- 1.3: a entrega ---

    /** O caderno so completa com todas as respostas: e entrega uma vez. */
    @Test
    fun `completa com todas as respostas, entrega uma vez, e nao entrega de novo`() {
        val sessao = comGabaritoED1()

        val primeira = sessao.onFrame(FrameOutcome.SoDiscursivas(listOf(d2())), mapOf(2 to entregue("d2.png")))
        val segunda = sessao.onFrame(FrameOutcome.SoDiscursivas(listOf(d2())))

        assertTrue(primeira is ApuracaoNova.DeCaderno)
        assertNull(segunda, "o caderno ja foi entregue")
        assertTrue(caderno(sessao).entregue)
    }

    /** Cenario "Refazer uma resposta de caderno entregue nao entrega de novo". */
    @Test
    fun `refazer uma resposta de um caderno ja entregue nao o entrega de novo`() {
        val sessao = comGabaritoED1()
        sessao.onFrame(FrameOutcome.SoDiscursivas(listOf(d2())), mapOf(2 to entregue("d2.png")))
        assertTrue(caderno(sessao).entregue, "guarda de vacuidade: o caderno precisa ter sido entregue")

        sessao.refazer(2)
        val denovo = sessao.onFrame(FrameOutcome.SoDiscursivas(listOf(d2())), mapOf(2 to entregue("d2-refeita.png")))

        assertNull(denovo, "refazer a resposta de um caderno entregue nao pode entregar de novo")
        assertEquals(3, caderno(sessao).capturadas, "a regiao voltou a ser capturada")
        assertEquals(guardada("d2-refeita.png"), regiao(sessao, 2).resposta)
        assertTrue(caderno(sessao).entregue)
    }

    // --- 1.4: refazer ---

    /** Cenario "Refazer a resposta": regiao nao vista, sem resposta, contador cai, o resto fica. */
    @Test
    fun `refazer devolve o arquivo e a regiao volta a nao vista, sem tocar nas outras nem na parcial`() {
        val sessao = sessaoAberta()
        sessao.onFrame(
            FrameOutcome.Read(gabarito(), listOf(d1(), d2())),
            mapOf(1 to entregue("d1.png"), 2 to entregue("d2.png")),
        )
        val antes = caderno(sessao)
        assertNotNull(antes.parcial, "guarda de vacuidade: o gabarito foi lido, ha parcial")

        val resultado = sessao.refazer(1)

        assertEquals(ResultadoDoRefazer.Refeita("d1.png"), resultado)
        val depois = caderno(sessao)
        assertEquals(EstadoDaRegiao.NaoVista, regiao(sessao, 1).estado)
        assertNull(regiao(sessao, 1).resposta)
        assertEquals(2, depois.capturadas, "o contador deixou de somar a regiao refeita")
        assertEquals(antes.regioes.single { it.regionIndex == 0 }, depois.regioes.single { it.regionIndex == 0 })
        assertEquals(antes.regioes.single { it.regionIndex == 2 }, depois.regioes.single { it.regionIndex == 2 })
        assertEquals(antes.parcial, depois.parcial, "refazer nao muda a parcial apresentada")
        assertEquals(depois, (sessao.state as ScanState.ProvaComDiscursiva).caderno, "a tela enxerga o caderno novo")
    }

    @Test
    fun `refazer de uma regiao sem resposta, ou que nao existe, recusa com o motivo e nao muda nada`() {
        val sessao = comGabaritoED1()
        val antes = caderno(sessao)

        val semResposta = sessao.refazer(2)
        val gabaritoSemResposta = sessao.refazer(0)
        val inexistente = sessao.refazer(99)

        assertEquals(ResultadoDoRefazer.Recusado("a regiao 2 nao tem resposta guardada"), semResposta)
        assertEquals(ResultadoDoRefazer.Recusado("a regiao 0 nao tem resposta guardada"), gabaritoSemResposta)
        assertEquals(ResultadoDoRefazer.Recusado("a regiao 99 nao existe no caderno"), inexistente)
        assertEquals(antes, caderno(sessao))
    }

    @Test
    fun `refazer sem caderno em andamento recusa`() {
        val sessao = sessaoAberta()

        assertEquals(ResultadoDoRefazer.Recusado("nao ha caderno em andamento"), sessao.refazer(1))
    }

    /** Cenario "A proxima captura depois de refazer": a regiao volta a ser capturada com a nova resposta. */
    @Test
    fun `depois de refazer, a regiao reconhecida de novo guarda a nova resposta`() {
        val sessao = comGabaritoED1()
        sessao.refazer(1)

        sessao.onFrame(FrameOutcome.SoDiscursivas(listOf(d1())), mapOf(1 to entregue("d1-nova.png")))

        assertEquals(EstadoDaRegiao.Capturada, regiao(sessao, 1).estado)
        assertEquals(guardada("d1-nova.png"), regiao(sessao, 1).resposta)
    }
}
