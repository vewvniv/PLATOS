package com.platos.api.exam

import com.platos.api.db.generated.tables.references.ANSWER_OBSERVATION
import com.platos.api.db.generated.tables.references.EXAM
import com.platos.api.db.generated.tables.references.GRADING_RESULT
import com.platos.api.http.dto.ResultSubmissionDto
import com.platos.domain.capture.QuestionAnswer
import com.platos.domain.scoring.ObjectiveScore
import org.jooq.DSLContext
import org.jooq.impl.DSL
import java.time.OffsetDateTime
import java.util.UUID

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
        nota: ObjectiveScore,
    ): Int {
        val jaGravada = ctx.select(GRADING_RESULT.REVISION)
            .from(GRADING_RESULT)
            .where(GRADING_RESULT.EXAM_ID.eq(examId))
            .and(GRADING_RESULT.CAPTURE_ID.eq(submission.captureId))
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
                    DSL.value(submission.studentToken),
                ),
            )
            .fetchOne { it.value1() } ?: 1

        val resultadoId = ctx.insertInto(GRADING_RESULT)
            .set(GRADING_RESULT.ORGANIZATION_ID, organizationId)
            .set(GRADING_RESULT.EXAM_ID, examId)
            .set(GRADING_RESULT.STUDENT_TOKEN, submission.studentToken)
            .set(GRADING_RESULT.REVISION, proxima)
            .set(GRADING_RESULT.CAPTURE_ID, submission.captureId)
            // Leitura optica. `ai` e `teacher` sao das fatias 5 e 8, e o valor e explicito aqui para
            // que o dia em que existir outra origem nao dependa do default da coluna.
            .set(GRADING_RESULT.ORIGIN, "omr")
            .set(GRADING_RESULT.PACKAGE_HASH, nota.packageHash)
            .set(GRADING_RESULT.VARIANT_ID, nota.variantId)
            .set(GRADING_RESULT.POINTS, nota.points)
            .set(GRADING_RESULT.MAX_SCORE, nota.maxScore)
            .set(GRADING_RESULT.CLOSED, nota.closed)
            .set(GRADING_RESULT.CAPTURED_AT, OffsetDateTime.parse(submission.capturedAt))
            .returningResult(GRADING_RESULT.ID)
            .fetchOne { it.value1() }!!

        for (outcome in nota.outcomes) {
            ctx.insertInto(ANSWER_OBSERVATION)
                .set(ANSWER_OBSERVATION.ORGANIZATION_ID, organizationId)
                .set(ANSWER_OBSERVATION.GRADING_RESULT_ID, resultadoId)
                .set(ANSWER_OBSERVATION.ITEM_ID, outcome.questionId)
                .set(ANSWER_OBSERVATION.ANSWER_KIND, outcome.answer.tipoGravado())
                .set(ANSWER_OBSERVATION.ANSWER_OPTIONS, outcome.answer.alternativas())
                .set(ANSWER_OBSERVATION.WORTH, outcome.worth)
                .set(ANSWER_OBSERVATION.EARNED, outcome.earned)
                .execute()
        }

        return proxima
    }
}

/** Os quatro valores que o `check` da migration admite, escritos num lugar so. */
private fun QuestionAnswer.tipoGravado(): String = when (this) {
    is QuestionAnswer.Marcada -> "marcada"
    is QuestionAnswer.EmBranco -> "em_branco"
    is QuestionAnswer.MultiplaMarcacao -> "multipla_marcacao"
    is QuestionAnswer.Indecisa -> "indecisa"
}

/**
 * As alternativas envolvidas, e nao a "vencedora".
 *
 * Em `multipla_marcacao` e `indecisa` sao todas: desempatar por qualquer criterio transformaria
 * rasura em resposta, e a rasura e justamente o caso em que a folha nao diz o que o aluno quis.
 */
private fun QuestionAnswer.alternativas(): Array<String?> = when (this) {
    is QuestionAnswer.Marcada -> arrayOf(option)
    is QuestionAnswer.EmBranco -> emptyArray()
    is QuestionAnswer.MultiplaMarcacao -> options.toTypedArray()
    is QuestionAnswer.Indecisa -> options.toTypedArray()
}
