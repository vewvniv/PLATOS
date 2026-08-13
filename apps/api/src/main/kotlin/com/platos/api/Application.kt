package com.platos.api

import com.auth0.jwk.JwkProvider
import com.platos.api.auth.configureAuthentication
import com.platos.api.billing.EntitlementResolver
import com.platos.api.billing.PlanCatalog
import com.platos.api.config.AppConfig
import com.platos.api.config.JwtConfig
import com.platos.api.db.DataSourceFactory
import com.platos.api.db.Tenancy
import com.platos.api.http.healthRoutes
import com.platos.api.http.identityRoutes
import com.platos.api.identity.IdentityBootstrap
import com.platos.api.identity.OrganizationQueries
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing

class ApiDependencies(
    val tenancy: Tenancy,
    val identityBootstrap: IdentityBootstrap,
    val organizationQueries: OrganizationQueries,
    val entitlementResolver: EntitlementResolver,
    val jwt: JwtConfig,
)

fun main() {
    val config = AppConfig.fromEnvironment()

    // D-0.8: plano invalido ou ausente derruba o boot, em vez de virar direito vazio silencioso.
    val planCatalog = PlanCatalog.load(config.plansDir)

    val dataSource = DataSourceFactory.create(config.database)

    val dependencies = ApiDependencies(
        tenancy = Tenancy(dataSource),
        identityBootstrap = IdentityBootstrap(dataSource),
        organizationQueries = OrganizationQueries(),
        entitlementResolver = EntitlementResolver(planCatalog),
        jwt = config.jwt,
    )

    embeddedServer(Netty, port = config.port) {
        module(dependencies)
    }.start(wait = true)
}

fun Application.module(dependencies: ApiDependencies, jwkProvider: JwkProvider? = null) {
    install(ContentNegotiation) { json() }
    if (jwkProvider == null) {
        configureAuthentication(dependencies.jwt)
    } else {
        configureAuthentication(dependencies.jwt, jwkProvider)
    }

    routing {
        healthRoutes()
        identityRoutes(dependencies)
    }
}
