package com.platos.android.outbox

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.platos.android.api.corpoDoEnvio
import com.platos.domain.capture.QuestionAnswer
import com.platos.domain.scoring.ObjectiveScore
import com.platos.domain.scoring.PendingQuestion
import com.platos.domain.scoring.PendingReason
import com.platos.domain.scoring.QuestionOutcome
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A fila do outbox sobre o SQLite de verdade.
 *
 * **O que este teste acrescenta ao de JVM.** `EnvioDeResultadosTest` exercita a decisao — enviar,
 * apagar so o confirmado, nao bloquear a fila — contra uma guarda em memoria. O que so o aparelho
 * decide e o resto: que o schema que o processador de anotacoes gerou bate com as consultas, que a
 * chave primaria de fato impede duas linhas para a mesma captura, que o corpo volta do disco
 * identico ao que entrou, e que o dado esta em **disco** e nao em memoria. Suite vizinha verde nao
 * verifica esta camada (P16).
 *
 * **O que ele NAO prova, e fica dito (P8):** sobreviver a morte do processo. Fechar e reabrir a base
 * mostra que o dado atravessa a instancia, e e ate onde um teste instrumentado alcanca. A
 * desinstalacao levando o `filesDir` junto e garantia da plataforma, e fica **herdada**, nao
 * verificada.
 *
 * **Base em arquivo, e nao `inMemoryDatabaseBuilder`.** Em memoria, "fechei e reabri e o dado estava
 * la" seria falso por construcao — e e exatamente a afirmacao que esta classe existe para fazer.
 */
