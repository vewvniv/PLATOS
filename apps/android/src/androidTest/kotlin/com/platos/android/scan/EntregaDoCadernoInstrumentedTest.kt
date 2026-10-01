package com.platos.android.scan

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.platos.android.outbox.BaseDoOutbox
import com.platos.android.outbox.ResultadoPendente
import com.platos.android.outbox.ResultadosEmRoom
import com.platos.android.outbox.ResultadosPendentes
import com.platos.android.vision.FrameOutcome
import com.platos.android.vision.RegiaoDiscursivaNoQuadro
import com.platos.domain.capture.CapturePayload
import com.platos.domain.capture.InterpretedReading
import com.platos.domain.capture.QuestionAnswer
import com.platos.domain.exam.ExamPackage
import com.platos.domain.scoring.ApuracaoParaEnvio
import kotlinx.serialization.json.Json
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Como o analisador entrega o quadro (`slice-5c-1-a-resposta-fica-no-aparelho`): cada regiao discursiva
 * reconhecida chega com a resposta guardada. Existe para as chamadas anteriores a 5c-1 continuarem
 * dizendo o que diziam — capturada — sem mudar nenhuma asercao.
 */
private fun ScanSession.onFrameGuardando(outcome: FrameOutcome): ApuracaoNova? = onFrame(
    outcome,
    outcome.discursivas.filterIsInstance<RegiaoDiscursivaNoQuadro.Reconhecida>().associate {
        it.regionIndex to RespostaDoQuadro.Guardada(RespostaGuardada("r${it.regionIndex}.png", 0L, false, 0))
    },
)

/**
 * O elo entre `ScanSession` e o outbox real (tarefa 4.3, `slice-5b-4-envio-da-parcial`).
 *
 * **O que isto acrescenta ao teste de JVM.** `ProvaComDiscursivaNaSessaoTest` prova que
 * `ScanSession.onFrame` devolve `ApuracaoNova.DeCaderno` na transição de completude; este arquivo
 * prova a metade que só o aparelho decide: que esse valor, levado ao mesmo caminho que
 * `ScanActivity.gravar` percorre, produz uma linha real na fila de `result-sync` — sobre o SQLite de
 * verdade, e não sobre uma guarda em memória. Suite vizinha verde não verifica esta camada (P16).
 *
 * A montagem do `ResultadoPendente` aqui é a mesma que `ScanActivity.gravar` faz: o token vem de
 * `DeCaderno.aluno`, vazio vira nulo, e a nota é `ApuracaoParaEnvio.Parcial`.
 */
