package com.platos.api.http

import com.platos.api.http.dto.OrganizationDto
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
import kotlinx.serialization.json.Json
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Os cenarios de "Consulta das organizacoes do usuario" (specs/identity). */
class MeOrganizationsTest {

    private val json = Json { ignoreUnknownKeys = true }

    @BeforeTest
    fun setUp() {
        PostgresSupport.start()
        PostgresSupport.reset()
    }

    @Test
    fun `usuario recem-criado ve exatamente uma organizacao pessoal como owner`() = comApp { client ->
        val organizacoes = client.organizacoesDe("sub-novo", "nova@escola.br", "Nova Professora")

        assertEquals(1, organizacoes.size)
        assertEquals("personal", organizacoes.single().kind)
        assertEquals("owner", organizacoes.single().role)
        assertEquals("Nova Professora", organizacoes.single().name)
    }

    @Test
    fun `usuario com multiplos vinculos ve as tres organizacoes com seus papeis`() = comApp { client ->
        // Primeiro acesso provisiona a organizacao pessoal.
        client.organizacoesDe("sub-multi", "multi@escola.br", "Professor Multi")

        val userId = idDoUsuario("sub-multi")
        val escolaA = PostgresSupport.createOrganization(kind = "school", name = "Escola A")
        val escolaB = PostgresSupport.createOrganization(kind = "school", name = "Escola B")
        PostgresSupport.addMembership(userId, escolaA, "teacher")
        PostgresSupport.addMembership(userId, escolaB, "admin")

        val organizacoes = client.organizacoesDe("sub-multi", "multi@escola.br", "Professor Multi")

        assertEquals(3, organizacoes.size)
        assertEquals(
            setOf("personal" to "owner", "school" to "teacher", "school" to "admin"),
            organizacoes.map { it.kind to it.role }.toSet(),
        )
        assertEquals(
            setOf("Professor Multi", "Escola A", "Escola B"),
            organizacoes.map { it.name }.toSet(),
        )
    }

    @Test
    fun `organizacoes de terceiros nao vazam`() = comApp { client ->
        val outro = PostgresSupport.createUser("sub-outro", "outro@escola.br")
        val escolaAlheia = PostgresSupport.createOrganization(kind = "school", name = "Escola Alheia")
        PostgresSupport.addMembership(outro, escolaAlheia, "teacher")
        PostgresSupport.createOrganization(kind = "school", name = "Escola Sem Membros")

        val organizacoes = client.organizacoesDe("sub-proprio", "proprio@escola.br", "Proprio")

        assertEquals(1, organizacoes.size)
        assertEquals("personal", organizacoes.single().kind)
        assertFalse(organizacoes.any { it.name == "Escola Alheia" })
        assertFalse(organizacoes.any { it.name == "Escola Sem Membros" })
    }

    @Test
    fun `requisicao nao autenticada responde 401 sem revelar organizacoes`() = comApp { client ->
        PostgresSupport.createOrganization(kind = "school", name = "Escola Secreta")

        val resposta = client.get("/me/organizations")

        assertEquals(HttpStatusCode.Unauthorized, resposta.status)
        assertFalse(resposta.bodyAsText().contains("Escola Secreta"))
    }

    @Test
    fun `resposta traz identificador utilizavel de cada organizacao`() = comApp { client ->
        val organizacoes = client.organizacoesDe("sub-id", "id@escola.br", "Com Id")

        assertTrue(organizacoes.single().id.isNotBlank())
        assertEquals(
            organizacoes.single().id,
            idDaOrganizacaoPessoal("sub-id").toString(),
        )
    }

    private suspend fun HttpClient.organizacoesDe(
        subject: String,
        email: String,
        fullName: String,
    ): List<OrganizationDto> {
        val resposta = get("/me/organizations") {
            header(HttpHeaders.Authorization, "Bearer ${JwtTestFixture.token(subject, email, fullName)}")
        }
        assertEquals(HttpStatusCode.OK, resposta.status)
        return json.decodeFromString(resposta.bodyAsText())
    }

    private fun idDoUsuario(authSubject: String): java.util.UUID =
        PostgresSupport.adminDataSource.connection.use { connection ->
            connection.prepareStatement("select id from app_user where auth_subject = ?").use { statement ->
                statement.setString(1, authSubject)
                statement.executeQuery().use { rows ->
                    check(rows.next())
                    rows.getObject(1, java.util.UUID::class.java)
                }
            }
        }

    private fun idDaOrganizacaoPessoal(authSubject: String): java.util.UUID =
        PostgresSupport.adminDataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                select o.id
                from organization o
                join membership m on m.organization_id = o.id
                join app_user u on u.id = m.user_id
                where u.auth_subject = ? and o.kind = 'personal'
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, authSubject)
                statement.executeQuery().use { rows ->
                    check(rows.next())
                    rows.getObject(1, java.util.UUID::class.java)
                }
            }
        }

    private fun comApp(block: suspend (HttpClient) -> Unit) = testApplication {
        val dependencias = TestDependencies.create()
        application { module(dependencias, JwtTestFixture.jwkProvider) }
        block(client)
    }
}
