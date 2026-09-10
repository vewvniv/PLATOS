package com.platos.android.session

import com.platos.android.pacote.MotivoDaRecusa
import com.platos.android.pacote.PacotesGuardados
import com.platos.android.render.RendererContract
import com.platos.domain.exam.ExamPackage

/** O que a listagem de provas devolveu, ja destilado pelo adaptador. */
sealed interface ResultadoDasProvas {
    data class Chegaram(val provas: List<ProvaPublicada>) : ResultadoDasProvas
    data object SemRede : ResultadoDasProvas
    data object Falhou : ResultadoDasProvas
}

/**
 * O que a obtencao do pacote devolveu, ja destilada.
 *
 * Nao ha `SessaoExpirada` aqui, e a ausencia e deliberada: o 401 e tratado num ponto unico pelo
 * interceptador de `clienteApi`, que leva a **sessao do aparelho** de volta a entrada. Repetir a
 * decisao nesta maquina seria a duplicacao que a tarefa 4.4 da fatia 4a-zero existiu para
 * demonstrar.
 */
sealed interface ResultadoDoPacote {
    data class Conferido(val pacote: ExamPackage, val contentHash: String) : ResultadoDoPacote
    data class Recusado(val motivo: MotivoDaRecusa) : ResultadoDoPacote
    data object Ausente : ResultadoDoPacote
    data object SemRede : ResultadoDoPacote
}

/**
 * O preparo de uma prova para escanear: eventos entram, [EstadoDaProva] sai.
 *
 * Kotlin puro, como [DeviceSession] e `ScanSession`. Nao conhece cliente HTTP, sistema de arquivos
 * nem Compose — recebe resultados **ja destilados** e decide o que a tela mostra.
 *
 * **Por que nao dentro de [DeviceSession].** Aquela responde "quem entrou e sob qual organizacao".
 * Escolher prova, obter pacote, conferir e barrar e outra pergunta, com outros estados e outros
 * motivos de falha; somar as duas daria uma classe que decide sobre credencial e sobre hash no mesmo
 * `when`. E a mesma fronteira que a 3a desenhou entre `vision/` e `omr/`, pela mesma razao.
 *
 * A organizacao ativa e pre-condicao, e nao estado daqui: sem ela nao ha o que listar, e quem sabe
 * disso e [DeviceSession]. Ela entra por construtor porque a visao guardada e **por organizacao**, e
 * porque um fluxo de preparo pertence a uma organizacao so — trocar de organizacao descarta o fluxo.
 *
 * **[visoes] e porta, e a escrita mora aqui e nao na `Activity`.** Foi a fiacao entre maquina pura e
 * tela que produziu os dois defeitos da fatia 4a (9b.1 e 9b.2), e nenhum teste desta base a alcanca.
 * Com a gravacao na maquina, "listagem que chega grava a visao" e "listagem que falha nao grava" sao
 * cenarios de JVM.
 */
