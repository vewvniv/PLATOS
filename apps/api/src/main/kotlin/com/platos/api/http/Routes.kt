package com.platos.api.http

import com.platos.api.ApiDependencies
import com.platos.api.auth.SUPABASE_AUTH
import com.platos.api.auth.toAuthenticatedSubject
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.healthRoutes() {
    get("/health") {
        call.respondText("ok")
    }
}

fun Route.identityRoutes(deps: ApiDependencies) {
    authenticate(SUPABASE_AUTH) {
        get("/me/organizations") {
            val subject = call.principal<JWTPrincipal>()!!.toAuthenticatedSubject()

            // D-0.4: idempotente. No primeiro acesso cria a organizacao pessoal; depois so resolve.
            val userId = deps.identityBootstrap.bootstrap(
                authSubject = subject.authSubject,
                email = subject.email,
                displayName = subject.displayName,
            )

            val organizations = deps.tenancy.asUser(userId) { ctx ->
                deps.organizationQueries.listForCurrentUser(ctx)
            }

            call.respond(organizations)
        }
    }
}
