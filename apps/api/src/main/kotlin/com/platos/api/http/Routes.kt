package com.platos.api.http

import com.platos.api.ApiDependencies
import com.platos.api.auth.SUPABASE_AUTH
import com.platos.api.auth.toAuthenticatedSubject
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import java.util.UUID

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

/** Cabecalho que declara o `content_hash` fora do corpo (ADR-0013, decisao 2). */
const val PACKAGE_CONTENT_HASH_HEADER = "X-Package-Content-Hash"

/**
 * As rotas de prova publicada: escolher qual, e puxar o pacote dela.
 *
 * **A organizacao vem no caminho, e nao do vinculo do chamador.** `short_id` e unique global, entao
 * `/exams/{shortId}/package` funcionaria — mas a organizacao ativa e uma escolha *do aparelho*
 * (fatia 4a-zero), e deixa-la implicita faria o servidor decidir por conta propria qual organizacao
 * o pedido significa quando o usuario tem duas. Com ela no caminho, o cache do aparelho e o pedido
 * usam o **mesmo** identificador.
 *
 * **Ausencia e ausencia, e nunca 403.** Organizacao a que o chamador nao pertence, prova que nao
 * existe e prova sem pacote sao a mesma resposta: 403 confirmaria que a prova existe, que e
 * exatamente o que a spec proibe revelar.
 */
fun Route.examRoutes(deps: ApiDependencies) {
    authenticate(SUPABASE_AUTH) {
        get("/organizations/{organizationId}/exams") {
            val organizationId = call.parameters["organizationId"]?.let(::uuidOrNull)
                ?: return@get call.naoEncontrado()

            val userId = call.resolverUsuario(deps)
            val provas = deps.tenancy.asUser(userId) { ctx ->
                deps.examQueries.listPublished(ctx, organizationId)
            }

            call.respond(provas)
        }

        get("/organizations/{organizationId}/exams/{shortId}/package") {
            val organizationId = call.parameters["organizationId"]?.let(::uuidOrNull)
                ?: return@get call.naoEncontrado()
            val shortId = call.parameters["shortId"] ?: return@get call.naoEncontrado()

            val userId = call.resolverUsuario(deps)
            val pacote = deps.tenancy.asUser(userId) { ctx ->
                deps.examQueries.findPackage(ctx, organizationId, shortId)
            } ?: return@get call.naoEncontrado()

            call.response.header(PACKAGE_CONTENT_HASH_HEADER, pacote.contentHash)

            // `respondBytes`, e nao `respondText`: este ultimo negocia charset, e o `content_hash`
            // foi calculado sobre UTF-8 sem BOM. Qualquer coisa que reabra essa decisao no caminho
            // de saida quebra a conferencia de integridade do aparelho — sem sintoma na tela, com o
            // pacote continuando integro e o hash deixando de bater. E tambem o que passa ao largo
            // do `ContentNegotiation`, que reserializaria o conteudo se ele fosse um objeto.
            call.respondBytes(
                bytes = pacote.content.encodeToByteArray(),
                contentType = ContentType.Application.Json,
            )
        }
    }
}

/**
 * O `app_user` do token, provisionando a organizacao pessoal se for o primeiro acesso (D-0.4).
 *
 * E a mesma chamada de `/me/organizations`, e ela e idempotente. Sem ela, um token valido cuja
 * primeira requisicao fosse a de provas nao teria linha em `app_user`, e `asUser` poria na sessao um
 * identificador que nao existe — a RLS devolveria vazio, e a resposta seria "nenhuma prova" em vez
 * de "voce ainda nao tem organizacao".
 */
private suspend fun io.ktor.server.application.ApplicationCall.resolverUsuario(
    deps: ApiDependencies,
): UUID {
    val subject = principal<JWTPrincipal>()!!.toAuthenticatedSubject()
    return deps.identityBootstrap.bootstrap(
        authSubject = subject.authSubject,
        email = subject.email,
        displayName = subject.displayName,
    )
}

private suspend fun io.ktor.server.application.ApplicationCall.naoEncontrado() =
    respondText("nao ha prova publicada com esse identificador", status = HttpStatusCode.NotFound)

/**
 * Identificador malformado e ausencia, e nao pedido invalido.
 *
 * 400 diria "voce escreveu errado" para quem pediu uma organizacao que nao existe, e a diferenca
 * entre as duas respostas e informacao sobre o que existe do outro lado.
 */
private fun uuidOrNull(texto: String): UUID? = try {
    UUID.fromString(texto)
} catch (_: IllegalArgumentException) {
    null
}
