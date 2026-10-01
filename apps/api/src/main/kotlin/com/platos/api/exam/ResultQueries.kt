package com.platos.api.exam

import com.platos.api.db.generated.tables.references.ANSWER_OBSERVATION
import com.platos.api.db.generated.tables.references.EXAM
import com.platos.api.db.generated.tables.references.GRADING_RESULT
import com.platos.domain.capture.QuestionAnswer
import com.platos.domain.scoring.ApuracaoParaEnvio
import com.platos.domain.scoring.NotaDoProfessor
import com.platos.domain.scoring.Pontos
import com.platos.domain.scoring.QuestionOutcome
import com.platos.domain.transport.AnswerKind
import com.platos.domain.transport.GradedResultSubmissionDto
import com.platos.domain.transport.ResultSubmissionDto
import com.platos.domain.transport.answerKind
import com.platos.domain.transport.answerOptions
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.impl.DSL

/**
 * A gravacao do resultado apurado no aparelho (§10, push append-only).
 *
 * **A autorizacao continua sendo da RLS**, como em [ExamQueries]. O `where organization_id = ?` e o
 * seletor que veio do caminho da rota, e nao a fronteira: quem restringe as linhas e
 * `grading_result_member_insert`/`_select`, a partir do usuario posto na sessao por
 * [com.platos.api.db.Tenancy.asUser].
 *
 * **Tudo aqui roda dentro da transacao que `asUser` ja abre.** O resultado e a evidencia entram
 * juntos ou nao entram: um resultado gravado sem as observacoes dele seria uma nota sem a correcao
 * que a produziu, e o append-only impede conserta-la depois.
 */
/**
 * A prova publicada, com o pacote que decide o que ela e.
 *
 * Os dois saem da mesma linha e da mesma consulta. O [examId] e o alvo da gravacao; o [pacote] e o
 * **oraculo da proveniencia** — o unico registro que diz contra o que a nota foi apurada.
 */
data class ProvaPublicada(
    val examId: UUID,
    val pacote: PackageContent,
)

class ResultQueries {

    /**
     * A prova publicada e o pacote que a identifica, ou `null` quando nao ha o que gravar.
     *
     * `null` cobre as mesmas tres situacoes de [ExamQueries.findPackage] — prova inexistente, prova
     * sem pacote e prova de organizacao a que o chamador nao pertence —, e pela mesma razao:
     * distingui-las revelaria a existencia da prova a quem nao pode ve-la.
     *
     * **O pacote vem na mesma consulta, e nao numa segunda.** A `join` em `EXAM_PACKAGE` ja estava
     * aqui — era ela que decidia "publicada" —, e o que muda e quantas colunas ela devolve. Uma
     * consulta separada por `content_hash` seria um **segundo oraculo** para "qual e o pacote desta
     * prova", que e o que as KDoc das rotas de roster e de resultado recusam por escrito: com dois
     * oraculos, "publicada" passa a significar coisas diferentes em rotas diferentes.
     *
     * O `content` e o `content_hash` viajam juntos dentro de [PackageContent] pela razao que a KDoc
     * dele ja da: busca-los em duas chamadas abriria a possibilidade de conferir conteudo contra o
     * hash de outra linha.
     *
     * **O nome continua dizendo `ExamId` e a funcao devolve mais do que isso.** Nao e descuido: o
     * plano da ETAPA 4 nomeia esta funcao, e renomear e refatoracao fora do escopo da mudanca
     * (regra 6). Fica dito aqui, como o achado 5.4 fez com `meta.exam_id` — o nome fica curto, e o
     * que ele esconde fica escrito.
     */
    fun findPublishedExamId(
        ctx: DSLContext,
        organizationId: UUID,
        shortId: String,
    ): ProvaPublicada? =
        ctx.select(
            EXAM.ID,
            com.platos.api.db.generated.tables.references.EXAM_PACKAGE.CONTENT,
            com.platos.api.db.generated.tables.references.EXAM_PACKAGE.CONTENT_HASH,
        )
            .from(EXAM)
            .join(com.platos.api.db.generated.tables.references.EXAM_PACKAGE)
            .on(com.platos.api.db.generated.tables.references.EXAM_PACKAGE.EXAM_ID.eq(EXAM.ID))
            .where(EXAM.ORGANIZATION_ID.eq(organizationId))
            .and(EXAM.SHORT_ID.eq(shortId))
            .fetchOne { record ->
                ProvaPublicada(
                    examId = record.value1()!!,
                    pacote = PackageContent(
                        content = record.value2() ?: "",
                        contentHash = record.value3() ?: "",
                    ),
                )
            }