@RunWith(AndroidJUnit4::class)
class EntregaDoCadernoInstrumentedTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val nomeDaBase = "entrega-do-caderno-de-teste.db"
    private val nomeDaBaseDeCadernos = "entrega-do-caderno-caderno-de-teste.db"

    private val organizacao = "01a06ba4-cb43-7d97-842d-165352d010b5"

    private lateinit var base: BaseDoOutbox
    private lateinit var pendentes: ResultadosPendentes
    private lateinit var baseDeCadernos: BaseDoCaderno
    private lateinit var cadernos: CadernosGuardados

    private val pacote: ExamPackage by lazy {
        Json { ignoreUnknownKeys = false }.decodeFromString(
            ExamPackage.serializer(),
            instrumentation.context.assets.open("prova-discursiva.package.json")
                .use { it.readBytes().decodeToString() },
        )
    }

    private fun payload(regiao: Int, token: String) = CapturePayload(pacote.meta.examId, token, "v1", regiao)

    private fun gabarito(token: String) = InterpretedReading(
        payload(0, token),
        emptyList(),
        listOf(
            QuestionAnswer.Marcada("q1", "A"),
            QuestionAnswer.Marcada("q2", "C"),
            QuestionAnswer.Marcada("q4", "B"),
            QuestionAnswer.Marcada("q5", "B"),
        ),
    )

    private fun d1(token: String) = RegiaoDiscursivaNoQuadro.Reconhecida(1, "d1", payload(1, token))
    private fun d2(token: String) = RegiaoDiscursivaNoQuadro.Reconhecida(2, "d2", payload(2, token))

    /** Grava exatamente como `ScanActivity.gravar` grava, para o mesmo `apuracao`. */
    private fun gravar(apuracao: ApuracaoNova) {
        val (studentToken, nota) = when (apuracao) {
            is ApuracaoNova.Completa ->
                apuracao.reading.payload.studentToken.ifEmpty { null } to ApuracaoParaEnvio.Completa(apuracao.score)
            is ApuracaoNova.DeCaderno ->
                apuracao.aluno.ifEmpty { null } to ApuracaoParaEnvio.Parcial(apuracao.score)
        }
        pendentes.guardar(
            ResultadoPendente(
                captureId = UUID.randomUUID().toString(),
                organizacao = organizacao,
                prova = pacote.meta.examId,
                studentToken = studentToken,
                apuradoEm = System.currentTimeMillis(),
                nota = nota,
            ),
        )
    }

    @Before
    fun preparar() {
        context.deleteDatabase(nomeDaBase)
        context.deleteDatabase(nomeDaBaseDeCadernos)
        base = Room.databaseBuilder(context, BaseDoOutbox::class.java, nomeDaBase).build()
        pendentes = ResultadosEmRoom(base.pendentes())
        baseDeCadernos = Room.databaseBuilder(context, BaseDoCaderno::class.java, nomeDaBaseDeCadernos).build()
        cadernos = CadernosEmRoom(baseDeCadernos.cadernos())
    }

    @After
    fun limpar() {
        base.close()
        baseDeCadernos.close()
        context.deleteDatabase(nomeDaBase)
        context.deleteDatabase(nomeDaBaseDeCadernos)
    }

    /**
     * Cenário "O caderno completo é entregue para gravação", de ponta a ponta: a sessão entrega, o
     * caminho de `ScanActivity.gravar` grava, e a fila real do outbox ganha exatamente uma linha.
     */
    @Test
    fun o_caderno_completo_entra_na_fila_do_outbox() {
        val sessao = ScanSession(pacote).apply { onPermission(granted = true) }
        sessao.onFrameGuardando(FrameOutcome.Read(gabarito("tok-a"), listOf(d1("tok-a"))))

        val entrega = sessao.onFrameGuardando(FrameOutcome.SoDiscursivas(listOf(d2("tok-a"))))

        val deCaderno = entrega as? ApuracaoNova.DeCaderno
            ?: throw AssertionError("esperava a entrega do caderno completo, veio $entrega")
        gravar(deCaderno)

        val naFila = pendentes.pendentesDa(organizacao)
        assertEquals(1, naFila.size)
        assertEquals(1, pendentes.quantosPendentes(organizacao))
    }

    /**
     * Cenário "Caderno incompleto substituído por outro aluno não é entregue", de ponta a ponta: a
     * sessão nunca entrega para o caderno de `tok-a`, e a fila do outbox continua vazia.
     */
    @Test
    fun trocar_de_aluno_antes_de_completar_nao_produz_pendente() {
        val sessao = ScanSession(pacote).apply { onPermission(granted = true) }
        sessao.onFrameGuardando(FrameOutcome.Read(gabarito("tok-a"), listOf(d1("tok-a"))))

        val aoTrocar = sessao.onFrameGuardando(FrameOutcome.SoDiscursivas(listOf(d2("tok-b"))))

        assertTrue("o caderno de tok-a estava incompleto, e nao pode ter sido entregue", aoTrocar == null)
        assertEquals(0, pendentes.quantosPendentes(organizacao))
    }

    /**
     * Tarefa 5.1: o aplicativo fecha com o caderno em 2 de 3 (`slice-5b-3-guardar-a-parcial-e-o-
     * caderno`), reabre retomando-o, completa e envia — as duas fatias compondo pela primeira vez.
     *
     * O "fechar e reabrir" é `cadernos.guardar` seguido de uma `ScanSession` nova construída só com
     * o que [CadernosGuardados.ler] devolve, exatamente como `ScanActivity.onStop`/`onCreate` fazem.
     */
    @Test
    fun caderno_retomado_apos_fechar_o_aplicativo_completa_e_entra_na_fila() {
        val original = ScanSession(pacote).apply { onPermission(granted = true) }
        original.onFrameGuardando(FrameOutcome.Read(gabarito("tok-a"), listOf(d1("tok-a"))))
        val emAndamento = requireNotNull(original.cadernoAtual) { "a sessao original nao capturou nada" }
        assertEquals("guarda de vacuidade: o caderno fecha incompleto", 2, emAndamento.capturadas)

        // "Fechar o aplicativo": guarda o caderno, como `ScanActivity.onStop` faz.
        cadernos.guardar(organizacao, pacote.meta.examId, emAndamento)

        // "Reabrir": uma sessao nova, retomando so o que foi guardado — nenhum quadro anterior.
        val retomada = ScanSession(pacote, cadernoInicial = cadernos.ler(organizacao, pacote.meta.examId))
        retomada.onPermission(granted = true)

        val entrega = retomada.onFrameGuardando(FrameOutcome.SoDiscursivas(listOf(d2("tok-a"))))

        val deCaderno = entrega as? ApuracaoNova.DeCaderno
            ?: throw AssertionError("esperava a entrega do caderno retomado e completo, veio $entrega")
        gravar(deCaderno)

        assertEquals(1, pendentes.quantosPendentes(organizacao))
        val corpo = pendentes.pendentesDa(organizacao).single().corpo
        assertTrue(corpo, """"partial":true""" in corpo)
        assertTrue(corpo, """"closed":false""" in corpo)
    }
}
