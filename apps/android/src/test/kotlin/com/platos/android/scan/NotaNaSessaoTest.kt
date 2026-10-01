package com.platos.android.scan

import com.platos.android.vision.FrameOutcome
import com.platos.android.vision.RegiaoDiscursivaNoQuadro
import com.platos.domain.capture.CapturePayload
import com.platos.domain.capture.InterpretedReading
import com.platos.domain.capture.QuestionAnswer
import com.platos.domain.exam.ExamPackage
import com.platos.domain.scoring.PontuacaoDada
import com.platos.domain.scoring.Pontos
import java.io.File
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * A nota do professor na sessao (`slice-5c-3-a-nota-no-aparelho`, spec `scan-session`): o que a sessao decide
 * ao dar a nota, ao proteger o caderno completo sem nota e ao comecar captura nova.
 *
 * A fixture e a de `ProvaComDiscursivaNaSessaoTest`: `q1,q2,q4,q5` objetivas (parcial 3 de 4), `d1` vale 3 e
 * `d2` vale 4, a prova vale 11.
 */
class NotaNaSessaoTest {

    private val fixtures = File(
        System.getProperty("platos.fixtures") ?: error("propriedade `platos.fixtures` nao definida pelo build"),
    )
    private val pacote: ExamPackage =
        Json.decodeFromString(File(fixtures, "prova-discursiva.package.json").readText())

    private var proximoId = 0
    private fun sessao() = ScanSession(pacote, novoId = { "parcial-${++proximoId}" }).apply { onPermission(true) }

    private fun payload(regiao: Int, token: String) = CapturePayload(pacote.meta.examId, token, "v1", regiao)
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

    private fun ScanSession.quadro(outcome: FrameOutcome): ApuracaoNova? = onFrame(
        outcome,
        outcome.discursivas.filterIsInstance<RegiaoDiscursivaNoQuadro.Reconhecida>().associate {
            it.regionIndex to RespostaDoQuadro.Guardada(RespostaGuardada("r${it.regionIndex}.png", 0L, false, 0))
        },
    )

    private fun completa(token: String = "tok-a"): ScanSession = sessao().also {
        it.quadro(FrameOutcome.Read(gabarito(token), listOf(d1(token))))
        it.quadro(FrameOutcome.SoDiscursivas(listOf(d2(token))))
    }

    private fun notas(d1: String = "2.5", d2: String = "3.75") =
        listOf(PontuacaoDada("d1", Pontos.parse(d1)), PontuacaoDada("d2", Pontos.parse(d2)))

    private fun corrigida(r: ResultadoDaNota) = r as? ResultadoDaNota.Corrigida
        ?: throw AssertionError("esperava a nota aceita, veio $r")

    private fun recusada(r: ResultadoDaNota) = (
        r as? ResultadoDaNota.Recusada
            ?: throw AssertionError("esperava a recusa, veio $r")
        ).motivo

    // --- a captura da parcial ---

    @Test
    fun `a entrega leva o id cunhado, e o caderno o guarda`() {
        val sessao = sessao()
        sessao.quadro(FrameOutcome.Read(gabarito(), listOf(d1())))

        val entrega = sessao.quadro(FrameOutcome.SoDiscursivas(listOf(d2()))) as ApuracaoNova.DeCaderno

        assertEquals("parcial-1", entrega.captureId)
        assertEquals("parcial-1", sessao.cadernoAtual!!.capturaDaParcial)
    }

    // --- dar a nota ---

    @Test
    fun `a nota aceita traz o total exato, a captura que completa e o caderno corrigido`() {
        val sessao = completa()

        val r = corrigida(sessao.darNota(notas()))

        assertEquals("9.25", r.nota.total.toString()) // 3 objetivos + 2.5 + 3.75
        assertEquals("parcial-1", r.completaCaptura)
        assertEquals("tok-a", r.aluno)
        assertTrue(r.cadernoCorrigido.corrigido)
    }

    @Test
    fun `darNota nao muda o caderno ate a gravacao ser confirmada`() {
        val sessao = completa()

        sessao.darNota(notas())

        assertFalse(sessao.cadernoAtual!!.corrigido)
    }

    @Test
    fun `confirmada a gravacao, o caderno passa a corrigido, e um quadro seguinte nao reentrega a parcial`() {
        val sessao = completa()
        sessao.darNota(notas())

        sessao.confirmarCorrigido()

        assertTrue(sessao.cadernoAtual!!.corrigido)
        assertNull(sessao.quadro(FrameOutcome.SoDiscursivas(listOf(d2()))), "caderno novo, 1 de 3: nada a entregar")
    }

    @Test
    fun `caderno corrigido nao oferece a nota outra vez`() {
        val sessao = completa()
        sessao.darNota(notas())
        sessao.confirmarCorrigido()

        assertTrue(recusada(sessao.darNota(notas())).contains("corrigido"))
    }

