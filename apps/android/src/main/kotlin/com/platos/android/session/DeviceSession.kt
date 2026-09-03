package com.platos.android.session

/** O que a autenticacao devolveu, ja destilado pelo adaptador. */
sealed interface ResultadoDaEntrada {
    data object Autenticado : ResultadoDaEntrada
    data object CredencialRecusada : ResultadoDaEntrada
    data object SemRede : ResultadoDaEntrada
}

/** O que a consulta das organizacoes devolveu, ja destilado pelo adaptador. */
sealed interface ResultadoDasOrganizacoes {
    data class Chegaram(val organizacoes: List<Organizacao>) : ResultadoDasOrganizacoes
    data object SemRede : ResultadoDasOrganizacoes
    data object SessaoExpirada : ResultadoDasOrganizacoes
    data object Falhou : ResultadoDasOrganizacoes
}

/**
 * O que sobrevive ao fechamento do aplicativo.
 *
 * **Duas coisas, nomeadas uma a uma, e nao um registro generico de estado local.** Com dois itens um
 * registro seria abstracao prematura (regra 8); e apagar as duas em chamadas separadas e o que
 * permite a um teste de JVM afirmar que **as duas** foram apagadas ao sair. Uma unica `apagarTudo`
 * esconderia a distincao dentro do adaptador Android, onde nenhum teste desta camada a alcanca.
 */
interface SessaoGuardada {
    fun organizacaoEscolhida(): String?
    fun guardarOrganizacaoEscolhida(id: String)
    fun apagarOrganizacaoEscolhida()
    fun apagarCredencial()
}

/**
 * A sessao do aparelho: eventos entram, [DeviceState] sai.
 *
 * Kotlin puro. Nao conhece `supabase-kt`, cliente HTTP nem Compose — recebe resultados **ja
 * destilados** e decide o que a tela mostra. E a mesma fronteira que a fatia 3a desenhou entre
 * `vision/` e `omr/`, e que a 3c repetiu em `ScanSession`, pela mesma razao: o que decide precisa
 * ser testavel sem aparelho e sem servidor.
 *
 * A distincao entre credencial recusada, ausencia de rede e sessao expirada **entra por parametro**,
 * ja resolvida. Ela nao e feita aqui e nao e feita por texto de mensagem: o adaptador olha o lado da
 * falha — excecao de transporte contra resposta 401 — e diz qual das tres foi.
 */
class DeviceSession(private val guardada: SessaoGuardada) {

    var state: DeviceState = DeviceState.Entrada()
        private set

    /**
     * Abertura do aplicativo.
     *
     * Com sessao guardada a entrada e dispensada, e o aplicativo vai consultar — porque o nome da
     * organizacao vem da API e nao do disco. O que o disco guarda e **qual** organizacao esta ativa,
     * nunca como ela se chama.
     */
    fun abrir(temSessaoGuardada: Boolean) {
        state = if (temSessaoGuardada) DeviceState.Consultando else DeviceState.Entrada()
    }

    /** O resultado de uma tentativa de entrar. */
    fun aoEntrar(resultado: ResultadoDaEntrada) {
        state = when (resultado) {
            is ResultadoDaEntrada.Autenticado -> DeviceState.Consultando
            is ResultadoDaEntrada.CredencialRecusada ->
                DeviceState.Entrada(MotivoDeEntrada.CREDENCIAL_RECUSADA)
            is ResultadoDaEntrada.SemRede -> DeviceState.Entrada(MotivoDeEntrada.SEM_REDE)
        }
    }

    /**
     * O resultado da consulta das organizacoes.
     *
     * Sessao expirada volta para a entrada **no momento em que e detectada**, e nao numa chamada
     * posterior com mensagem que nao seja sobre a sessao.
     *
     * **Resultado que chega fora de [DeviceState.Consultando] e descartado**, no mesmo padrao que
     * [escolher] usa com [DeviceState.Escolhendo]. Dois casos reais, e nenhum deles e hipotetico:
     *
     * - o interceptador leva a sessao a expirada **durante** a chamada, e a mesma chamada retorna
     *   `Recusou(401)` logo depois. Sem a guarda, esse retorno viraria `Falhou` e sobrescreveria a
     *   expiracao com "nao foi possivel obter sua organizacao" — a tela mentiria sobre a causa;
     * - a consulta responde depois de o professor sair, e a tela de trabalho ressuscitaria por cima
     *   da entrada.
     *
     * A alternativa era quem chama conferir o status antes de repassar, e isso poria a regra na
     * fiacao — o defeito que a tarefa 4.4 existe para demonstrar. A regra e de estado, e mora onde o
     * estado mora.
     */
    fun aoConsultarOrganizacoes(resultado: ResultadoDasOrganizacoes) {
        if (state !is DeviceState.Consultando) return
        state = when (resultado) {
            is ResultadoDasOrganizacoes.Chegaram -> resolverOrganizacao(resultado.organizacoes)
            is ResultadoDasOrganizacoes.SessaoExpirada -> {
                guardada.apagarCredencial()
                DeviceState.Entrada(MotivoDeEntrada.SESSAO_EXPIRADA)
            }
            is ResultadoDasOrganizacoes.SemRede ->
                DeviceState.SemOrganizacao(FalhaDaConsulta.SEM_REDE)
            is ResultadoDasOrganizacoes.Falhou ->
                DeviceState.SemOrganizacao(FalhaDaConsulta.OUTRA)
        }
    }

    /** A escolha de quem segura o aparelho, entre as organizacoes apresentadas. */
    fun escolher(organizacao: Organizacao) {
        if (state !is DeviceState.Escolhendo) return
        guardada.guardarOrganizacaoEscolhida(organizacao.id)
        state = DeviceState.Ativa(organizacao)
    }

    /**
     * Sair.
     *
     * Apaga a credencial **e** a organizacao escolhida. A segunda nao e detalhe: o aparelho e
     * compartilhado entre escolas, e a escolha do usuario anterior sobrevivendo a troca de conta e
     * invisivel para quem entra depois.
     */
    fun sair() {
        guardada.apagarCredencial()
        guardada.apagarOrganizacaoEscolhida()
        state = DeviceState.Entrada(MotivoDeEntrada.SAIU)
    }

    /**
     * Uma organizacao so dispensa a escolha; mais de uma pede.
     *
     * Escolha guardada que nao esta mais na lista **volta a ser pedida**: perder o vinculo com uma
     * organizacao e possivel, e seguir com um identificador que a API nao devolve mais deixaria a
     * tela ativa sobre uma organizacao que o usuario nao tem.
     */
    private fun resolverOrganizacao(organizacoes: List<Organizacao>): DeviceState {
        if (organizacoes.isEmpty()) return DeviceState.SemOrganizacao(FalhaDaConsulta.OUTRA)

        val guardadaAgora = guardada.organizacaoEscolhida()
        val jaEscolhida = organizacoes.firstOrNull { it.id == guardadaAgora }
        if (jaEscolhida != null) return DeviceState.Ativa(jaEscolhida)

        if (guardadaAgora != null) guardada.apagarOrganizacaoEscolhida()

        val unica = organizacoes.singleOrNull()
        if (unica != null) {
            guardada.guardarOrganizacaoEscolhida(unica.id)
            return DeviceState.Ativa(unica)
        }

        return DeviceState.Escolhendo(organizacoes)
    }
}
