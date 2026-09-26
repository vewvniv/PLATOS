package com.platos.domain.scoring

import com.platos.domain.capture.CapturePayload
import com.platos.domain.capture.QuestionAnswer
import com.platos.domain.exam.ExamPackage
import com.platos.domain.exam.PackageVariant
import com.platos.domain.exam.QuestionKind

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
 * O que foi apurado numa questao: a evidencia da correcao, questao a questao.
 *
 * **Existe para nao jogar fora o que so existe agora.** O total responde "quanto o aluno tirou"; esta
 * lista responde "em que", e e dela que a dimensao analitica sera derivada depois — o vinculo
 * item->habilidade mora no `ExamPackage`, que e imutavel e hasheado, entao a juncao continua possivel
 * enquanto o item estiver gravado. Sem ela, a prova corrigida hoje fica sem analise **para sempre**:
 * nao por falta de codigo, e sim porque a folha de papel sai de circulacao e o dado nao volta.
 *
 * **Nao e nota por habilidade, e nem pode virar uma.** [worth] e [earned] estao na unidade que o
 * professor declarou no gabarito — ponto por questao —, e nada aqui converte habilidade em ponto.
 *
 * [answer] e a propria leitura, e nao uma traducao dela: um segundo vocabulario para dizer "marcou A"
 * seria uma copia que envelhece sozinha.
 */
