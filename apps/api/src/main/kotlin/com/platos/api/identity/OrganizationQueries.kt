package com.platos.api.identity

import com.platos.api.db.generated.tables.references.MEMBERSHIP
import com.platos.api.db.generated.tables.references.ORGANIZATION
import com.platos.api.http.dto.OrganizationDto
import org.jooq.DSLContext

class OrganizationQueries {

    /**
     * Repare que nao ha `where user_id = ...` nem `where organization_id in (...)`.
     *
     * Isso e deliberado e e o ponto inteiro da fatia: quem restringe as linhas e a RLS
     * (`membership_self_select` e `organization_member_select`), a partir do usuario posto na
     * sessao por [com.platos.api.db.Tenancy.asUser]. Um filtro escrito aqui seria uma segunda
     * fonte de verdade de autorizacao — exatamente o erro que §3.2 chama de unico erro caro
     * possivel neste desenho.
     */
    fun listForCurrentUser(ctx: DSLContext): List<OrganizationDto> =
        ctx.select(ORGANIZATION.ID, ORGANIZATION.NAME, ORGANIZATION.KIND, MEMBERSHIP.ROLE)
            .from(ORGANIZATION)
            .join(MEMBERSHIP).on(MEMBERSHIP.ORGANIZATION_ID.eq(ORGANIZATION.ID))
            .orderBy(ORGANIZATION.CREATED_AT)
            .fetch { record ->
                OrganizationDto(
                    id = record.value1().toString(),
                    name = record.value2() ?: "",
                    kind = record.value3() ?: "",
                    role = record.value4() ?: "",
                )
            }
}
