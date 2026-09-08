package com.platos.android.session

import com.platos.android.pacote.MotivoDaRecusa
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
 * disso e [DeviceSession].
 */
class PreparoDaProva {

    var state: EstadoDaProva = EstadoDaProva.Listando
        private set

    /** Uma nova listagem foi pedida. Volta ao inicio, descartando o que estivesse na tela. */
    fun listar() {
        state = EstadoDaProva.Listando
    }

    /**
     * O resultado da listagem.
     *
     * **Lista vazia e [EstadoDaProva.SemProvaPublicada], e falha e [EstadoDaProva.ListagemFalhou].**
     * Confundir os dois faria a tela afirmar que a organizacao nao tem prova quando o que houve foi
     * a consulta nao chegar — uma afirmacao sobre o mundo que a falha nao autoriza.
     */
    fun aoListar(resultado: ResultadoDasProvas) {
        if (state !is EstadoDaProva.Listando) return
        state = when (resultado) {
            is ResultadoDasProvas.Chegaram ->
                if (resultado.provas.isEmpty()) {
                    EstadoDaProva.SemProvaPublicada
                } else {
                    EstadoDaProva.Escolhendo(resultado.provas)
                }

            is ResultadoDasProvas.SemRede -> EstadoDaProva.ListagemFalhou(FalhaDaListagem.SEM_REDE)
            is ResultadoDasProvas.Falhou -> EstadoDaProva.ListagemFalhou(FalhaDaListagem.OUTRA)
        }
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
        if (prova !in atual.provas) return
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

    /** Volta a escolha, depois de uma barragem. */
    fun voltarAEscolha(provas: List<ProvaPublicada>) {
        state = if (provas.isEmpty()) EstadoDaProva.SemProvaPublicada else EstadoDaProva.Escolhendo(provas)
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
