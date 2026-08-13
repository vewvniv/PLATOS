package com.platos.api.support

import com.auth0.jwk.Jwk
import com.auth0.jwk.JwkProvider
import com.auth0.jwk.SigningKeyNotFoundException
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.platos.api.config.JwtConfig
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.time.Instant
import java.util.Base64
import java.util.Date

/**
 * Par de chaves gerado no proprio teste e servido por um [JwkProvider] em memoria.
 *
 * Verifica o caminho real de validacao do Ktor (assinatura, issuer, audience, expiracao) sem
 * depender de rede nem de um projeto Supabase — o que tornaria a suite lenta e flaky por motivos
 * que nada tem a ver com o que ela verifica.
 */
object JwtTestFixture {

    const val ISSUER = "https://teste.supabase.co/auth/v1"
    const val AUDIENCE = "authenticated"
    private const val KID = "chave-de-teste"

    private val geradorRsa = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }

    private val parConhecido = geradorRsa.generateKeyPair()
    private val parDesconhecido = geradorRsa.generateKeyPair()

    val config = JwtConfig(
        issuer = ISSUER,
        audience = AUDIENCE,
        jwksUrl = "http://localhost:1/jwks-nunca-acessado.json",
    )

    val jwkProvider = JwkProvider { keyId ->
        if (keyId == KID) jwkConhecida else throw SigningKeyNotFoundException("kid desconhecido: $keyId", null)
    }

    private val jwkConhecida: Jwk by lazy {
        val publica = parConhecido.public as RSAPublicKey
        Jwk.fromValues(
            mapOf(
                "kty" to "RSA",
                "kid" to KID,
                "alg" to "RS256",
                "use" to "sig",
                "n" to base64Url(publica.modulus.toByteArray()),
                "e" to base64Url(publica.publicExponent.toByteArray()),
            ),
        )
    }

    fun token(
        subject: String,
        email: String? = null,
        fullName: String? = null,
        issuer: String = ISSUER,
        audience: String = AUDIENCE,
        expiresAt: Instant = Instant.now().plusSeconds(3600),
        chaveDesconhecida: Boolean = false,
    ): String {
        val par = if (chaveDesconhecida) parDesconhecido else parConhecido

        return JWT.create()
            .withKeyId(KID)
            .withIssuer(issuer)
            .withAudience(audience)
            .withSubject(subject)
            .withIssuedAt(Date.from(Instant.now().minusSeconds(60)))
            .withExpiresAt(Date.from(expiresAt))
            .apply {
                if (email != null) withClaim("email", email)
                if (fullName != null) withClaim("user_metadata", mapOf("full_name" to fullName))
            }
            .sign(Algorithm.RSA256(par.public as RSAPublicKey, par.private as RSAPrivateKey))
    }

    private fun base64Url(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}
