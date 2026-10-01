package com.platos.domain.scoring

/** A pontuacao que o professor deu a uma discursiva, como chegou: o item e a nota, sem o valor dele. */
data class PontuacaoDada(
    val questionId: String,
    val earned: Pontos,
)

/** Uma discursiva ja corrigida: o que valia (inteiro, do pacote) e o que o professor lhe deu. */
data class DiscursivaCorrigida(
    val questionId: String,
    val worth: Int,
    val earned: Pontos,
) {
    init {
        require(worth >= 0) { "a discursiva '$questionId' vale $worth ponto(s), e pontuacao nao e negativa" }
        require(earned.centesimos <= worth * 100L) {
            "a discursiva '$questionId' recebeu $earned e vale no maximo $worth"
        }
    }
}

/**
 * A nota completa de uma prova com discursiva, depois que o professor pontuou todas elas
 * (`slice-5c-2-a-nota-do-professor`, spec `scoring`).
 *
 * **Nao herda de [PartialScore] nem de [ObjectiveScore], e nao os contem** — a mesma protecao que a
 * KDoc de [PartialScore] da: a gravacao e o envio de cada tipo aceitam so aquele tipo, e o compilador
 * recusa a troca. Uma parcial nunca pode subir como a nota do professor, nem o contrario.
 *
 * [total] e **exato** (centesimos). [closed] e derivado: so fecha sem pendencia **objetiva** — a nota
 * do professor e sobre a discursiva, e nao decide a objetiva ambigua.
 *
 * Construir uma [NotaDoProfessor] direto exige que o chamador ja tenha conferido a cobertura das
 * discursivas; o caminho que **confere** e [CorrecaoDoProfessor.completar].
 */
data class NotaDoProfessor(
    val packageHash: String,
    val variantId: String,
    /** Pontuacao objetiva ja apurada, inteira. Nao inclui nada que dependa de revisao. */
    val objectivePoints: Int,
    val objectiveMaxScore: Int,
    /** Pontuacao maxima da prova, como o pacote a declara. */
    val maxScore: Int,
    val pending: List<PendingQuestion>,
    /** A evidencia das questoes **objetivas**, como na parcial. */
    val outcomes: List<QuestionOutcome>,
    /** As discursivas corrigidas, na ordem das posicoes da variante. */
    val essays: List<DiscursivaCorrigida>,
) {

    init {
        require(essays.isNotEmpty()) { "a nota do professor precisa de ao menos uma discursiva corrigida" }
        require(objectivePoints in 0..objectiveMaxScore) {
            "objetiva $objectivePoints fora de 0..$objectiveMaxScore"
        }
        require(outcomes.sumOf { it.earned } == objectivePoints) {
            "a evidencia objetiva soma ${outcomes.sumOf { it.earned }} ponto(s) e a parcial apurada e $objectivePoints"
        }
        val repetidos = essays.groupingBy { it.questionId }.eachCount().filterValues { it > 1 }.keys
        require(repetidos.isEmpty()) {
            "a nota repete a discursiva: " + repetidos.sorted().joinToString(", ")
        }
        require(objectiveMaxScore + essays.sumOf { it.worth } == maxScore) {
            "o maximo objetivo ($objectiveMaxScore) mais as discursivas (${essays.sumOf { it.worth }}) " +
                "somam ${objectiveMaxScore + essays.sumOf { it.worth }}, e a prova vale $maxScore"
        }
    }

    /** O total exato: a parte objetiva mais o que o professor deu, em centesimos. */
    val total: Pontos = essays.fold(Pontos.inteiros(objectivePoints)) { soma, e -> soma + e.earned }

    /** Quanto da parte objetiva ainda depende das pendencias. */
    val pointsAtStake: Int get() = pending.sumOf { it.points }

    /** Fechada quando nada **objetivo** depende de revisao humana. */
    val closed: Boolean get() = pending.isEmpty()
}

/** O que saiu de completar a parcial: ou a nota do professor, ou o motivo da recusa. */
sealed interface NotaDoProfessorOutcome {

    data class Scored(val nota: NotaDoProfessor) : NotaDoProfessorOutcome

    /** A nota nao pode ser composta, e [reason] diz por que numa frase que serve para a tela. */
    data class Rejected(val reason: String) : NotaDoProfessorOutcome
}

/**
 * Completa a [PartialScore] com a pontuacao que o professor deu a cada discursiva.
 *
 * **Local, deterministica e sem efeito**, como as demais apuracoes: o servidor a roda com o **mesmo**
 * codigo que o aparelho usara (regra 7 do `CLAUDE.md`). Nao recalcula a parte objetiva — copia o que a
 * parcial apurou — e cobre **todas** as discursivas da variante de uma vez: nota de parte delas nao e
 * representavel aqui (e do caderno incompleto, outra mudanca).
 */
object CorrecaoDoProfessor {

    fun completar(parcial: PartialScore, pontuacoes: List<PontuacaoDada>): NotaDoProfessorOutcome {
        if (parcial.awaiting.isEmpty()) {
            return NotaDoProfessorOutcome.Rejected(
                "a prova nao tem questao discursiva e tem nota completa; nao ha o que pontuar",
            )
        }

        val discursivas = parcial.awaiting.associateBy { it.questionId }

        val repetidas = pontuacoes.groupingBy { it.questionId }.eachCount().filterValues { it > 1 }.keys
        if (repetidas.isNotEmpty()) {
            return NotaDoProfessorOutcome.Rejected(
                "a discursiva ${nomes(repetidas)} foi pontuada mais de uma vez",
            )
        }

        val alheias = pontuacoes.map { it.questionId }.filter { it !in discursivas }
        if (alheias.isNotEmpty()) {
            return NotaDoProfessorOutcome.Rejected(
                "o item ${nomes(alheias)} nao e discursiva da variante '${parcial.variantId}'",
            )
        }

        val dadas = pontuacoes.associateBy { it.questionId }
        val faltando = parcial.awaiting.map { it.questionId }.filter { it !in dadas }
        if (faltando.isNotEmpty()) {
            return NotaDoProfessorOutcome.Rejected(
                "a discursiva ${nomes(faltando)} da variante '${parcial.variantId}' nao recebeu pontuacao",
            )
        }

        val corrigidas = parcial.awaiting.map { aguardando ->
            val dada = dadas.getValue(aguardando.questionId)
            if (dada.earned.centesimos > aguardando.points * 100L) {
                return NotaDoProfessorOutcome.Rejected(
                    "a discursiva '${aguardando.questionId}' recebeu ${dada.earned} e vale no maximo ${aguardando.points}",
                )
            }
            DiscursivaCorrigida(aguardando.questionId, aguardando.points, dada.earned)
        }

        return NotaDoProfessorOutcome.Scored(
            NotaDoProfessor(
                packageHash = parcial.packageHash,
                variantId = parcial.variantId,
                objectivePoints = parcial.objectivePoints,
                objectiveMaxScore = parcial.objectiveMaxScore,
                maxScore = parcial.maxScore,
                pending = parcial.pending,
                outcomes = parcial.outcomes,
                essays = corrigidas,
            ),
        )
    }

    private fun nomes(ids: Collection<String>): String = ids.sorted().joinToString(", ") { "'$it'" }
}