@RunWith(AndroidJUnit4::class)
class OutboxEmRepousoInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val nomeDaBase = "outbox-de-teste.db"

    private lateinit var base: BaseDoOutbox
    private lateinit var guarda: ResultadosPendentes

    private val organizacao = "01a06ba4-cb43-7d97-842d-165352d010b5"
    private val outraOrganizacao = "01a06ba4-0000-7d97-842d-165352d010b5"
    private val prova = "prova-referencia-slice-1"

    private fun abrir(): BaseDoOutbox =
        Room.databaseBuilder(context, BaseDoOutbox::class.java, nomeDaBase)
            // So neste teste: o fio principal do runner instrumentado e onde as assercoes moram, e a
            // producao grava de dentro do laco da camera, que tambem e o principal.
            .allowMainThreadQueries()
            .build()

    private fun nota(pontos: Int = 1, comPendencia: Boolean = false): ObjectiveScore {
        val marcada = QuestionOutcome("q01", QuestionAnswer.Marcada("q01", "A"), 1, pontos.coerceAtMost(1))
        val segunda = if (comPendencia) {
            QuestionOutcome("q02", QuestionAnswer.MultiplaMarcacao("q02", listOf("A", "C")), 1, 0)
        } else {
            QuestionOutcome("q02", QuestionAnswer.EmBranco("q02"), 1, 0)
        }
        return ObjectiveScore(
            packageHash = "a".repeat(64),
            variantId = "v1",
            points = marcada.earned,
            maxScore = 2,
            pending = if (comPendencia) {
                listOf(PendingQuestion("q02", PendingReason.MULTIPLA_MARCACAO, 1))
            } else {
                emptyList()
            },
            outcomes = listOf(marcada, segunda),
        )
    }

    private fun pendente(
        captureId: String,
        organizacao: String = this.organizacao,
        token: String? = "tok-a",
        comPendencia: Boolean = false,
    ) = ResultadoPendente(
        captureId = captureId,
        organizacao = organizacao,
        prova = prova,
        studentToken = token,
        apuradoEm = 1_789_646_400_000L,
        nota = nota(comPendencia = comPendencia),
    )

    @Before
    fun abrirBase() {
        context.deleteDatabase(nomeDaBase)
        base = abrir()
        guarda = ResultadosEmRoom(base.pendentes())
    }

    @After
    fun limpar() {
        base.close()
        context.deleteDatabase(nomeDaBase)
    }

    // ------------------------------------------------------------------ 5.1 e 5.4

    @Test
    fun a_correcao_gravada_atravessa_o_fechamento_da_base() {
        guarda.guardar(pendente("cap-1"))
        base.close()

        // Instancia nova sobre o **mesmo arquivo**: se o dado estivesse em memoria, aqui viria zero.
        base = abrir()
        val depois = ResultadosEmRoom(base.pendentes()).pendentesDa(organizacao)

        assertEquals(1, depois.size)
        assertEquals("cap-1", depois.single().captureId)
        assertEquals(prova, depois.single().prova)
    }

    /**
     * A ancora do que foi lido (P3).
     *
     * Contar uma linha nao distingue "a correcao que eu gravei" de "alguma correcao": uma base
     * herdada de outra execucao daria a mesma contagem. O corpo e comparado com o que
     * `corpoDoEnvio` produz para o **mesmo** pendente, e ele carrega o instante da apuracao.
     */
    @Test
    fun o_corpo_volta_do_disco_identico_ao_que_entrou() {
        val original = pendente("cap-ancora", comPendencia = true)
        guarda.guardar(original)
        base.close()

        base = abrir()
        val lido = ResultadosEmRoom(base.pendentes()).pendentesDa(organizacao).single()

        assertEquals(original.corpoDoEnvio(), lido.corpo)
        assertTrue(
            "o corpo precisa trazer a pendencia, ou este teste nao exercita o caso parcial",
            lido.corpo.contains("multipla_marcacao"),
        )
        assertTrue(lido.corpo.contains("\"closed\":false"))
    }

    // ------------------------------------------------------------------ 5.2

    /**
     * Folha recusada nao grava nada — e o canario e o que faz a assercao valer (P13).
     *
     * "A tabela esta vazia" passa tambem numa base que nunca recebeu nada, ou cujo `guardar` esta
     * quebrado. A gravacao que **funciona** antes da assercao e o que separa os dois casos.
     */
    @Test
    fun a_tabela_vazia_e_afirmacao_e_nao_acidente() {
        assertEquals(0, guarda.quantosPendentes(organizacao))

        guarda.guardar(pendente("cap-canario"))
        assertEquals(
            "o canario nao gravou: a assercao de vazio acima nao mede nada",
            1,
            guarda.quantosPendentes(organizacao),
        )

        guarda.apagarConfirmado("cap-canario")
        assertEquals(0, guarda.quantosPendentes(organizacao))
    }

    // ------------------------------------------------------------------ 5.3

    /**
     * O que esta gravado nao leva nome, turma nem matricula.
     *
     * A assercao e sobre o conteudo da coluna, e nao sobre a ausencia de uma palavra no codigo: o
     * nome do aluno so existe no roster, resolvido para a tela e nunca copiado para o resultado.
     */
    @Test
    fun o_gravado_leva_o_token_e_nao_o_nome() {
        guarda.guardar(pendente("cap-token", token = "tok-a"))

        val corpo = guarda.pendentesDa(organizacao).single().corpo

        assertTrue("o token do QR precisa estar no corpo: $corpo", corpo.contains("\"tok-a\""))
        assertFalse("nome de aluno no resultado gravado: $corpo", corpo.contains("Ana"))
        assertFalse(corpo.contains("class_group"))
        assertFalse(corpo.contains("enrollment"))
        assertFalse("habilidade nao e gravada aqui: $corpo", corpo.contains("skill"))
    }

    @Test
    fun a_folha_avulsa_grava_token_nulo() {
        guarda.guardar(pendente("cap-avulsa", token = null))

        val corpo = guarda.pendentesDa(organizacao).single().corpo

        assertTrue(corpo, corpo.contains("\"student_token\":null"))
    }

    // ------------------------------------------------------------------ o schema em si

    /**
     * A mesma captura nao vira duas linhas.
     *
     * E a chave primaria fazendo o trabalho dela. Sem isto, um reenvio local — o trabalho que roda
     * duas vezes — empilharia copias da mesma correcao, e o servidor veria a segunda como reenvio
     * enquanto o aparelho continuaria com uma linha a mais que nunca sairia.
     */
    @Test
    fun a_mesma_captura_nao_vira_duas_linhas() {
        guarda.guardar(pendente("cap-igual"))
        guarda.guardar(pendente("cap-igual"))

        assertEquals(1, guarda.quantosPendentes(organizacao))
    }

    @Test
    fun capturas_diferentes_da_mesma_folha_convivem() {
        guarda.guardar(pendente("cap-1"))
        guarda.guardar(pendente("cap-2"))

        assertEquals(
            "recaptura e outra linha: o servidor a transforma em revisao nova",
            2,
            guarda.quantosPendentes(organizacao),
        )
    }

    @Test
    fun a_fila_e_escopada_pela_organizacao() {
        guarda.guardar(pendente("cap-daqui"))
        guarda.guardar(pendente("cap-de-outra", organizacao = outraOrganizacao))

        assertEquals(1, guarda.quantosPendentes(organizacao))
        assertEquals(1, guarda.quantosPendentes(outraOrganizacao))
        assertEquals("cap-daqui", guarda.pendentesDa(organizacao).single().captureId)
    }

    @Test
    fun o_confirmado_sai_e_so_ele() {
        guarda.guardar(pendente("cap-1"))
        guarda.guardar(pendente("cap-2"))

        guarda.apagarConfirmado("cap-1")

        assertEquals(listOf("cap-2"), guarda.pendentesDa(organizacao).map { it.captureId })
    }

    @Test
    fun a_fila_sai_do_mais_antigo_para_o_mais_novo() {
        guarda.guardar(pendente("cap-novo").copy(apuradoEm = 2_000_000_000_000L))
        guarda.guardar(pendente("cap-velho").copy(apuradoEm = 1_000_000_000_000L))

        assertEquals(
            listOf("cap-velho", "cap-novo"),
            guarda.pendentesDa(organizacao).map { it.captureId },
        )
    }

    @Test
    fun apagar_captura_que_nao_existe_nao_derruba_nada() {
        guarda.guardar(pendente("cap-1"))

        guarda.apagarConfirmado("cap-que-nunca-existiu")

        assertEquals(1, guarda.quantosPendentes(organizacao))
        assertNull(guarda.pendentesDa(outraOrganizacao).firstOrNull())
    }
}
