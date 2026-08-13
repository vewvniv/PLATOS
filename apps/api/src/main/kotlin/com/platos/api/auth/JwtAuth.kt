package com.platos.api.auth

import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import com.platos.api.config.JwtConfig
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.response.respond
import java.net.URI
import java.util.concurrent.TimeUnit

const val SUPABASE_AUTH = "supabase"

/**
 * §13: Supabase Auth validada por JWKS.
 *
 * A identidade vem exclusivamente do `sub` do token verificado. Nada de cabecalho, query ou corpo
 * participa da identificacao — e o teste de identidade forjada existe para manter isso verdadeiro
 * quando alguem, um dia, achar conveniente aceitar um `?userId=`.
 */
fun Application.configureAuthentication(
    config: JwtConfig,
    // Costura para teste: permite verificar contra um par de chaves gerado no proprio teste, sem
    // depender de rede nem de um projeto Supabase real.
    jwkProvider: JwkProvider = JwkProviderBuilder(URI(config.jwksUrl).toURL())
        .cached(10, 24, TimeUnit.HOURS)
        .rateLimited(10, 1, TimeUnit.MINUTES)
        .build(),
) {
    install(Authentication) {
        jwt(SUPABASE_AUTH) {
            verifier(jwkProvider, config.issuer) {
                acceptLeeway(3)
                withAudience(config.audience)
            }
            validate { credential ->
                val subject = credential.payload.subject
                if (subject.isNullOrBlank()) null else JWTPrincipal(credential.payload)
            }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized)
            }
        }
    }
}

/** Identidade autenticada, ja reduzida ao que o dominio usa. */
data class AuthenticatedSubject(
    val authSubject: String,
    val email: String?,
    val displayName: String?,
)

fun JWTPrincipal.toAuthenticatedSubject(): AuthenticatedSubject = AuthenticatedSubject(
    authSubject = payload.subject,
    email = payload.getClaim("email").asString(),
    displayName = payload.getClaim("user_metadata")
        ?.asMap()
        ?.get("full_name") as? String,
)
