package com.platos.android.scan

import com.platos.domain.scoring.ObjectiveScore
import com.platos.domain.scoring.PartialScore
import com.platos.domain.scoring.PendingQuestion
import com.platos.domain.scoring.PendingReason

/**
 * A nota, ja na forma em que a tela a mostra.
 *
 * Existe separado da composicao por uma razao so: **a decisao de apresentar ou nao a nota como
 * fechada e verificavel, e desenhar nao e.** Deixa-la dentro de um `@Composable` a poria do outro
 * lado da fronteira que esta fatia desenhou — a tela vai para conferencia em aparelho, e o que
 * decide fica onde o teste alcanca.
 */
internal data class NotaApresentada(
    /** "12 de 40". */
    val pontuacao: String,
    val fechada: Boolean,
    /** A linha que explica o numero acima. */
    val resumo: String,
    /** Uma linha por questao pendente, com o motivo e quanto vale. */
    val pendencias: List<String>,
)

/**
 * **Pendencia nao vira nota.** Uma questao com rasura ou com bolha indecisa vale zero ate alguem
 * revisar, e apresentar o total como fechado transformaria duvida em resultado. A invariante que
 * manda revisao humana vencer o automatico comeca por nao esconder o que esta em disputa.
 */
internal fun apresentar(score: ObjectiveScore): NotaApresentada = NotaApresentada(
    pontuacao = "${score.points} de ${score.maxScore}",
    fechada = score.closed,
    resumo = if (score.closed) {
        "Nota fechada."
    } else {
        "Nota parcial: ${score.pending.size} questao(oes) dependem de revisao, " +
            "${score.pointsAtStake} ponto(s) em disputa."
    },
    pendencias = score.pending.map(::linhaDePendencia),
)

/**
 * A parcial objetiva de uma prova com discursiva, ja na forma em que a tela a mostra
 * (`slice-5b-2-a-nota-objetiva-parcial`).
 *
 * **Tres numeros, e nao um.** O risco e o professor ler a parcial como nota: "3 de 4" sozinho parece
 * uma nota de 75%. Por isso o maximo objetivo vem junto da pontuacao, e o quanto as discursivas valem
 * e o maximo da prova vem logo abaixo. Que a nota nao e definitiva, quem diz e o aviso do estado.
 */
internal data class ParcialApresentada(
    /** "3 de 4 na objetiva". */
    val pontuacao: String,
    /** "Discursivas: 7 ponto(s) aguardam correcao · a prova vale 11". */
    val resumo: String,
    /** Uma linha por questao objetiva pendente, com o motivo e quanto vale. */
    val pendencias: List<String>,
)

internal fun apresentar(parcial: PartialScore): ParcialApresentada = ParcialApresentada(
    pontuacao = "${parcial.objectivePoints} de ${parcial.objectiveMaxScore} na objetiva",
    resumo = "Discursivas: ${parcial.awaitingPoints} ponto(s) aguardam correcao · " +
        "a prova vale ${parcial.maxScore}",
    pendencias = parcial.pending.map(::linhaDePendencia),
)

private fun linhaDePendencia(pendencia: PendingQuestion): String =
    "· ${pendencia.questionId}: ${motivo(pendencia.reason)} (${pendencia.points} ponto(s))"

private fun motivo(reason: PendingReason): String = when (reason) {
    PendingReason.MULTIPLA_MARCACAO -> "mais de uma alternativa marcada"
    PendingReason.INDECISA -> "marcacao fraca demais para decidir"
}
