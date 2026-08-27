package com.platos.domain.scoring

import com.platos.domain.capture.CapturePayload
import com.platos.domain.capture.QuestionAnswer
import com.platos.domain.exam.ExamPackage
import com.platos.domain.exam.PackageVariant

/** Por que uma questao ficou pendente de revisao humana. */
enum class PendingReason {
    /** Mais de uma bolha marcada. A folha nao diz o que o aluno quis. */
    MULTIPLA_MARCACAO,

    /** Ao menos uma bolha na faixa de indecisao, e nenhuma marcada. */
    INDECISA,
}

/**
 * Uma questao que a nota nao pode fechar sozinha.
 *
 * [points] e o que ela vale — o quanto da nota depende de quem revisar. Sem esse numero a
 * pendencia seria uma lista de nomes, e nao daria para dizer se a nota apurada ja decide a
 * aprovacao do aluno ou se a revisao ainda pode virar tudo.
 */
data class PendingQuestion(
    val questionId: String,
    val reason: PendingReason,
    val points: Int,
)

/**
 * A nota objetiva de uma folha (D4, §15).
 *
 * Traz a identidade do que a produziu — [packageHash] e [variantId] — porque a nota atravessa o
 * modelo offline: ela e apurada no aparelho contra um pacote em cache e sobe depois. Nota sem
 * dizer de qual pacote e vira numero sem prova, no dia em que existir mais de uma versao da prova.
 */
data class ObjectiveScore(
    val packageHash: String,
    val variantId: String,
    /** Pontuacao ja apurada. Nao inclui nada que dependa de revisao. */
    val points: Int,
    /** Pontuacao maxima da prova, como o pacote a declara. */
    val maxScore: Int,
    val pending: List<PendingQuestion>,
) {

    init {
        // A segunda guarda do mesmo erro, e ela existe por experiencia desta fatia: a conferencia
        // por conjunto deixava passar resposta repetida, e a nota saia 41 de 40 — bem-formada,
        // plausivel e errada. Uma nota fora da escala nao e folha ruim, e defeito de quem apura,
        // entao aqui e excecao e nao recusa.
        require(points in 0..maxScore) { "nota $points fora de 0..$maxScore" }
        require(points + pending.sumOf { it.points } <= maxScore) {
            "nota $points mais ${pending.sumOf { it.points }} em disputa passam de $maxScore"
        }
    }

    /** Quanto ainda depende das pendencias. */
    val pointsAtStake: Int get() = pending.sumOf { it.points }

    /**
     * A nota esta fechada quando nada depende de revisao humana.
     *
     * Nao e o mesmo que "a prova e objetiva". Uma prova sem discursiva pode ter folha com rasura,
     * e a invariante que manda a revisao humana vencer o resultado automatico vale ali.
     */
    val closed: Boolean get() = pending.isEmpty()
}

/** O que saiu de uma tentativa de apurar a nota: ou a nota, ou o motivo da recusa. */
sealed interface ScoringOutcome {

    data class Scored(val score: ObjectiveScore) : ScoringOutcome

    /** A folha nao pode ser apurada, e [reason] diz por que numa frase que serve para a tela. */
    data class Rejected(val reason: String) : ScoringOutcome
}

/**
 * A nota, a partir das respostas lidas e do pacote publicado.
 *
 * **Aritmetica sobre contrato que ja existe.** `answer_key`, `points` e `scoring.max_score` viajam
 * no `ExamPackage` desde a fatia 2a; o que faltava era o consumidor.
 *
 * A apuracao **nao** refaz o mapeamento de posicao para item. `Publish` resolve isso na publicacao
 * e grava o item em cada bolha do `LayoutMap` da variante, entao a leitura ja entrega item.
 * `positions` continua sendo a declaracao de **quais** itens a variante contem, e e nessa qualidade
 * que ele entra aqui: para conferir que a folha lida e a folha que a variante descreve.
 *
 * Local, deterministica e sem efeito: nada aqui toca rede, disco ou estado.
 */
object ObjectiveScoring {

