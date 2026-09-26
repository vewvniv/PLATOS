package com.platos.domain.scoring

/**
 * Uma questao discursiva que aguarda correcao, com o que ela vale.
 *
 * [points] e a soma dos criterios da rubrica dela, que e onde o pacote declara quanto a discursiva
 * vale: o gabarito so cobre as objetivas.
 */
data class AwaitingEssay(
    val questionId: String,
    val points: Int,
)

/**
 * A parte objetiva de uma prova com discursiva, apurada como **parcial**
 * (`slice-5b-2-a-nota-objetiva-parcial`, decisao 1).
 *
 * **Nao e uma [ObjectiveScore], nao herda dela e nao a contem**, e isso e a protecao, e nao um
 * detalhe. A gravacao e o envio aceitam [ObjectiveScore]. Se a parcial fosse uma, com o maximo da
 * parte objetiva, uma folha sem pendencia objetiva sairia `closed`, e um erro de fiacao a gravaria e
 * subiria como nota final. Com um tipo proprio, **o compilador recusa** a troca.
 *
 * **Nao tem `closed`, em nenhuma hipotese.** A parte discursiva ainda nao foi corrigida, e a nota da
 * prova nao e esta (D4): o offline so e definitivo sem discursiva.
 */
data class PartialScore(
    val packageHash: String,
    val variantId: String,
    /** Pontuacao objetiva ja apurada. Nao inclui nada que dependa de revisao. */
    val objectivePoints: Int,
    /** Pontuacao maxima da parte objetiva: a soma do gabarito dos itens objetivos da variante. */
    val objectiveMaxScore: Int,
    /** Pontuacao maxima da prova, como o pacote a declara em `scoring.max_score`. */
    val maxScore: Int,
    /** As discursivas da variante, cada uma com o que vale, na ordem das posicoes. */
    val awaiting: List<AwaitingEssay>,
    val pending: List<PendingQuestion>,
    /** A evidencia da correcao, uma entrada por questao **objetiva** da variante. */
    val outcomes: List<QuestionOutcome>,
) {

    init {
        // As guardas da nota, na escala da parte objetiva. Os motivos sao os mesmos de
        // `ObjectiveScore`, e as frases tambem, para que quem ler uma recusa nao precise saber de
        // qual dos dois tipos ela veio.
        require(objectivePoints in 0..objectiveMaxScore) {
            "parcial $objectivePoints fora de 0..$objectiveMaxScore"
        }
        require(objectivePoints + pending.sumOf { it.points } <= objectiveMaxScore) {
            "parcial $objectivePoints mais ${pending.sumOf { it.points }} em disputa passam de $objectiveMaxScore"
        }

        val somado = outcomes.sumOf { it.earned }
        require(somado == objectivePoints) {
            "a evidencia soma $somado ponto(s) e a parcial apurada e $objectivePoints"
        }

        // Uma questao e objetiva ou aguarda correcao, e so uma vez: a mesma questao nos dois lados
        // seria contada na parte objetiva e de novo na que falta corrigir.
        val ids = outcomes.map { it.questionId } + awaiting.map { it.questionId }
        val repetidos = ids.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        require(repetidos.isEmpty()) {
            "a parcial repete o item: " + repetidos.sorted().joinToString(", ")
        }

        val pendentesNaEvidencia = outcomes.filter { it.pendente }.map { it.questionId }.toSet()
        val pendentesRelatados = pending.map { it.questionId }.toSet()
        require(pendentesNaEvidencia == pendentesRelatados) {
            "a evidencia diz que dependem de revisao " +
                "${pendentesNaEvidencia.sorted()} e a lista de pendencias diz " +
                "${pendentesRelatados.sorted()}"
        }

        // A guarda nova, e a conferencia cruzada que a P28 pede: o maximo da prova vem de
        // `scoring.max_score`, e as duas parcelas vem de outros dois registros, o gabarito e as
        // rubricas. A coerencia do pacote confere o mesmo na publicacao; aqui ele e conferido de
        // novo no ponto em que um numero que nao fecha viraria tela.
        val aguardando = awaiting.sumOf { it.points }
        require(objectiveMaxScore + aguardando == maxScore) {
            "o maximo objetivo ($objectiveMaxScore) mais as discursivas aguardando correcao " +
                "($aguardando) somam ${objectiveMaxScore + aguardando}, e a prova vale $maxScore"
        }
    }

    /** Quanto da parte objetiva ainda depende das pendencias. */
    val pointsAtStake: Int get() = pending.sumOf { it.points }

    /** Quanto a parte discursiva vale, e ainda nao foi corrigido. */
    val awaitingPoints: Int get() = awaiting.sumOf { it.points }
}

/** O que saiu de uma tentativa de apurar a parcial: ou a parcial, ou o motivo da recusa. */
sealed interface PartialScoringOutcome {

    data class Scored(val partial: PartialScore) : PartialScoringOutcome

    /** A folha nao pode ser apurada, e [reason] diz por que numa frase que serve para a tela. */
    data class Rejected(val reason: String) : PartialScoringOutcome
}