    @Test
    fun `o duplo toque nao gera duas notas, e falhar a gravacao libera de novo`() {
        val sessao = completa()
        corrigida(sessao.darNota(notas()))

        assertTrue(recusada(sessao.darNota(notas())).contains("sendo gravada"))

        sessao.falhouAGravacao()
        corrigida(sessao.darNota(notas()))
    }

    @Test
    fun `caderno incompleto nao tem nota`() {
        val sessao = sessao()
        sessao.quadro(FrameOutcome.Read(gabarito(), listOf(d1())))

        assertTrue(recusada(sessao.darNota(notas())).contains("completo"))
    }

    @Test
    fun `sem caderno nao ha nota`() {
        assertTrue(recusada(sessao().darNota(notas())).contains("caderno"))
    }

    @Test
    fun `a faixa do pacote e a do dominio, aqui e no servidor`() {
        val sessao = completa()

        assertTrue(recusada(sessao.darNota(notas(d1 = "3.01"))).contains("d1"))
        assertTrue(recusada(sessao.darNota(listOf(PontuacaoDada("d1", Pontos.parse("1"))))).contains("d2"))
    }

    @Test
    fun `zero em todas e maximo em todas fecham a soma exata`() {
        assertEquals("3.00", corrigida(completa().darNota(notas("0", "0"))).nota.total.toString())
        assertEquals("10.00", corrigida(completa().darNota(notas("3", "4"))).nota.total.toString())
    }

    // --- a mesma folha, depois de corrigida ---

    @Test
    fun `a mesma folha lida de novo depois de corrigida comeca captura nova`() {
        val sessao = completa()
        sessao.darNota(notas())
        sessao.confirmarCorrigido()

        sessao.quadro(FrameOutcome.Read(gabarito(), listOf(d1())))
        val entrega = sessao.quadro(FrameOutcome.SoDiscursivas(listOf(d2()))) as ApuracaoNova.DeCaderno

        assertEquals("parcial-2", entrega.captureId)
        assertFalse(sessao.cadernoAtual!!.corrigido)
    }

    // --- a substituicao protegida ---

    @Test
    fun `outro aluno nao substitui o caderno completo sem nota`() {
        val sessao = completa("tok-a")

        val aoTrocar = sessao.quadro(FrameOutcome.SoDiscursivas(listOf(d2("tok-b"))))

        assertNull(aoTrocar)
        val estado = sessao.state as ScanState.NotaPorDarDeOutroAluno
        assertEquals("tok-a", estado.caderno.aluno)
        assertEquals("tok-b", estado.alunoNovo)
        assertEquals("tok-a", sessao.cadernoAtual!!.aluno, "o caderno de tok-a segue sendo o corrente")
    }

    @Test
    fun `descartar e seguir perde o caderno e devolve os arquivos a eliminar`() {
        val sessao = completa("tok-a")
        sessao.quadro(FrameOutcome.SoDiscursivas(listOf(d2("tok-b"))))

        val arquivos = sessao.descartarESeguir()

        assertEquals(listOf("r1.png", "r2.png"), arquivos)
        assertNull(sessao.cadernoAtual)
        assertEquals(ScanState.Searching, sessao.state)
        sessao.quadro(FrameOutcome.SoDiscursivas(listOf(d2("tok-b"))))
        assertEquals("tok-b", sessao.cadernoAtual!!.aluno)
    }

    @Test
    fun `dar a nota e seguir libera a substituicao`() {
        val sessao = completa("tok-a")
        sessao.quadro(FrameOutcome.SoDiscursivas(listOf(d2("tok-b"))))

        corrigida(sessao.darNota(notas()))
        sessao.confirmarCorrigido()

        assertEquals(ScanState.Searching, sessao.state)
        sessao.quadro(FrameOutcome.SoDiscursivas(listOf(d2("tok-b"))))
        assertEquals("tok-b", sessao.cadernoAtual!!.aluno)
    }

    @Test
    fun `caderno incompleto continua sendo substituido sem confirmacao`() {
        val sessao = sessao()
        sessao.quadro(FrameOutcome.Read(gabarito("tok-a"), listOf(d1("tok-a"))))

        sessao.quadro(FrameOutcome.SoDiscursivas(listOf(d2("tok-b"))))

        assertEquals("tok-b", sessao.cadernoAtual!!.aluno)
        assertTrue(sessao.state is ScanState.ProvaComDiscursiva)
    }

    @Test
    fun `caderno corrigido tambem e substituido sem confirmacao`() {
        val sessao = completa("tok-a")
        sessao.darNota(notas())
        sessao.confirmarCorrigido()

        sessao.quadro(FrameOutcome.SoDiscursivas(listOf(d2("tok-b"))))

        assertEquals("tok-b", sessao.cadernoAtual!!.aluno)
    }
}
