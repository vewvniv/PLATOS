package com.platos.android.session

import com.platos.android.pacote.PacotesGuardados

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
class DeviceSession(
    private val guardada: SessaoGuardada,
    private val pacotes: PacotesGuardados,
    private val visoes: VisoesGuardadas,
) {

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
        // **A expiracao e a excecao a guarda, e precisa ser.** Ela nao e resultado de consulta: vem
        // do interceptador de `clienteApi` e pode chegar de QUALQUER chamada autenticada. Ate a
        // 4a-zero todas aconteciam em `Consultando`, e trata-la aqui dentro era seguro; a fatia 4a
        // acrescentou `provas()` e `pacote()`, que rodam com o aparelho em `Ativa`, e para elas a
        // guarda descartava o 401 em silencio — credencial expirada seguia guardada e a tela
        // oferecia um "tentar de novo" que falharia para sempre (tarefa 9b.1, decisao 12).
        //
        // Sair continua sendo sair: expiracao que chega depois de o professor ja estar na entrada
        // nao reescreve o motivo, senao a tela mentiria para quem saiu por vontade propria.
        if (resultado is ResultadoDasOrganizacoes.SessaoExpirada) {
            if (state is DeviceState.Entrada) return
            guardada.apagarCredencial()
            state = DeviceState.Entrada(MotivoDeEntrada.SESSAO_EXPIRADA)
            return
        }

        if (state !is DeviceState.Consultando) return
        state = when (resultado) {
            is ResultadoDasOrganizacoes.Chegaram -> resolverOrganizacao(resultado.organizacoes)
            is ResultadoDasOrganizacoes.SessaoExpirada ->
                error("tratada acima; o ramo existe para o `when` seguir exaustivo sem `else`")
            is ResultadoDasOrganizacoes.SemRede -> semRede()
            is ResultadoDasOrganizacoes.Falhou ->
                DeviceState.SemOrganizacao(FalhaDaConsulta.OUTRA)
        }
    }

    /**
     * O servidor **nao respondeu** — e isso nao e o mesmo que ele ter respondido sem a organizacao.
     *
     * Ate esta fatia os dois casos terminavam iguais, e o registro do §16 chama isso de risco de
     * entrega da §10: com pacote conferido em disco, o aplicativo parava no arranque a dois passos
     * dele. **O caso que a decisao 10 da 4a-zero decidiu continua decidido**: quando o servidor
     * responde e o vinculo nao esta la, a escolha guardada cai — quem faz isso e `resolverOrganizacao`,
     * e nao este ramo.
     *
     * Sem organizacao guardada, ou sem visao dela, nao ha o que apresentar: a tela explica e pede
     * rede uma vez. Visao guardada e a **unica** origem do nome aqui; nada e construido.
     */
    private fun semRede(): DeviceState {
        val escolhida = guardada.organizacaoEscolhida()
            ?: return DeviceState.SemOrganizacao(FalhaDaConsulta.SEM_REDE)

        val visao = visoes.ler(escolhida)
            ?: return DeviceState.SemOrganizacao(FalhaDaConsulta.SEM_REDE)

        return DeviceState.Ativa(visao.organizacao, Procedencia.Cacheada(visao.vistaEm))
    }

    /** A escolha de quem segura o aparelho, entre as organizacoes apresentadas. */
    fun escolher(organizacao: Organizacao) {
        if (state !is DeviceState.Escolhendo) return
        guardada.guardarOrganizacaoEscolhida(organizacao.id)
        state = DeviceState.Ativa(organizacao, Procedencia.Fresca)
    }

    /**
     * Sair.
     *
     * Apaga a credencial, a organizacao escolhida, **a visao guardada e os pacotes guardados sob
     * ela**. Nenhuma das quatro e detalhe: o aparelho e compartilhado entre escolas, e o que o
     * usuario anterior deixou sobrevivendo a troca de conta e invisivel para quem entra depois. A
     * fatia 4a-zero pagou esse defeito com a organizacao; o pacote e o mesmo defeito um nivel
     * abaixo, e por isso ADR-0013 manda cada fatia **nomear o que apaga** em vez de confiar num
     * requisito generico de limpar dados locais.
     *
     * A visao entrou nesta lista nesta fatia, e ela e a **unica** das quatro que aparece em tela: sem
     * este apagamento, quem entrasse depois no mesmo aparelho e abrisse sem rede leria o nome da
     * organizacao anterior e a lista de provas dela.
     *
     * **A organizacao e lida antes de ser apagada.** Invertendo a ordem, o identificador ja teria
     * sumido quando o cache fosse apagado, e o apagamento aconteceria sobre `null` — sem estourar, e
     * sem apagar nada.
     */
    fun sair() {
        val organizacao = organizacaoAtiva()

        guardada.apagarCredencial()
        guardada.apagarOrganizacaoEscolhida()
        if (organizacao != null) {
            visoes.apagarDaOrganizacao(organizacao)
            pacotes.apagarDaOrganizacao(organizacao)
        }

        state = DeviceState.Entrada(MotivoDeEntrada.SAIU)
    }

    /**
     * Qual organizacao esta ativa neste instante, para efeito de apagamento.
     *
     * O estado vem primeiro porque ele e o mais recente; o disco responde quando a tela nao esta em
     * [DeviceState.Ativa] — sair a partir da escolha, por exemplo. Nao havendo nenhum dos dois, nao
     * houve pull, porque puxar exige organizacao ativa.
     */
    private fun organizacaoAtiva(): String? =
        (state as? DeviceState.Ativa)?.organizacao?.id ?: guardada.organizacaoEscolhida()

    /**
     * Uma organizacao so dispensa a escolha; mais de uma pede.
     *
     * Escolha guardada que nao esta mais na lista **volta a ser pedida**: perder o vinculo com uma
     * organizacao e possivel, e seguir com um identificador que a API nao devolve mais deixaria a
     * tela ativa sobre uma organizacao que o usuario nao tem.
     *
     * **Resposta sem organizacao nenhuma e revogacao tambem**, e isto e decisao desta fatia, nao
     * heranca: a condicao que o requisito escreve — "o servidor respondeu e a organizacao nao esta
     * mais entre as do usuario" — e satisfeita por uma lista vazia do mesmo jeito que por uma lista
     * que nao a traz. Tratar a lista vazia como caso de falha, e nao de revogacao, deixaria o
     * gabarito em cache exatamente na revogacao mais dura, a de quem perdeu todos os vinculos. O
     * desfecho de tela nao muda ([DeviceState.SemOrganizacao]); o que muda e o que sai do disco.
     */
    private fun resolverOrganizacao(organizacoes: List<Organizacao>): DeviceState {
        val guardadaAgora = guardada.organizacaoEscolhida()

        if (organizacoes.isEmpty()) {
            if (guardadaAgora != null) revogar(guardadaAgora)
            return DeviceState.SemOrganizacao(FalhaDaConsulta.OUTRA)
        }

        val jaEscolhida = organizacoes.firstOrNull { it.id == guardadaAgora }
        if (jaEscolhida != null) return DeviceState.Ativa(jaEscolhida, Procedencia.Fresca)

        if (guardadaAgora != null) revogar(guardadaAgora)

        val unica = organizacoes.singleOrNull()
        if (unica != null) {
            guardada.guardarOrganizacaoEscolhida(unica.id)
            return DeviceState.Ativa(unica, Procedencia.Fresca)
        }

        return DeviceState.Escolhendo(organizacoes)
    }

    /**
     * O vinculo caiu, e quem disse foi o servidor.
     *
     * Apaga a escolha, a **visao** e os **pacotes** daquela organizacao. A escolha sozinha ja caia
     * desde a decisao 10 da 4a-zero; cair sem levar o resto deixava no aparelho o **gabarito** de uma
     * organizacao que a instituicao ja revogou — e o gabarito nao aparece em tela nenhuma, entao
     * ninguem tem como notar que ficou.
     *
     * **A ausencia de teto de tempo depende deste apagamento.** A decisao 2 do `design.md` recusou
     * expirar a visao por calendario, e o que ela poe no lugar e isto: a visao vale enquanto o
     * servidor nao disser o contrario, e no instante em que ele disser, o que estava guardado sob a
     * organizacao vai junto. Sem esta funcao, "sem prazo de validade" viraria "para sempre".
     *
     * E o mesmo apagamento de [sair], com duas diferencas: a credencial continua valendo — o usuario
     * segue sendo ele mesmo, e pode ter outras organizacoes —, e quem manda sair da organizacao e o
     * servidor, e nao o professor.
     */
    private fun revogar(organizacao: String) {
        guardada.apagarOrganizacaoEscolhida()
        visoes.apagarDaOrganizacao(organizacao)
        pacotes.apagarDaOrganizacao(organizacao)
    }
}