    fun score(
        examPackage: ExamPackage,
        payload: CapturePayload,
        answers: List<QuestionAnswer>,
    ): ScoringOutcome {
        val variant = resolveVariant(examPackage, payload)
            ?: return ScoringOutcome.Rejected(variantRejection(examPackage, payload))

        // Repeticao antes de conjunto: `Set` nao ve resposta duplicada, e o conjunto continuaria
        // batendo com a variante enquanto o laco somaria o item duas vezes. A nota passaria de
        // `max_score` sem nada acusar.
        val repetidos = answers.groupingBy { it.questionId }.eachCount().filterValues { it > 1 }.keys
        if (repetidos.isNotEmpty()) {
            return ScoringOutcome.Rejected(
                "item com resposta repetida: " + repetidos.sorted().joinToString(", "),
            )
        }

        val declared = variant.positions.values.toSet()
        val read = answers.map { it.questionId }.toSet()
        if (read != declared) {
            return ScoringOutcome.Rejected(divergence(variant.variantId, declared, read))
        }

        val key = examPackage.answerKey.associateBy { it.itemId }
        var points = 0
        val pending = mutableListOf<PendingQuestion>()

        for (answer in answers) {
            val entry = key[answer.questionId]
                ?: return ScoringOutcome.Rejected(
                    "item '${answer.questionId}' nao tem entrada no gabarito do pacote",
                )

            when (answer) {
                // Acerto e erro sao definitivos. Em branco tambem: o aluno nao marcou, e a leitura
                // tem certeza disso — nao ha o que revisar numa bolha que ninguem tocou.
                is QuestionAnswer.Marcada ->
                    if (answer.option == entry.correct) points += entry.points
                is QuestionAnswer.EmBranco -> Unit

                is QuestionAnswer.MultiplaMarcacao ->
                    pending += PendingQuestion(
                        answer.questionId,
                        PendingReason.MULTIPLA_MARCACAO,
                        entry.points,
                    )

                is QuestionAnswer.Indecisa ->
                    pending += PendingQuestion(
                        answer.questionId,
                        PendingReason.INDECISA,
                        entry.points,
                    )
            }
        }

        return ScoringOutcome.Scored(
            ObjectiveScore(
                packageHash = examPackage.contentHash(),
                variantId = variant.variantId,
                points = points,
                maxScore = examPackage.scoring.maxScore,
                pending = pending,
            ),
        )
    }

    /**
     * A variante da folha, ou `null` quando ela nao pode ser determinada.
     *
     * O payload declara a variante desde a fatia 3a, e ela vem **vazia** ate a fatia 7 (§8). Com
     * uma variante so no pacote isso e inequivoco. Com mais de uma nao e, e escolher a primeira
     * atribuiria a folha ao gabarito errado — em silencio, porque toda folha continuaria recebendo
     * uma nota plausivel.
     */
    private fun resolveVariant(examPackage: ExamPackage, payload: CapturePayload): PackageVariant? =
        if (payload.variant.isNotEmpty()) {
            examPackage.variants.firstOrNull { it.variantId == payload.variant }
        } else {
            examPackage.variants.singleOrNull()
        }

    private fun variantRejection(examPackage: ExamPackage, payload: CapturePayload): String {
        val declared = examPackage.variants.joinToString(", ") { it.variantId }
        return if (payload.variant.isNotEmpty()) {
            "variante '${payload.variant}' nao existe no pacote, que declara: $declared"
        } else {
            "folha sem variante no payload, e o pacote declara ${examPackage.variants.size}: $declared"
        }
    }

    private fun divergence(variantId: String, declared: Set<String>, read: Set<String>): String {
        val faltando = (declared - read).sorted()
        val sobrando = (read - declared).sorted()
        return "itens lidos divergem da variante '$variantId'" +
            (if (faltando.isNotEmpty()) "; faltando: ${faltando.joinToString(", ")}" else "") +
            (if (sobrando.isNotEmpty()) "; nao declarados: ${sobrando.joinToString(", ")}" else "")
    }
}