class PreparoDaProva(
    private val visoes: VisoesGuardadas,
    private val pacotes: PacotesGuardados,
    private val organizacao: Organizacao,
) {

    var state: EstadoDaProva = EstadoDaProva.Listando
        private set

    /**
     * A ultima escolha apresentada, para voltar a ela sem consultar nada.
     *
     * Mora aqui, e nao na tela: e a maquina que sabe o que foi apresentado, e voltar depois de uma
     * barragem ou do escaneamento nao pode depender de rede — a volta e em sala, e sala e onde nao
     * ha sinal.
     */
    private var ultimaEscolha: EstadoDaProva? = null

    /** Uma nova listagem foi pedida. Volta ao inicio, descartando o que estivesse na tela. */
    fun listar() {
        state = EstadoDaProva.Listando
    }

    /**
     * O resultado da listagem, e a gravacao da visao quando ele chega.
     *
     * **Lista vazia e [EstadoDaProva.SemProvaPublicada], e falha e [EstadoDaProva.ListagemFalhou].**
     * Confundir os dois faria a tela afirmar que a organizacao nao tem prova quando o que houve foi
     * a consulta nao chegar — uma afirmacao sobre o mundo que a falha nao autoriza.
     *
     * **So listagem que chegou grava.** Falha nao grava nada, e a razao nao e economia: gravar no
     * caminho de falha substituiria uma visao boa por uma visao vazia, e o aparelho sem rede passaria
     * a nao saber o que ja sabia. Lista vazia que **chegou** grava, e grava vazia — "esta organizacao
     * nao tem prova publicada" e uma afirmacao sobre o mundo, e o aparelho pode reproduzi-la offline.
     *
     * [agora] entra por parametro, em milissegundos de epoch, em vez de sair de um relogio aqui
     * dentro: e o instante que a tela apresenta ao lado do nome, e relogio interno o tornaria
     * impossivel de afirmar num teste.
     */
    fun aoListar(resultado: ResultadoDasProvas, agora: Long) {
        if (state !is EstadoDaProva.Listando) return
        state = when (resultado) {
            is ResultadoDasProvas.Chegaram -> {
                visoes.guardar(VisaoDaOrganizacao(organizacao, resultado.provas, agora))
                apresentar(resultado.provas, Procedencia.Fresca)
            }

            // **Sem resposta cai na visao guardada; resposta que nao serve, nao.** Um 500 nao e
            // afirmacao sobre o mundo nem ausencia de servidor: o aparelho esta alcancando a rede, e
            // o que cabe e tentar de novo. So a ausencia de resposta autoriza falar pelo passado.
            is ResultadoDasProvas.SemRede -> semResposta()
            is ResultadoDasProvas.Falhou -> EstadoDaProva.ListagemFalhou(FalhaDaListagem.OUTRA)
        }
        if (state is EstadoDaProva.Escolhendo || state is EstadoDaProva.SemProvaPublicada) {
            ultimaEscolha = state
        }
    }

    /**
     * A listagem nao chegou ao servidor: apresenta o que a ultima consulta devolveu.
     *
     * **Lista vazia guardada continua sendo "nao ha prova publicada"**, e nao vira falha: o servidor
     * ja afirmou isso um dia, e a visao carrega a idade dessa afirmacao para a tela dizer de quando
     * ela e. Sem visao nenhuma nao ha o que apresentar, e ai sim e falha com motivo.
     */
    private fun semResposta(): EstadoDaProva {
        val visao = visoes.ler(organizacao.id)
            ?: return EstadoDaProva.ListagemFalhou(FalhaDaListagem.SEM_REDE)

        return apresentar(visao.provas, Procedencia.Cacheada(visao.vistaEm))
    }

    /**
     * Monta o que a tela apresenta, com a presenca do pacote de cada prova.
     *
     * A presenca e perguntada ao cache **sem abrir o pacote**: aqui ela e dica para o professor
     * escolher, e quem julga o conteudo continua sendo o gate, na escolha.
     */
    private fun apresentar(provas: List<ProvaPublicada>, procedencia: Procedencia): EstadoDaProva {
        if (provas.isEmpty()) return EstadoDaProva.SemProvaPublicada(procedencia)

        return EstadoDaProva.Escolhendo(
            provas = provas.map {
                ProvaApresentada(it, pacotes.temConteudo(organizacao.id, it.contentHash))
            },
            procedencia = procedencia,
        )
    }

    /**
     * A escolha de quem segura o aparelho, entre as provas apresentadas.
     *
     * **Prova que nao esta na lista apresentada e ignorada.** Nao e defesa contra o usuario: e o que
     * garante que a tela nunca abra o preparo de uma prova que a consulta nao devolveu, que e o
     * mesmo principio pelo qual o nome da organizacao vem da API.
     */
    fun escolher(prova: ProvaPublicada) {
        val atual = state
        if (atual !is EstadoDaProva.Escolhendo) return
        if (atual.provas.none { it.prova == prova }) return
        state = EstadoDaProva.Preparando(prova)
    }

    /**
     * O resultado da obtencao do pacote, seguido do **gate de pre-voo**.
     *
     * O gate e binario por construcao (ADR-0009): com pacote conferido a sessao abre, sem ele nao
     * abre. Nao existe pacote parcialmente presente para uma prova.
     *
     * **A versao e conferida aqui, e nao so na renderizacao.** Ate esta fatia,
     * `min_renderer_version` era imposto ao desenhar e apenas declarado no caminho de captura — um
     * pacote que o aplicativo nao desenha por inteiro tambem nao e um pacote que ele deva medir.
     */
    fun aoObterPacote(resultado: ResultadoDoPacote) {
        val atual = state
        if (atual !is EstadoDaProva.Preparando) return

        state = when (resultado) {
            is ResultadoDoPacote.Conferido -> passarPeloGate(atual.prova, resultado)
            is ResultadoDoPacote.Recusado ->
                EstadoDaProva.Barrada(atual.prova, MotivoDaBarragem.CONFERENCIA_FALHOU)

            is ResultadoDoPacote.Ausente ->
                EstadoDaProva.Barrada(atual.prova, MotivoDaBarragem.PACOTE_AUSENTE)

            is ResultadoDoPacote.SemRede ->
                EstadoDaProva.Barrada(atual.prova, MotivoDaBarragem.SEM_REDE)
        }
    }

    /**
     * O escaneamento fechou e a tela do preparo voltou.
     *
     * **Evento, e nao inferencia da tela** (decisao 13). [EstadoDaProva.Pronta] diz "o gate passou e
     * a camera vai abrir"; ela nao diz se a camera **ja foi**. Sem este evento o preparo ficava
     * parado em `Pronta` com a camera fechada, desenhando a tela de preparo sem nenhuma saida — foi
     * o que a tarefa 9b.2 encontrou em aparelho.
     *
     * **O destino e a escolha, e nao um estado novo.** A `ScanActivity` escaneia folha apos folha
     * sem voltar, entao voltar significa "terminei com esta prova". As [provas] sao as que ja foram
     * apresentadas, e e por isso que a volta **nao consulta nada**: a sala e onde nao ha sinal, e
     * escolher a mesma prova de novo cai no pacote ja guardado.
     */
    fun aoVoltarDoEscaneamento() {
        // A guarda e a dos outros eventos desta maquina: volta atrasada — a `Activity` pode ser
        // recriada com a camera aberta — nao reescreve um preparo que ja seguiu.
        if (state !is EstadoDaProva.Pronta) return
        voltarAEscolha()
    }

    /**
     * Volta a escolha apresentada, depois de uma barragem ou do escaneamento.
     *
     * Sem consultar nada: quem lembra o que foi apresentado e [ultimaEscolha]. Nao havendo nada
     * lembrado — caso que so acontece se a volta chegar antes de qualquer listagem —, o desfecho e
     * uma listagem nova, que e o que a tela ja sabia fazer.
     */
    fun voltarAEscolha() {
        state = ultimaEscolha ?: EstadoDaProva.Listando
    }

    private fun passarPeloGate(
        prova: ProvaPublicada,
        conferido: ResultadoDoPacote.Conferido,
    ): EstadoDaProva {
        // O maximo entre as variantes, e nao a primeira: basta uma exigir mais do que o aplicativo
        // desenha para a folha impressa daquela variante ser ilegivel.
        val exigida = conferido.pacote.layout.values.maxOfOrNull { it.minRendererVersion } ?: 0
        if (exigida > RendererContract.RENDERER_VERSION) {
            return EstadoDaProva.Barrada(prova, MotivoDaBarragem.VERSAO_INSUFICIENTE)
        }
        return EstadoDaProva.Pronta(prova, conferido.contentHash)
    }
}

