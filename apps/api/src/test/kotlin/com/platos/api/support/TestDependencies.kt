package com.platos.api.support

import com.platos.api.ApiDependencies
import com.platos.api.billing.EntitlementResolver
import com.platos.api.billing.PlanCatalog
import com.platos.api.config.AppConfig
import com.platos.api.db.Tenancy
import com.platos.api.exam.ExamQueries
import com.platos.api.identity.IdentityBootstrap
import com.platos.api.identity.OrganizationQueries
import java.nio.file.Paths

object TestDependencies {

    val plansDir: java.nio.file.Path
        get() = Paths.get(
            System.getProperty("platos.plans.dir") ?: error("systemProperty platos.plans.dir nao definida"),
        )

    fun create(): ApiDependencies {
        PostgresSupport.start()
        return ApiDependencies(
            tenancy = Tenancy(PostgresSupport.appDataSource),
            identityBootstrap = IdentityBootstrap(PostgresSupport.appDataSource),
            organizationQueries = OrganizationQueries(),
            examQueries = ExamQueries(),
            entitlementResolver = EntitlementResolver(PlanCatalog.load(plansDir)),
            jwt = JwtTestFixture.config,
            // Um artefato de teste nao e construido pelo caminho de publicacao, entao ele
            // legitimamente nao tem identificador — e declarar isso e mais honesto que inventar um.
            build = AppConfig.BUILD_DESCONHECIDO,
        )
    }
}