    /**
     * Grava o resultado e devolve a revisao dele. Idempotente por [ResultSubmissionDto.captureId].
     *
     * **Reenvio nao grava nada e devolve a revisao que ja existe.** E o caso rotineiro do modelo
     * offline — a confirmacao que se perdeu no caminho —, e nao a excecao: o aparelho so pode
     * expurgar o pendente depois de uma confirmacao, entao toda confirmacao perdida vira reenvio.
     *
     * **Recaptura e outra captura, e ganha revisao nova.** A distincao entre "e o mesmo" e "e de
     * novo" fica onde a informacao existe: no aparelho, que sabe se a folha passou pela camera uma
     * segunda vez. O servidor nao a adivinha comparando conteudo, e comparar seria errado — uma
     * recaptura que desse a mesma nota continua sendo recaptura.
     *
     * **A corrida de dois envios simultaneos do mesmo `capture_id` nao e tratada aqui, e nao e
     * omissao.** Ela termina na constraint `grading_result_captura_unica`, a transacao inteira volta
     * atras e o pendente continua no aparelho para ser reenviado — que e o desfecho certo. Tratar a
     * corrida dentro da transacao exigiria reler depois de um erro que ja a invalidou. O envio e
     * serial por construcao: e um trabalho de fila por vez.
     */
    fun record(
        ctx: DSLContext,
        organizationId: UUID,
        examId: UUID,
        submission: ResultSubmissionDto,
        nota: ApuracaoParaEnvio,
    ): Int {
        val campos = nota.paraGravacao()
        return gravar(
            ctx,
            organizationId,
            examId,
            Gravacao(
                captureId = submission.captureId,
                studentToken = submission.studentToken,
                capturedAt = submission.capturedAt,
                origin = "omr",
                path = null,
                completesCaptureId = null,
                packageHash = campos.packageHash,
                variantId = campos.variantId,
                points = campos.points.toBigDecimal(),
                maxScore = campos.maxScore,
                closed = campos.closed,
                evidencias = campos.outcomes.map { outcome ->
                    Evidencia(
                        itemId = outcome.questionId,
                        answerKind = outcome.answer.answerKind(),
                        answerOptions = outcome.answer.alternativas(),
                        worth = outcome.worth,
                        earned = outcome.earned.toBigDecimal(),
                    )
                },
            ),
        )
    }

    /**
     * O miolo da gravacao, **um so** para toda origem: idempotencia por `(exam_id, capture_id)`, revisao
     * por `(exam_id, student_token)` e as linhas de evidencia, na transacao que `asUser` ja abriu.
     * Foi extraido de `record` sem mudar uma linha do que ele fazia (`slice-5c-2-a-nota-do-professor`).
     */
    private fun gravar(ctx: DSLContext, organizationId: UUID, examId: UUID, g: Gravacao): Int {
        val jaGravada = ctx.select(GRADING_RESULT.REVISION)
            .from(GRADING_RESULT)
            .where(GRADING_RESULT.EXAM_ID.eq(examId))
            .and(GRADING_RESULT.CAPTURE_ID.eq(g.captureId))
            .fetchOne { it.value1() }
        if (jaGravada != null) return jaGravada

        // `is not distinct from`, e nao `=`: o token e nulo na folha avulsa, e `coluna = null` nunca
        // e verdadeiro. Com `=`, toda avulsa comecaria na revisao 1 e a segunda colidiria com a
        // primeira no unique — recusada como duplicata de uma folha que nao e a dela.
        val proxima = ctx.select(DSL.coalesce(DSL.max(GRADING_RESULT.REVISION), 0).plus(1))
            .from(GRADING_RESULT)
            .where(GRADING_RESULT.EXAM_ID.eq(examId))
            .and(
                DSL.condition(
                    "{0} is not distinct from {1}",
                    GRADING_RESULT.STUDENT_TOKEN,
                    DSL.value(g.studentToken),
                ),
            )
            .fetchOne { it.value1() } ?: 1

        val resultadoId = ctx.insertInto(GRADING_RESULT)
            .set(GRADING_RESULT.ORGANIZATION_ID, organizationId)
            .set(GRADING_RESULT.EXAM_ID, examId)
            .set(GRADING_RESULT.STUDENT_TOKEN, g.studentToken)
            .set(GRADING_RESULT.REVISION, proxima)
            .set(GRADING_RESULT.CAPTURE_ID, g.captureId)
            // A origem e explicita: o dia em que existir outra nao depende do default da coluna.
            .set(GRADING_RESULT.ORIGIN, g.origin)
            .set(GRADING_RESULT.PATH, g.path)
            .set(GRADING_RESULT.COMPLETES_CAPTURE_ID, g.completesCaptureId)
            .set(GRADING_RESULT.PACKAGE_HASH, g.packageHash)
            .set(GRADING_RESULT.VARIANT_ID, g.variantId)
            .set(GRADING_RESULT.POINTS, g.points)
            .set(GRADING_RESULT.MAX_SCORE, g.maxScore)
            .set(GRADING_RESULT.CLOSED, g.closed)
            .set(GRADING_RESULT.CAPTURED_AT, OffsetDateTime.parse(g.capturedAt))
            .returningResult(GRADING_RESULT.ID)
            .fetchOne { it.value1() }!!

        for (evidencia in g.evidencias) {
            ctx.insertInto(ANSWER_OBSERVATION)
                .set(ANSWER_OBSERVATION.ORGANIZATION_ID, organizationId)
                .set(ANSWER_OBSERVATION.GRADING_RESULT_ID, resultadoId)
                .set(ANSWER_OBSERVATION.ITEM_ID, evidencia.itemId)
                .set(ANSWER_OBSERVATION.ANSWER_KIND, evidencia.answerKind)
                .set(ANSWER_OBSERVATION.ANSWER_OPTIONS, evidencia.answerOptions)
                .set(ANSWER_OBSERVATION.WORTH, evidencia.worth)
                .set(ANSWER_OBSERVATION.EARNED, evidencia.earned)
                .execute()
        }

        return proxima
    }

