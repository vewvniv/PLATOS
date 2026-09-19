package com.platos.android.outbox

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.platos.android.session.SessaoGuardadaAndroid
import com.platos.domain.capture.QuestionAnswer
import com.platos.domain.scoring.ObjectiveScore
import com.platos.domain.scoring.QuestionOutcome
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * O pendente de um membro sobe com a credencial de **outro**, no mesmo aparelho.
 *
 * **Por que esta classe existe.** `docs/cobertura-slice-4b-outbox-de-resultado.md` §5.1.2 registrava
 * que quem reabriu a sessao na conferencia foi o mesmo usuario, e que o escopo por organizacao "e o
 * mesmo caminho de codigo com outra credencial — mas isso e inferencia, e nao medicao". Aqui a parte
 * que o **aparelho** decide deixa de ser inferida.
 *
 * **O oraculo e o cabecalho que saiu, e nao o estado guardado.** Afirmar "a credencial guardada agora
 * e a de B" mediria o armazenamento, que ja tem teste proprio, e passaria numa implementacao que
 * guardasse a credencial do autor junto do pendente e a usasse no envio — que e exatamente a hipotese
 * sob teste. O que se le e o `Authorization` que chegou ao transporte, produzido pelo caminho inteiro.
 *
 * **As duas credenciais sao distinguiveis, e a assercao diz qual chegou.** "Chegou alguma" e
 * indistinguivel entre o caminho certo e o defeito.
 *
 * **O que esta classe NAO prova, e fica dito (P8):** que o servidor aceita o envio de B e recusa o de
 * A. Isso e do servidor, esta verificado em `:apps:api:test`, e aqui e **simulado** pelo `MockEngine`.
 * A conferencia com duas contas reais contra producao continua item com dono.
 */
