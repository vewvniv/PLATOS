package com.platos.android.scan

/**
 * O prazo da resposta guardada no aparelho (`slice-5c-1-a-resposta-fica-no-aparelho`, design, decisao 5).
 *
 * **Funcao pura sobre listas**, sem disco e sem relogio proprio: [agora] entra por parametro, e o prazo de
 * 30 dias e testavel sem esperar 30 dias. A resposta e dado de classe H (manuscrito de menor no aparelho
 * do professor); a classe H diz "ate 30 dias" (`docs/legal/politica-de-privacidade.md` §10.8; ADR-0012,
 * decisao 4).
 */
object RetencaoDaResposta {

    /** O teto, em dias, contado da captura. Dono unico (P28): nenhum outro lugar escreve 30. */
    const val PRAZO_DIAS: Int = 30

    private const val MILISSEGUNDOS_POR_DIA = 86_400_000L

    /**
     * Os arquivos de [noDisco] que devem ser eliminados:
     * - o que **nenhum caderno referencia** — a resposta do caderno substituido, a descartada ao refazer, a
     *   de uma gravacao que nao chegou a ser referenciada, **e o temporario de uma gravacao interrompida**;
     * - o que tem [PRAZO_DIAS] dias **ou mais** desde a captura. `>=`, e nao `>`: a classe H diz "em ate 30
     *   dias", e o dia 30 ja e o limite. O contato de borda e pinado por teste.
     *
     * [referenciadas] mapeia arquivo para o instante da captura, de todos os cadernos do aparelho. Uma
     * referencia sem arquivo nao gera nada (a normalizacao da leitura cuida dela). Relogio anterior a captura
     * (`agora < capturadaEm`) mantem: a diferenca negativa nunca alcanca o prazo.
     */
    fun arquivosAEliminar(
        noDisco: List<String>,
        referenciadas: Map<String, Long>,
        agora: Long,
        escaneamentoAberto: Boolean = false,
    ): List<String> =
        noDisco.filter { arquivo ->
            val capturadaEm = referenciadas[arquivo]
            // Sem referencia: so se elimina com o escaneamento fechado. Aberto, o caderno em memoria ainda nao foi ao
            // Room (so no `onStop`), e a resposta recem-gravada parece orfa (`slice-5c-3`, design D4).
            if (capturadaEm == null) {
                !escaneamentoAberto
            } else {
                agora - capturadaEm >= PRAZO_DIAS * MILISSEGUNDOS_POR_DIA
            }
        }

    /** Arquivo e instante da captura de cada resposta referenciada por [cadernos]. */
    fun referenciadasPor(cadernos: List<Caderno>): Map<String, Long> =
        cadernos.flatMap { it.regioes }
            .mapNotNull { it.resposta }
            .groupBy({ it.arquivo }, { it.capturadaEm })
            .mapValues { (_, instantes) -> instantes.min() }
}

/** O que uma varredura fez: quantos eliminou e quantos **nao conseguiu** eliminar. */
data class Varredura(val eliminados: Int, val naoEliminados: Int, val semLeituraDosCadernos: Boolean = false)

/**
 * Elimina do aparelho as respostas vencidas ou sem referencia (`slice-5c-1-a-resposta-fica-no-aparelho`,
 * design, decisao 5). Independe de aluno, prova e organizacao: o aparelho e compartilhado, e o prazo nao
 * pertence a sessao corrente — por isso le **todos** os cadernos.
 *
 * **Nunca lanca.** Eliminar e por arquivo: a excecao de E/S de um arquivo e capturada e contada, e os outros
 * seguem; o arquivo fica para a proxima varredura. Se os cadernos nao puderem ser lidos, **nada** e eliminado
 * — sem saber o que esta referenciado nao se sabe o que e orfao — e o resultado diz isso. Em nenhum caso a
 * varredura impede o escaneamento de abrir. O numero de falhas e devolvido; em producao ninguem o ve alem do
 * log de quem chama, e isso e lacuna conhecida (P8).
 *
 * **Bloqueia** (Room e disco): chame de `Dispatchers.IO`.
 */
fun varrerRespostas(
    respostas: RespostasGuardadas,
    cadernos: CadernosGuardados,
    agora: Long,
    escaneamentoAberto: Boolean = false,
): Varredura {
    val referenciadas = try {
        RetencaoDaResposta.referenciadasPor(cadernos.todos())
    } catch (e: Exception) {
        return Varredura(eliminados = 0, naoEliminados = 0, semLeituraDosCadernos = true)
    }
    var eliminados = 0
    var naoEliminados = 0
    for (arquivo in RetencaoDaResposta.arquivosAEliminar(respostas.listar(), referenciadas, agora, escaneamentoAberto)) {
        try {
            respostas.eliminar(arquivo)
            eliminados++
        } catch (e: Exception) {
            naoEliminados++
        }
    }
    return Varredura(eliminados, naoEliminados)
}
