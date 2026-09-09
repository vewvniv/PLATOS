package com.platos.android.session

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.platos.android.BuildConfig
import com.platos.android.api.ApiPlatos
import com.platos.android.api.paraProvas
import com.platos.android.api.paraSessao
import com.platos.android.auth.AutenticacaoSupabase
import com.platos.android.auth.ResultadoDaAutenticacao
import com.platos.android.auth.paraSessao
import com.platos.android.net.clienteHttp
import com.platos.android.pacote.PacotesEmArquivo
import com.platos.android.pacote.obterPacote
import com.platos.android.scan.ScanActivity
import io.ktor.client.HttpClient
import kotlinx.coroutines.launch

/**
 * O `Activity` de lancamento: a entrada, e o que vem depois dela.
 *
 * **O que esta classe faz e ligar coisas e escolher tela**, como `ScanActivity` faz com a camera.
 * Nenhuma regra desta fatia e reimplementada aqui: quem decide se pede escolha e
 * `resolverOrganizacao`, quem decide que 401 e expiracao e `clienteApi`, quem escolhe frase sao
 * [mensagemDeEntrada] e [textoSemOrganizacao], e quem decide o que sair apaga e [DeviceSession.sair].
 *
 * **A sessao vive aqui, e o estado e derivado do que esta gravado** (decisao 10). Na recriacao —
 * rotacao, idioma, fonte, tema — [abrirSessao] reconstroi tudo a partir de [SessaoGuardada], que e a
 * unica fonte de verdade do que persiste. Nao ha `ViewModel` porque nao pode haver um segundo lugar
 * onde mora "o que o aparelho sabe": dois lugares que descrevem a mesma coisa divergem no dia em que
 * um dos dois esquecer de mudar.
 *
 * A orientacao **nao** e travada, ao contrario de `ScanActivity`. Travar esconderia o caso sem
 * resolve-lo: idioma, fonte e tema recriam o `Activity` de qualquer forma.
 */
class SessaoActivity : ComponentActivity() {

    private lateinit var guardada: SessaoGuardadaAndroid
    private lateinit var pacotes: PacotesEmArquivo
    private lateinit var http: HttpClient
    private lateinit var autenticacao: AutenticacaoSupabase
    private lateinit var api: ApiPlatos
    private lateinit var sessao: DeviceSession

    private var state by mutableStateOf<DeviceState>(DeviceState.Entrada())

    /**
     * Ha um pedido de entrada no ar.
     *
     * Nao esta em [DeviceState] de proposito: a maquina de estados descreve o que o aparelho
     * **sabe**, e isto e afordancia de tela. Ele se perde na recriacao, e o desfecho disso e voltar
     * ao formulario — que nao e carregamento sem desfecho, e sim um desfecho modesto.
     */
    private var enviando by mutableStateOf(false)

    /**
     * O preparo de prova em curso, ou `null` quando a tela e a de trabalho.
     *
     * **Maquina separada, e nao um estado a mais em [DeviceState]** (decisao 5 do `design.md`).
     * Nula significa "ninguem pediu para escanear ainda"; nao-nula, que o fluxo de escolha de prova
     * esta aberto sobre a organizacao ativa.
     *
     * Ela e descartada ao sair e ao trocar de organizacao, porque as provas apresentadas pertencem a
     * uma organizacao so — mante-la viva deixaria a lista da organizacao anterior na tela.
     */
    private var preparo by mutableStateOf<PreparoDaProva?>(null)

    /** O estado do preparo, espelhado para o Compose recompor. */
    private var estadoDaProva by mutableStateOf<EstadoDaProva>(EstadoDaProva.Listando)

    /** As provas da ultima listagem bem-sucedida, para o "escolher outra prova" da barragem. */
    private var provasApresentadas by mutableStateOf<List<ProvaPublicada>>(emptyList())

    /**
     * A ida a camera, e a volta dela.
     *
     * **`registerForActivityResult`, e nao `onResume`** (decisao 13). O que interessa e o fim da
     * `ScanActivity`, e `onResume` roda tambem na primeira abertura e em toda volta de dialogo do
     * sistema: distinguir "voltei da camera" pelo ciclo de vida pediria um sinalizador, e o
     * sinalizador seria um segundo lugar onde mora "onde o aparelho esta".
     *
     * **O codigo de resultado nao e lido.** A `ScanActivity` nao publica nada nesta fatia — nada e
     * persistido ate a 4b —, e o que este ponto precisa saber e que ela fechou.
     *
     * `preparo` nulo e a `Activity` recriada enquanto a camera estava aberta: o fluxo de escolha nao
     * sobrevive a recriacao de proposito (decisao 5), e a tela de trabalho e o desfecho.
     */
    private val escaneamento =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            val maquina = preparo ?: return@registerForActivityResult
            maquina.aoVoltarDoEscaneamento(provasApresentadas)
            estadoDaProva = maquina.state
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        guardada = SessaoGuardadaAndroid(applicationContext)
        // Quem sabe onde fica o armazenamento privado do aplicativo e o `Activity`; o cache so sabe
        // de arquivos, e e isso que o deixa verificavel na JVM (decisao 4 do `design.md`).
        pacotes = PacotesEmArquivo(java.io.File(filesDir, "packages"))
        sessao = DeviceSession(guardada, pacotes)
        http = clienteHttp()