/**
 * A frase de cada motivo de barragem.
 *
 * `when` exaustivo **sem `else`**: motivo novo quebra a compilacao em vez de cair numa frase
 * generica. Mesmo padrao de `mensagemDeEntrada` e `textoSemOrganizacao` — motivo e estado, frase e
 * apresentacao, e as duas coisas moram em lugares diferentes.
 */
fun textoDaBarragem(motivo: MotivoDaBarragem): String = when (motivo) {
    MotivoDaBarragem.SEM_REDE ->
        "Esta prova ainda nao foi baixada, e o aparelho esta sem rede. " +
            "Conecte-se uma vez para baixa-la; depois disso ela funciona sem rede."

    MotivoDaBarragem.PACOTE_AUSENTE ->
        "Esta prova nao tem pacote publicado. Publique-a antes de escanear as folhas."

    MotivoDaBarragem.CONFERENCIA_FALHOU ->
        "O pacote desta prova nao conferiu, e escanear com ele produziria nota errada. " +
            "Tente de novo; se continuar, avise quem publicou a prova."

    MotivoDaBarragem.VERSAO_INSUFICIENTE ->
        "Esta prova exige uma versao mais nova do aplicativo. Atualize para escanea-la."
}

/** A frase de cada falha de listagem, pelo mesmo criterio de [textoDaBarragem]. */
fun textoDaListagem(falha: FalhaDaListagem): String = when (falha) {
    FalhaDaListagem.SEM_REDE ->
        "Nao foi possivel falar com o servidor para saber quais provas existem. Confira a conexao."

    FalhaDaListagem.OUTRA ->
        "Nao foi possivel obter as provas desta organizacao. Tente de novo."
}
