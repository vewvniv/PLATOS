package com.platos.api

import com.platos.api.config.AppConfig
import com.platos.api.http.BUILD_HEADER
import com.platos.api.http.healthRoutes
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class HealthTest {

    @Test
    fun `health responde 200`() = testApplication {
        // A fiacao ganhou o identificador; as **asercoes** deste cenario sao as mesmas de antes, e e
        // isso que prova que declarar o build e mudanca aditiva. Se alguma delas tivesse mudado,
        // algum consumidor do corpo teria mudado junto.
        application { routing { healthRoutes("sha-cdd12e8") } }

        val response = client.get("/health")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("ok", response.bodyAsText())
    }

    @Test
    fun `a resposta declara o build que esta servindo`() = testApplication {
        application { routing { healthRoutes("sha-cdd12e8") } }

        val response = client.get("/health")

        assertEquals("sha-cdd12e8", response.headers[BUILD_HEADER])
        // E o corpo continua intocado no mesmo cenario: o cabecalho nao substituiu nada.
        assertEquals("ok", response.bodyAsText())
    }

    @Test
    fun `sem identificador, a resposta declara desconhecido em vez de omitir`() = testApplication {
        application { routing { healthRoutes(AppConfig.BUILD_DESCONHECIDO) } }

        val response = client.get("/health")

        // **Presente, e nao omitido.** Cabecalho ausente e indistinguivel de intermediario que o
        // removeu no caminho — e quem consulta esta rota esta justamente tentando descobrir o que
        // esta no ar. "Nao sei" e uma resposta; silencio nao e.
        assertNotNull(response.headers[BUILD_HEADER], "o cabecalho foi omitido quando o build e desconhecido")
        assertEquals(AppConfig.BUILD_DESCONHECIDO, response.headers[BUILD_HEADER])
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `dois builds diferentes sao distinguiveis pela resposta`() = testApplication {
        // O requisito que a mudanca existe para cumprir: duas imagens do mesmo repositorio precisam
        // ser distinguiveis **sem** acesso ao painel de quem hospeda. Um cenario que so olhasse um
        // build passaria com o cabecalho fixo num literal.
        application { routing { healthRoutes("sha-771bdbd") } }

        assertEquals("sha-771bdbd", client.get("/health").headers[BUILD_HEADER])
    }
}
