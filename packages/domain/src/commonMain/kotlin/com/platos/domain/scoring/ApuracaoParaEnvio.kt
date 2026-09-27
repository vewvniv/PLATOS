package com.platos.domain.scoring

/**
 * A nota apurada de uma folha, pronta para a fila de envio de `result-sync`
 * (`slice-5b-4-envio-da-parcial`).
 *
 * **Um tipo por cima, e nao um `ObjectiveScore` alargado.** [PartialScore] existe para nunca ser
 * confundida com [ObjectiveScore] — a KDoc dela ja diz por que: "a gravacao e o envio aceitam
 * `ObjectiveScore`. Se a parcial fosse uma... um erro de fiacao a gravaria e subiria como nota
 * final." Este tipo e o que deixa `ResultadoPendente.nota` (aparelho) e `ResultQueries.record`
 * (servidor) aceitarem os dois **sem** apagar essa distincao: um `when` sobre [ApuracaoParaEnvio]
 * e exaustivo, e o compilador acusa o caso que faltar.
 *
 * Sem `@Serializable`: nem este tipo nem `ResultadoPendente` sao serializados como um todo em
 * nenhum ponto do caminho — o que viaja e o corpo do fio (`ResultSubmissionDto`), ja serializado
 * antes de chegar aqui ou depois de sair daqui.
 */
sealed interface ApuracaoParaEnvio {

    /** A nota fechada de uma prova sem discursiva, ou de uma prova com discursiva sem pendencia. */
    data class Completa(val score: ObjectiveScore) : ApuracaoParaEnvio

    /** A parcial objetiva de uma prova com discursiva: nunca fechada, por construcao de [PartialScore]. */
    data class Parcial(val score: PartialScore) : ApuracaoParaEnvio
}
