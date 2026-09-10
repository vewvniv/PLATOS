package com.platos.api.exam

import com.platos.api.db.generated.tables.references.EXAM
import com.platos.api.db.generated.tables.references.EXAM_PACKAGE
import com.platos.api.http.dto.ExamSummaryDto
import org.jooq.DSLContext
import java.util.UUID

/**
 * O pacote publicado como ele sai do banco: os bytes exatos e o hash que os cobre.
 *
 * Os dois viajam juntos de proposito. Quem confere precisa dos dois, e busca-los em duas chamadas
 * abriria a possibilidade de conferir conteudo contra o hash de outra linha.
 */
data class PackageContent(
    val content: String,
    val contentHash: String,
)

/**
 * As consultas de prova publicada (ADR-0013, decisao 2).
 *
 * **A autorizacao continua sendo da RLS**, como em [com.platos.api.identity.OrganizationQueries]. O
 * `where organization_id = ?` daqui e o **seletor** que veio do caminho da rota — qual organizacao o
 * pedido significa —, e nao a fronteira: quem restringe as linhas visiveis e
 * `exam_member_select`/`exam_package_member_select`, a partir do usuario posto na sessao por
 * [com.platos.api.db.Tenancy.asUser].
 *
 * A diferenca importa quando o chamador pede uma organizacao a que nao pertence: o seletor casa, a
 * RLS nao devolve linha, e o desfecho e ausencia — o mesmo de prova inexistente. E o que a spec
 * exige, e vem de nao haver filtro de autorizacao escrito aqui para ser esquecido depois.
 */
class ExamQueries {

    /**
     * As provas publicadas de uma organizacao.
     *
     * **`join`, e nao filtro posterior.** Prova sem pacote nao e uma escolha oferecivel: deixa-la
     * aparecer produziria um item de lista que so falha ao ser tocado, e a recusa chegaria depois
     * de o professor ja ter escolhido.
     */
    fun listPublished(ctx: DSLContext, organizationId: UUID): List<ExamSummaryDto> =
        ctx.select(EXAM.SHORT_ID, EXAM.TITLE, EXAM_PACKAGE.CONTENT_HASH)
            .from(EXAM)
            .join(EXAM_PACKAGE).on(EXAM_PACKAGE.EXAM_ID.eq(EXAM.ID))
            .where(EXAM.ORGANIZATION_ID.eq(organizationId))
            .orderBy(EXAM.CREATED_AT)
            .fetch { record ->
                ExamSummaryDto(
                    shortId = record.value1() ?: "",
                    title = record.value2() ?: "",
                    contentHash = record.value3() ?: "",
                )
            }

    /**
     * O pacote de uma prova, ou `null` quando nao ha o que entregar.
     *
     * `null` cobre tres situacoes que a rota trata como uma so — prova inexistente, prova sem pacote
     * e prova de organizacao a que o chamador nao pertence. Distingui-las na resposta revelaria a
     * existencia da prova a quem nao pode ve-la.
     *
     * O `content` sai como esta gravado. Nenhuma reserializacao acontece aqui, e e por isso que
     * ADR-0008 escolheu `text` em vez de `jsonb`: o hash e sobre estes bytes.
     */
    fun findPackage(ctx: DSLContext, organizationId: UUID, shortId: String): PackageContent? =
        ctx.select(EXAM_PACKAGE.CONTENT, EXAM_PACKAGE.CONTENT_HASH)
            .from(EXAM_PACKAGE)
            .join(EXAM).on(EXAM.ID.eq(EXAM_PACKAGE.EXAM_ID))
            .where(EXAM.ORGANIZATION_ID.eq(organizationId))
            .and(EXAM.SHORT_ID.eq(shortId))
            .fetchOne { record ->
                PackageContent(
                    content = record.value1() ?: "",
                    contentHash = record.value2() ?: "",
                )
            }
}
