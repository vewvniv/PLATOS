package com.platos.android.session

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.platos.android.BuildConfig
import com.platos.android.api.ApiPlatos
import com.platos.android.api.paraSessao
import com.platos.android.auth.AutenticacaoSupabase
import com.platos.android.auth.ResultadoDaAutenticacao
import com.platos.android.auth.paraSessao
import com.platos.android.net.clienteHttp
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        guardada = SessaoGuardadaAndroid(applicationContext)
        sessao = DeviceSession(guardada)
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
    }

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

            is DeviceState.Ativa -> TrabalhoScreen(
                organizacao = atual.organizacao,
                onEscanear = { startActivity(Intent(this, ScanActivity::class.java)) },
                onSair = ::sair,
            )

            is DeviceState.SemOrganizacao -> SemOrganizacaoScreen(
                texto = textoSemOrganizacao(atual.falha),
                onTentarDeNovo = ::abrirSessao,
                onSair = ::sair,
            )
        }
    }
}