    /**
     * Grava a nota do professor como **revisao nova** da mesma folha (`slice-5c-2-a-nota-do-professor`).
     *
     * Mesma idempotencia e mesma numeracao de [record], porque e o mesmo [gravar]: reenvio devolve a
     * revisao que ja existe, e nova correcao da mesma captura tem `capture_id` proprio. A evidencia leva
     * as objetivas, como na parcial, **e** uma linha `discursiva_corrigida` por discursiva. Quem decide
     * qual revisao e a corrente e a view `grading_result_current`, e nao este metodo.
     */
    fun recordGraded(
        ctx: DSLContext,
        organizationId: UUID,
        examId: UUID,
        submission: GradedResultSubmissionDto,
        nota: NotaDoProfessor,
    ): Int = gravar(
        ctx,
        organizationId,
        examId,
        Gravacao(
            captureId = submission.captureId,
            studentToken = submission.studentToken,
            capturedAt = submission.capturedAt,
            origin = "teacher",
            path = "image",
            completesCaptureId = submission.completesCaptureId,
            packageHash = nota.packageHash,
            variantId = nota.variantId,
            points = nota.total.paraBigDecimal(),
            maxScore = nota.maxScore,
            closed = nota.closed,
            evidencias = nota.outcomes.map { outcome ->
                Evidencia(
                    itemId = outcome.questionId,
                    answerKind = outcome.answer.answerKind(),
                    answerOptions = outcome.answer.alternativas(),
                    worth = outcome.worth,
                    earned = outcome.earned.toBigDecimal(),
                )
            } + nota.essays.map { essay ->
                Evidencia(
                    itemId = essay.questionId,
                    answerKind = AnswerKind.DISCURSIVA_CORRIGIDA,
                    answerOptions = emptyArray(),
                    worth = essay.worth,
                    earned = essay.earned.paraBigDecimal(),
                )
            },
        ),
    )
}

/**
 * A forma que o jOOQ quer, e **so** ela.
 *
 * Os quatro valores de `answer_kind` e a regra de quais alternativas contam vivem em
 * `com.platos.domain.transport` desde o ADR-0015 — os dois lados do fio os leem de la. O que sobra
 * aqui e a conversao de `List<String>` para o `Array<String?>` que a coluna espera: traducao para o
 * **banco**, e por isso ela nao subiu para o dominio (ADR-0015 decisao 2).
 */
private fun QuestionAnswer.alternativas(): Array<String?> =
    answerOptions().toTypedArray<String?>()

/**
 * Os cinco escalares e a evidencia que [ResultQueries.record] grava, de qualquer um dos dois casos
 * de [ApuracaoParaEnvio] (`slice-5b-4-envio-da-parcial`, design decisao 5).
 *
 * Uma parcial nunca e `closed`, por construcao de [com.platos.domain.scoring.PartialScore] — o
 * `false` aqui e o mesmo que o tipo ja garante, e nao uma segunda declaracao dele.
 */
private data class CamposGravaveis(
    val packageHash: String,
    val variantId: String,
    val points: Int,
    val maxScore: Int,
    val closed: Boolean,
    val outcomes: List<QuestionOutcome>,
)

private fun ApuracaoParaEnvio.paraGravacao(): CamposGravaveis = when (this) {
    is ApuracaoParaEnvio.Completa -> CamposGravaveis(
        packageHash = score.packageHash,
        variantId = score.variantId,
        points = score.points,
        maxScore = score.maxScore,
        closed = score.closed,
        outcomes = score.outcomes,
    )
    is ApuracaoParaEnvio.Parcial -> CamposGravaveis(
        packageHash = score.packageHash,
        variantId = score.variantId,
        points = score.objectivePoints,
        maxScore = score.maxScore,
        closed = false,
        outcomes = score.outcomes,
    )
}

/** Uma linha de evidencia pronta para o banco: o que `answer_observation` guarda. */
internal class Evidencia(
    val itemId: String,
    val answerKind: String,
    val answerOptions: Array<String?>,
    val worth: Int,
    val earned: java.math.BigDecimal,
)

/** Tudo o que `grading_result` e `answer_observation` guardam de **uma** gravacao, qualquer que seja a origem. */
internal class Gravacao(
    val captureId: String,
    val studentToken: String?,
    val capturedAt: String,
    val origin: String,
    val path: String?,
    val completesCaptureId: String?,
    val packageHash: String,
    val variantId: String,
    val points: java.math.BigDecimal,
    val maxScore: Int,
    val closed: Boolean,
    val evidencias: List<Evidencia>,
)

/** Centesimos exatos para o `numeric(8,2)`: sem passar por `Double`. */
private fun Pontos.paraBigDecimal(): BigDecimal = BigDecimal.valueOf(centesimos, 2)