@RunWith(AndroidJUnit4::class)
class SegundoMembroInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val organizacao = "01a06ba4-cb43-7d97-842d-165352d010b5"
    private val prova = "prova-referencia-slice-1"

    private val credencialDeA = "token-do-membro-a"
    private val credencialDeB = "token-do-membro-b"

    private lateinit var guardada: SessaoGuardadaAndroid

    /** Os cabecalhos que o transporte viu, em ordem. E o unico oraculo desta classe. */
    private val autorizacoesVistas = mutableListOf<String?>()

    @Before
    fun prepararAparelho() {
        // A costura usa a base de producao, entao o teste comeca com ela vazia — e nao com "o que
        // estiver la". Uma fila herdada de outra execucao faria as contagens abaixo medirem outra
        // coisa.
        //
        // **Reiniciar antes de apagar, e nao depois.** `abrir` guarda a instancia no processo;
        // apagar o arquivo sem esquecer a referencia deixaria a proxima chamada devolvendo uma
        // instancia que aponta para um arquivo que nao existe mais.
        ResultadosEmRoom.reiniciarParaTeste()
        context.deleteDatabase(NOME_DA_BASE)
        guardada = SessaoGuardadaAndroid(context)
        guardada.apagarCredencial()
        autorizacoesVistas.clear()
    }

    @After
    fun limpar() {
        ResultadosEmRoom.reiniciarParaTeste()
        context.deleteDatabase(NOME_DA_BASE)
        guardada.apagarCredencial()
    }

    /**
     * O caminho inteiro: A escaneia, A sai, B entra, e o pendente de A sobe com a credencial de B.
     */
    @Test
    fun o_pendente_de_um_membro_sobe_com_a_credencial_de_outro() = runBlocking {
        gravarPendente("cap-do-membro-a")
        guardada.guardarCredencial(credencialDeA)

        // O canario (P13): a fila precisa ter uma linha **antes**, ou "a fila drenou" passaria numa
        // base vazia e o cabecalho nunca seria produzido.
        assertEquals(1, quantosPendentes())

        // A saida de A. Nao e so sobrescrever o token: o requisito fala de **outro membro depois de o
        // primeiro sair**, e sobrescrever mediria "duas credenciais em sequencia".
        guardada.apagarCredencial()
        assertEquals("sair apagou o pendente, e a classe H proibe isso", 1, quantosPendentes())

        guardada.guardarCredencial(credencialDeB)
        val passada = passadaDeEnvio(
            context = context,
            organizacao = organizacao,
            engine = engineQueResponde(HttpStatusCode.OK),
            urlBase = URL_DE_TESTE,
        )

        assertNotNull("sem credencial guardada a passada nem tentou", passada)
        assertEquals(
            "o envio saiu com a credencial de quem escaneou, e nao com a de quem esta na sessao",
            listOf("Bearer $credencialDeB"),
            autorizacoesVistas,
        )
        assertEquals(1, passada!!.resumo.confirmados)
        assertEquals("o confirmado continua no aparelho", 0, quantosPendentes())
    }

    /**
     * A recusa ao membro revogado nao apaga, e o membro seguinte envia o **mesmo** pendente.
     *
     * As duas passadas sao da mesma linha, e e isso que o requisito descreve: o usuario cujo vinculo
     * foi revogado nao consegue envia-la, e ela espera outro membro abrir sessao neste aparelho.
     */
    @Test
    fun recusa_ao_membro_revogado_preserva_o_pendente_para_o_seguinte() = runBlocking {
        gravarPendente("cap-esperando-outro-membro")
        guardada.guardarCredencial(credencialDeA)

        val recusada = passadaDeEnvio(
            context = context,
            organizacao = organizacao,
            engine = engineQueResponde(HttpStatusCode.Forbidden),
            urlBase = URL_DE_TESTE,
        )

        assertEquals(1, recusada!!.resumo.recusados)
        assertEquals("403 e decisao do servidor, e nao falha dele", 0, recusada.resumo.transitorios)
        assertEquals("a recusa por vinculo revogado apagou o pendente", 1, quantosPendentes())

        guardada.apagarCredencial()
        guardada.guardarCredencial(credencialDeB)

        val aceita = passadaDeEnvio(
            context = context,
            organizacao = organizacao,
            engine = engineQueResponde(HttpStatusCode.OK),
            urlBase = URL_DE_TESTE,
        )

        assertEquals(
            "as duas passadas precisam ter saido com credenciais diferentes",
            listOf("Bearer $credencialDeA", "Bearer $credencialDeB"),
            autorizacoesVistas,
        )
        assertEquals(1, aceita!!.resumo.confirmados)
        assertEquals(0, quantosPendentes())
    }

    /**
     * A guarda de vacuidade da costura (P13).
     *
     * Com a fila vazia, nenhum pedido sai. Sem isto, os dois testes acima passariam contra uma costura
     * que nunca envia nada — `autorizacoesVistas` vazio seria lido como "nada de errado".
     */
    @Test
    fun fila_vazia_nao_produz_pedido_nenhum() = runBlocking {
        guardada.guardarCredencial(credencialDeA)

        val passada = passadaDeEnvio(
            context = context,
            organizacao = organizacao,
            engine = engineQueResponde(HttpStatusCode.OK),
            urlBase = URL_DE_TESTE,
        )

        assertTrue("fila vazia produziu pedido", autorizacoesVistas.isEmpty())
        assertEquals(0, passada!!.resumo.confirmados)
        assertEquals(0, passada.resumo.pendentesRestantes)
    }

    private fun engineQueResponde(status: HttpStatusCode) = MockEngine { requisicao ->
        autorizacoesVistas += requisicao.headers[HttpHeaders.Authorization]
        respond(
            content = "",
            status = status,
            headers = headersOf(HttpHeaders.ContentType, "application/json"),
        )
    }

    /**
     * **Nao fecha a base, e a ausencia do `close()` e o requisito.**
     *
     * Estas duas funcoes fechavam a instancia num `finally`. Era inofensivo enquanto `abrir`
     * devolvia uma instancia nova por chamada — cada chamador era dono da sua. Com o acessador
     * unico, a instancia e **a** do processo, e fecha-la aqui a fecha para o worker e para as duas
     * `Activity`: a chamada seguinte recebe a mesma referencia, ja fechada, e estoura com
     * `IllegalStateException: Database is closed`.
     *
     * Foi o que aconteceu, e o conserto **nao** e `abrir` reconstruir quando acha a instancia
     * fechada: isso esconderia um chamador fechando a base compartilhada, que e precisamente a
     * classe de defeito que esta mudanca existe para tornar impossivel. O conserto e o chamador
     * parar de fechar — nenhum chamador de producao fecha, e este teste passa a exercitar a mesma
     * topologia que eles.
     */
    private fun gravarPendente(captureId: String) {
        val base = ResultadosEmRoom.abrir(context)
        ResultadosEmRoom(base.pendentes()).guardar(
            ResultadoPendente(
                captureId = captureId,
                organizacao = organizacao,
                prova = prova,
                studentToken = "tok-a",
                apuradoEm = 1_789_646_400_000L,
                nota = nota(),
            ),
        )
    }

    private fun quantosPendentes(): Int {
        val base = ResultadosEmRoom.abrir(context)
        return ResultadosEmRoom(base.pendentes()).quantosPendentes(organizacao)
    }

    private fun nota(): ObjectiveScore {
        val marcada = QuestionOutcome("q01", QuestionAnswer.Marcada("q01", "A"), 1, 1)
        val branca = QuestionOutcome("q02", QuestionAnswer.EmBranco("q02"), 1, 0)
        return ObjectiveScore(
            packageHash = "a".repeat(64),
            variantId = "v1",
            points = 1,
            maxScore = 2,
            pending = emptyList(),
            outcomes = listOf(marcada, branca),
        )
    }

    private companion object {
        const val NOME_DA_BASE = "outbox.db"
        const val URL_DE_TESTE = "https://api.invalido"
    }
}