        autenticacao = AutenticacaoSupabase(
            http = http,
            urlBase = BuildConfig.SUPABASE_URL,
            chaveAnonima = BuildConfig.SUPABASE_ANON_KEY,
        )

        api = ApiPlatos(
            http = http,
            urlBase = BuildConfig.API_URL,
            credencial = { guardada.credencial() },
            // Roda antes de a chamada retornar e na thread de quem chamou — `ClienteApiTest` afirma
            // as duas coisas pelo nome. E por isso que aplicar direto e correto: postar para a
            // principal inverteria a ordem, e a guarda da tarefa 3.8 passaria a descartar a
            // expiracao em vez do retorno atrasado.
            aoExpirarSessao = { aplicar(ResultadoDasOrganizacoes.SessaoExpirada) },
        )

        setContent { Tela() }

        abrirSessao()
    }

    /**
     * Fecha o cliente, e so ele.
     *
     * `ApiPlatos` deriva o dele com `HttpClient.config`, que compartilha o engine **sem** posse:
     * fechar o derivado nao fecharia o engine, e fechar os dois fecharia duas vezes. O escopo de
     * corrotina morre junto por conta do `lifecycleScope`, e com ele qualquer pedido no ar.
     */
    override fun onDestroy() {
        super.onDestroy()
        http.close()
    }

    // --- O estado, derivado e aplicado ---

    /**
     * A abertura, e tambem o "tentar de novo" da tela sem organizacao.
     *
     * Sao a mesma coisa, e nao por coincidencia: tentar de novo e reconstruir o estado a partir do
     * que esta gravado, que e exatamente o que a recriacao faz. Um `reconsultar()` em
     * [DeviceSession] seria uma transicao nova para dizer o que [DeviceSession.abrir] ja diz.
     */
    private fun abrirSessao() {
        sessao.abrir(temSessaoGuardada = guardada.credencial() != null)
        state = sessao.state
        if (sessao.state is DeviceState.Consultando) consultar()
    }

    private fun consultar() {
        lifecycleScope.launch {
            aplicar(api.organizacoes().paraSessao())
        }
    }

    private fun aplicar(resultado: ResultadoDasOrganizacoes) {
        sessao.aoConsultarOrganizacoes(resultado)
        state = sessao.state
    }

    private fun entrar(email: String, senha: String) {
        enviando = true
        lifecycleScope.launch {
            val resultado = autenticacao.entrar(email, senha)
            if (resultado is ResultadoDaAutenticacao.Autenticado) {
                guardada.guardarCredencial(resultado.credencial.accessToken)
            }
            sessao.aoEntrar(resultado.paraSessao())
            state = sessao.state
            enviando = false
            if (sessao.state is DeviceState.Consultando) consultar()
        }
    }

    private fun sair() {
        sessao.sair()
        state = sessao.state
        preparo = null
    }

    // --- O preparo da prova ---

    /** Abre o fluxo de escolha de prova sobre a organizacao ativa, e lista. */
    private fun prepararProva() {
        val maquina = PreparoDaProva()
        preparo = maquina
        listarProvas()
    }

    private fun listarProvas() {
        val maquina = preparo ?: return
        val organizacao = organizacaoAtiva() ?: return

        maquina.listar()
        estadoDaProva = maquina.state

        lifecycleScope.launch {
            maquina.aoListar(api.provas(organizacao).paraProvas())
            estadoDaProva = maquina.state
            (maquina.state as? EstadoDaProva.Escolhendo)?.let { provasApresentadas = it.provas }
        }
    }

    private fun escolherProva(prova: ProvaPublicada) {
        val maquina = preparo ?: return
        val organizacao = organizacaoAtiva() ?: return

        maquina.escolher(prova)
        estadoDaProva = maquina.state
        if (maquina.state !is EstadoDaProva.Preparando) return

        lifecycleScope.launch {
            maquina.aoObterPacote(obterPacote(pacotes, api, organizacao, prova))
            estadoDaProva = maquina.state
        }
    }

    /**
     * O gate passou: abre o escaneamento passando o **endereco** do pacote, e nao o pacote.
     *
     * Sao ~100 KB, e uma transacao Binder desse tamanho derruba o aplicativo em vez de falhar com
     * motivo. `ScanActivity` rele do cache e reconfere, que e o que faz o requisito "reconferido a
     * cada leitura" valer no caminho real — e o que a faz sobreviver a morte do processo.
     */
    private fun escanear(pronta: EstadoDaProva.Pronta) {
        val organizacao = organizacaoAtiva() ?: return
        escaneamento.launch(
            Intent(this, ScanActivity::class.java)
                .putExtra(ScanActivity.EXTRA_ORGANIZACAO, organizacao)
                .putExtra(ScanActivity.EXTRA_CONTENT_HASH, pronta.contentHash),
        )
    }

    private fun organizacaoAtiva(): String? = (state as? DeviceState.Ativa)?.organizacao?.id

    // --- Estado para tela ---

    /**
     * O `when` e **exaustivo e sem `else`**, e essa e a unica razao de ele estar escrito assim.
     *
     * Este e o primeiro lugar onde os cinco estados se encontram. Um estado novo em [DeviceState]
     * tem de quebrar a compilacao aqui — com `else`, ele cairia numa tela por padrao, e tela errada
     * apresentada com confianca e o modo de falha desta fatia inteira. Mesma razao do `when` de
     * [mensagemDeEntrada].
     */
    @Composable
    private fun Tela() {
        when (val atual = state) {
            is DeviceState.Entrada -> EntradaScreen(
                motivo = atual.motivo,
                enviando = enviando,
                onEntrar = ::entrar,
            )

            is DeviceState.Consultando -> ConsultandoScreen()

            is DeviceState.Escolhendo -> EscolhaScreen(
                organizacoes = atual.organizacoes,
                onEscolher = {
                    sessao.escolher(it)
                    state = sessao.state
                },
            )

            is DeviceState.Ativa -> when (val fluxo = preparo) {
                null -> TrabalhoScreen(
                    organizacao = atual.organizacao,
                    onEscanear = ::prepararProva,
                    onSair = ::sair,
                )

                else -> TelaDoPreparo(fluxo)
            }

            is DeviceState.SemOrganizacao -> SemOrganizacaoScreen(
                texto = textoSemOrganizacao(atual.falha),
                onTentarDeNovo = ::abrirSessao,
                onSair = ::sair,
            )
        }
    }

    /**
     * O `when` do preparo e **exaustivo e sem `else`**, pela mesma razao do de [DeviceState].
     *
     * Estado novo em [EstadoDaProva] quebra a compilacao aqui em vez de cair numa tela por padrao.
     * Tela errada apresentada com confianca e o modo de falha desta fatia inteira.
     */
    @Composable
    private fun TelaDoPreparo(fluxo: PreparoDaProva) {
        when (val atual = estadoDaProva) {
            is EstadoDaProva.Listando -> ConsultandoScreen()

            is EstadoDaProva.Escolhendo -> EscolhaDaProvaScreen(
                provas = atual.provas,
                onEscolher = ::escolherProva,
                onSair = ::sair,
            )

            is EstadoDaProva.SemProvaPublicada -> SemProvaScreen(
                onTentarDeNovo = ::listarProvas,
                onSair = ::sair,
            )

            is EstadoDaProva.ListagemFalhou -> BarragemScreen(
                titulo = "Nao foi possivel listar as provas",
                texto = textoDaListagem(atual.falha),
                onTentarDeNovo = ::listarProvas,
                onVoltar = { preparo = null },
            )

            is EstadoDaProva.Preparando -> PreparandoScreen(atual.prova, "Preparando a prova…")

            is EstadoDaProva.Pronta -> {
                // Efeito, e nao chamada no corpo do `@Composable`: navegar durante a composicao
                // dispararia de novo a cada recomposicao. A chave e o hash, entao a mesma prova
                // conferida nao reabre a camera sozinha.
                //
                // Voltar da camera **nao** passa por aqui: quem devolve a escolha e o resultado do
                // `escaneamento`, e nao este efeito — que, com a composicao viva na mesma chave, nao
                // roda de novo. Era essa a tela sem saida da 9b.2.
                androidx.compose.runtime.LaunchedEffect(atual.contentHash) { escanear(atual) }
                PreparandoScreen(atual.prova, "Abrindo a camera…")
            }

            is EstadoDaProva.Barrada -> BarragemScreen(
                titulo = atual.prova.titulo,
                texto = textoDaBarragem(atual.motivo),
                onTentarDeNovo = { escolherProva(atual.prova) },
                onVoltar = { fluxo.voltarAEscolha(provasApresentadas); estadoDaProva = fluxo.state },
            )
        }
    }
}