data class QuestionOutcome(
    val questionId: String,
    /** O que a folha respondeu, na forma em que a leitura a entregou. */
    val answer: QuestionAnswer,
    /** Quanto o item valia, como o gabarito do pacote o declara. */
    val worth: Int,
    /** Quanto ele rendeu. Zero para erro, para em branco e para o que depende de revisao. */
    val earned: Int,
) {

    /**
     * Se esta questao depende de revisao humana.
     *
     * Derivado da forma da resposta, e nao copiado de quem apurou: e o que permite a guarda de
     * [ObjectiveScore] conferir a lista de pendencias contra um segundo caminho. Os dois concordando
     * nao prova muito; os dois discordando denuncia o laco que esqueceu de relatar uma pendencia.
     */
    val pendente: Boolean
        get() = answer is QuestionAnswer.MultiplaMarcacao || answer is QuestionAnswer.Indecisa

    init {
        require(worth >= 0) { "item '$questionId' vale $worth ponto(s), e pontuacao nao e negativa" }
        require(earned in 0..worth) {
            "item '$questionId' rendeu $earned de $worth, e isso esta fora da escala dele"
        }
        require(!pendente || earned == 0) {
            "item '$questionId' depende de revisao e mesmo assim rendeu $earned ponto(s)"
        }
    }
}

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
    /** A evidencia da correcao, uma entrada por questao da variante. */
    val outcomes: List<QuestionOutcome>,
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

        // Dois numeros sobre a mesma apuracao sao duas chances de ela se contradizer, e a evidencia
        // por questao e o segundo. As tres guardas abaixo prendem um ao outro; sem elas, uma
        // evidencia incompleta acompanharia um total correto sem nada acusar, e quem derivasse
        // analise dela leria menos questoes do que a prova teve.
        val somado = outcomes.sumOf { it.earned }
        require(somado == points) {
            "a evidencia soma $somado ponto(s) e a nota apurada e $points"
        }

        val repetidos = outcomes.groupingBy { it.questionId }.eachCount().filterValues { it > 1 }.keys
        require(repetidos.isEmpty()) {
            "a evidencia repete o item: " + repetidos.sorted().joinToString(", ")
        }

        val pendentesNaEvidencia = outcomes.filter { it.pendente }.map { it.questionId }.toSet()
        val pendentesRelatados = pending.map { it.questionId }.toSet()
        require(pendentesNaEvidencia == pendentesRelatados) {
            "a evidencia diz que dependem de revisao " +
                "${pendentesNaEvidencia.sorted()} e a lista de pendencias diz " +
                "${pendentesRelatados.sorted()}"
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

        val declared = variant.positions.values.toSet()
        val julgamento = julgar(examPackage, declared, answers) { lidos ->
            divergence("itens lidos divergem da variante", variant.variantId, declared, lidos, "nao declarados")
        }
        return when (julgamento) {
            is Julgamento.Recusado -> ScoringOutcome.Rejected(julgamento.reason)
            is Julgamento.Feito -> ScoringOutcome.Scored(
                ObjectiveScore(
                    packageHash = examPackage.contentHash(),
                    variantId = variant.variantId,
                    points = julgamento.points,
                    maxScore = examPackage.scoring.maxScore,
                    pending = julgamento.pending,
                    outcomes = julgamento.outcomes,
                ),
            )
        }
    }

    /**
     * A parte objetiva de uma prova com discursiva, apurada como parcial
     * (`slice-5b-2-a-nota-objetiva-parcial`, decisoes 2 e 3).
     *
     * **As mesmas regras de [score], restritas aos itens objetivos da variante:** a variante sai do
     * payload, o gabarito vem do pacote, e o julgamento de cada resposta e o mesmo, porque e a mesma
     * funcao. Quais itens sao objetivos, o pacote ja diz em `PackageItem.kind`, e nada aqui cria um
     * segundo registro disso. As discursivas saem como aguardando correcao, com a soma da rubrica de
     * cada uma.
     *
     * **Recusa a prova corrigivel so no aparelho.** Para ela, [score] ja da a nota, e uma parcial
     * seria um segundo caminho de nota para a mesma prova. E o que impede a sessao de uma prova so
     * objetiva de mostrar, por engano, "parcial" de uma nota que e final.
     *
     * O resultado nunca e a nota: e [PartialScore], que nao tem `closed` e que nenhum consumidor de
     * [ObjectiveScore] aceita.
     */
    fun scorePartial(
        examPackage: ExamPackage,
        payload: CapturePayload,
        answers: List<QuestionAnswer>,
    ): PartialScoringOutcome {
        if (examPackage.meta.fullyOfflineGradable) {
            return PartialScoringOutcome.Rejected(
                "a prova '${examPackage.meta.examId}' e corrigivel so no aparelho e tem nota completa; " +
                    "a apuracao parcial e so de prova com discursiva",
            )
        }

        val variant = resolveVariant(examPackage, payload)
            ?: return PartialScoringOutcome.Rejected(variantRejection(examPackage, payload))

        val itens = examPackage.items.associateBy { it.id }
        val objetivos = mutableSetOf<String>()
        val aguardando = mutableListOf<AwaitingEssay>()
        for (itemId in variant.positions.values) {
            val item = itens[itemId] ?: return PartialScoringOutcome.Rejected(
                "item '$itemId' da variante '${variant.variantId}' nao existe no pacote",
            )
            when (item.kind) {
                QuestionKind.OBJECTIVE -> objetivos += itemId
                QuestionKind.ESSAY -> {
                    val rubrica = item.rubric ?: return PartialScoringOutcome.Rejected(
                        "discursiva '$itemId' nao tem rubrica no pacote, e nao ha quanto ela vale",
                    )
                    aguardando += AwaitingEssay(itemId, rubrica.criteria.sumOf { it.points })                }
            }
        }

        val julgamento = julgar(examPackage, objetivos, answers) { lidos ->
            divergence(
                "itens objetivos lidos divergem da variante",
                variant.variantId,
                objetivos,
                lidos,
                "nao objetivos na variante",
            )
        }
        return when (julgamento) {
            is Julgamento.Recusado -> PartialScoringOutcome.Rejected(julgamento.reason)
            is Julgamento.Feito -> PartialScoringOutcome.Scored(
                PartialScore(
                    packageHash = examPackage.contentHash(),
                    variantId = variant.variantId,
                    objectivePoints = julgamento.points,
                    // O julgamento ja recusou item objetivo sem entrada no gabarito, entao a soma
                    // cobre todos eles.
                    objectiveMaxScore = examPackage.answerKey.filter { it.itemId in objetivos }.sumOf { it.points },
                    maxScore = examPackage.scoring.maxScore,
                    awaiting = aguardando,
                    pending = julgamento.pending,
                    outcomes = julgamento.outcomes,
                ),
            )
        }
    }

    /** O que saiu de julgar as respostas contra o gabarito: os tres numeros, ou o motivo da recusa. */
    private sealed interface Julgamento {
        data class Feito(
            val points: Int,
            val pending: List<PendingQuestion>,
            val outcomes: List<QuestionOutcome>,
        ) : Julgamento

        data class Recusado(val reason: String) : Julgamento
    }

    /**
     * Julga cada resposta contra o gabarito, depois de conferir que as respostas sao exatamente o
     * conjunto [declared]. Quando nao sao, [divergencia] recebe os itens lidos e da a frase.
     *
     * **Um so julgamento para toda apuracao** (`slice-5b-2-a-nota-objetiva-parcial`, decisao 2): quem
     * chama diz qual conjunto de itens a folha tem de ter, e nada mais muda. Um segundo laco seria uma
     * segunda regra de "em branco e erro, ambigua e pendencia", e as duas divergiriam em silencio.
     */
    private fun julgar(
        examPackage: ExamPackage,
        declared: Set<String>,
        answers: List<QuestionAnswer>,
        divergencia: (lidos: Set<String>) -> String,
    ): Julgamento {
        // Repeticao antes de conjunto: `Set` nao ve resposta duplicada, e o conjunto continuaria
        // batendo com a variante enquanto o laco somaria o item duas vezes. A nota passaria de
        // `max_score` sem nada acusar.
        val repetidos = answers.groupingBy { it.questionId }.eachCount().filterValues { it > 1 }.keys
        if (repetidos.isNotEmpty()) {
            return Julgamento.Recusado(
                "item com resposta repetida: " + repetidos.sorted().joinToString(", "),
            )
        }

        val read = answers.map { it.questionId }.toSet()
        if (read != declared) {
            return Julgamento.Recusado(divergencia(read))
        }

        val key = examPackage.answerKey.associateBy { it.itemId }
        var points = 0
        val pending = mutableListOf<PendingQuestion>()
        val outcomes = mutableListOf<QuestionOutcome>()

        for (answer in answers) {
            val entry = key[answer.questionId]
                ?: return Julgamento.Recusado(
                    "item '${answer.questionId}' nao tem entrada no gabarito do pacote",
                )

            // O quanto a questao rendeu sai **deste** `when`, e nao de uma segunda passada sobre as
            // respostas: o total e a evidencia vem do mesmo julgamento, entao nao ha como um dizer
            // "acertou" e o outro "errou". A guarda de construcao de `ObjectiveScore` confere que
            // eles fecham; esta e a razao de eles fecharem.
            val rendeu = when (answer) {
                // Acerto e erro sao definitivos. Em branco tambem: o aluno nao marcou, e a leitura
                // tem certeza disso — nao ha o que revisar numa bolha que ninguem tocou.
                is QuestionAnswer.Marcada ->
                    if (answer.option == entry.correct) entry.points else 0
                is QuestionAnswer.EmBranco -> 0

                is QuestionAnswer.MultiplaMarcacao -> {
                    pending += PendingQuestion(
                        answer.questionId,
                        PendingReason.MULTIPLA_MARCACAO,
                        entry.points,
                    )
                    0
                }

                is QuestionAnswer.Indecisa -> {
                    pending += PendingQuestion(
                        answer.questionId,
                        PendingReason.INDECISA,
                        entry.points,
                    )
                    0
                }
            }

            points += rendeu
            outcomes += QuestionOutcome(
                questionId = answer.questionId,
                answer = answer,
                worth = entry.points,
                earned = rendeu,
            )
        }

        return Julgamento.Feito(points, pending, outcomes)
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

    private fun divergence(
        oQue: String,
        variantId: String,
        declared: Set<String>,
        read: Set<String>,
        rotuloSobrando: String,
    ): String {
        val faltando = (declared - read).sorted()
        val sobrando = (read - declared).sorted()
        return "$oQue '$variantId'" +
            (if (faltando.isNotEmpty()) "; faltando: ${faltando.joinToString(", ")}" else "") +
            (if (sobrando.isNotEmpty()) "; $rotuloSobrando: ${sobrando.joinToString(", ")}" else "")
    }
}
