package com.platos.api.config

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A leitura do identificador do build, sem tocar o ambiente real.
 *
 * `fromEnvironment` recebe o leitor por parametro, entao cada cenario monta o ambiente que ele
 * afirma. Ler `System.getenv` aqui faria o resultado depender da maquina — e o que estes cenarios
 * cobrem e a **decisao** sobre presente, ausente e vazio.
 */
class AppConfigTest {

    /** O minimo que `fromEnvironment` exige para nao estourar em `required`. */
    private val obrigatorias = mapOf(
        "DATABASE_URL" to "jdbc:postgresql://localhost:5432/postgres",
        "DATABASE_PASSWORD" to "segredo-de-teste",
        "JWT_ISSUER" to "https://exemplo.invalid/auth/v1",
        "JWKS_URL" to "https://exemplo.invalid/auth/v1/.well-known/jwks.json",
    )

    private fun configCom(vararg extras: Pair<String, String>): AppConfig =
        AppConfig.fromEnvironment((obrigatorias + extras).let { mapa -> { nome -> mapa[nome] } })

    @Test
    fun `o identificador presente e o que foi passado`() {
        assertEquals("sha-cdd12e8", configCom("PLATOS_BUILD" to "sha-cdd12e8").build)
    }

    @Test
    fun `sem identificador, o build e declarado desconhecido`() {
        // Ausencia dita como ausencia. O valor e nomeado e mora num lugar so, e a asercao aponta
        // para a constante em vez de repetir o literal: quem mudar o texto muda os dois juntos.
        assertEquals(AppConfig.BUILD_DESCONHECIDO, configCom().build)
    }

    @Test
    fun `identificador vazio tambem e ausencia`() {
        // O caso real: um `--build-arg PLATOS_BUILD=` mal formado assa string vazia. Sem esta
        // conferencia ela passaria por "presente" e a resposta declararia nada — pior que declarar
        // desconhecido, porque nada parece cabecalho perdido no caminho.
        assertEquals(AppConfig.BUILD_DESCONHECIDO, configCom("PLATOS_BUILD" to "").build)
    }

    @Test
    fun `identificador com espacos tambem e ausencia`() {
        assertEquals(AppConfig.BUILD_DESCONHECIDO, configCom("PLATOS_BUILD" to "   ").build)
    }

    @Test
    fun `a ausencia do identificador nao impede a configuracao de subir`() {
        // A decisao 3 do design: `required` faria a API recusar subir sem o identificador, e
        // transformaria observabilidade em modo novo de falha de arranque. Este cenario e o que
        // segura essa decisao — ele passa por `fromEnvironment` inteiro, e nao so pelo campo.
        val config = configCom()

        assertEquals("app_backend", config.database.user)
        assertEquals(8080, config.port)
        assertEquals(AppConfig.BUILD_DESCONHECIDO, config.build)
    }
}
