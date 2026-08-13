package com.platos.api.auth

import com.platos.api.module
import com.platos.api.support.JwtTestFixture
import com.platos.api.support.PostgresSupport
import com.platos.api.support.TestDependencies
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import java.time.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Os cenarios de "Identidade do usuario derivada do provedor de autenticacao" (specs/identity). */
class AuthenticationTest {

    @BeforeTest
    fun setUp() {
        PostgresSupport.start()
        PostgresSupport.reset()
    }

    @Test
    fun `token valido e aceito`() = comApp { client ->
        val resposta = client.get("/me/organizations") {
            header(HttpHeaders.Authorization, "Bearer ${JwtTestFixture.token("sub-valido", "a@escola.br")}")
        }

        assertEquals(HttpStatusCode.OK, resposta.status)
    }

    @Test
    fun `token ausente responde 401 sem tocar o banco`() = comApp { client ->
        val resposta = client.get("/me/organizations")

        assertEquals(HttpStatusCode.Unauthorized, resposta.status)
        assertNadaFoiEscrito()
    }

    @Test
    fun `token expirado responde 401 sem tocar o banco`() = comApp { client ->
        val expirado = JwtTestFixture.token("sub-expirado", expiresAt = Instant.now().minusSeconds(3600))

        val resposta = client.get("/me/organizations") {
            header(HttpHeaders.Authorization, "Bearer $expirado")
        }

        assertEquals(HttpStatusCode.Unauthorized, resposta.status)
        assertNadaFoiEscrito()
    }

    @Test
    fun `token malformado responde 401 sem tocar o banco`() = comApp { client ->
        val resposta = client.get("/me/organizations") {
            header(HttpHeaders.Authorization, "Bearer isto-nao-e-um-jwt")
        }

        assertEquals(HttpStatusCode.Unauthorized, resposta.status)
        assertNadaFoiEscrito()
    }

    @Test
    fun `token assinado por chave desconhecida responde 401 sem tocar o banco`() = comApp { client ->
        val forjado = JwtTestFixture.token("sub-forjado", chaveDesconhecida = true)

        val resposta = client.get("/me/organizations") {
            header(HttpHeaders.Authorization, "Bearer $forjado")
        }

        assertEquals(HttpStatusCode.Unauthorized, resposta.status)
        assertNadaFoiEscrito()
    }

    @Test
    fun `token de outro emissor responde 401`() = comApp { client ->
        val outroEmissor = JwtTestFixture.token(
            subject = "sub-outro-issuer",
            issuer = "https://atacante.example.com/auth/v1",
        )

        val resposta = client.get("/me/organizations") {
            header(HttpHeaders.Authorization, "Bearer $outroEmissor")
        }

        assertEquals(HttpStatusCode.Unauthorized, resposta.status)
        assertNadaFoiEscrito()
    }

    @Test
    fun `token com audience errada responde 401`() = comApp { client ->
        val outraAudiencia = JwtTestFixture.token("sub-aud", audience = "outro-servico")

        val resposta = client.get("/me/organizations") {
            header(HttpHeaders.Authorization, "Bearer $outraAudiencia")
        }

        assertEquals(HttpStatusCode.Unauthorized, resposta.status)
        assertNadaFoiEscrito()
    }

    /**
     * A identidade vem exclusivamente do `sub` do token. Cabecalho, query e corpo sao ignorados —
     * este teste existe para que continue assim quando alguem achar conveniente aceitar um
     * `?userId=`.
     */
    @Test
    fun `identidade forjada no payload e ignorada`() = comApp { client ->
        val vitima = PostgresSupport.createUser("sub-vitima", "vitima@escola.br")
        val orgDaVitima = PostgresSupport.createOrganization(kind = "school", name = "Escola da Vitima")
        PostgresSupport.addMembership(vitima, orgDaVitima, "teacher")

        val tokenDoAtacante = JwtTestFixture.token("sub-atacante", "atacante@escola.br", "Atacante")

        val resposta = client.get("/me/organizations?userId=$vitima") {
            header(HttpHeaders.Authorization, "Bearer $tokenDoAtacante")
            header("X-User-Id", vitima.toString())
            header("X-Organization-Id", orgDaVitima.toString())
        }

        assertEquals(HttpStatusCode.OK, resposta.status)

        val corpo = resposta.bodyAsText()
        assertFalse(corpo.contains(orgDaVitima.toString()), "vazou organizacao da vitima: $corpo")
        assertTrue(corpo.contains("\"kind\":\"personal\""), "esperava a organizacao pessoal do atacante: $corpo")
    }

    private fun assertNadaFoiEscrito() {
        PostgresSupport.adminDataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("select count(*) from app_user").use { rows ->
                    check(rows.next())
                    assertEquals(0, rows.getInt(1), "requisicao nao autenticada nao pode escrever no banco")
                }
            }
        }
    }

    private fun comApp(block: suspend (HttpClient) -> Unit) = testApplication {
        val dependencias = TestDependencies.create()
        application { module(dependencias, JwtTestFixture.jwkProvider) }
        block(client)
    }
}
