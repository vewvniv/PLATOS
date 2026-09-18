package com.platos.build

import org.testcontainers.containers.PostgreSQLContainer
import java.sql.Driver
import java.sql.DriverManager
import java.sql.SQLException
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A guarda de `conectarAoPostgres`, e o motivo de ela existir num lugar onde nada mais tem teste.
 *
 * `:apps:api:generateJooq` falhava de forma intermitente com
 * `java.sql.SQLException: No suitable driver found for jdbc:postgresql://...`. A causa nunca foi
 * medida — 2 execucoes de 7 num dia, 0 de 4 no outro. O que se pode afirmar, e o que este teste
 * afirma, e outra coisa: **o estado em que aquele erro e obrigatorio nao derruba mais a aquisicao.**
 *
 * Esperar a intermitencia aparecer nao e metodo. O teste **produz** a condicao: com o
 * `DriverManager` sem nenhum driver registrado, a busca que gera aquela mensagem nao tem como
 * encontrar nada. E onde o codigo antigo era obrigado a falhar.
 */
class AquisicaoDeConexaoTest {

    @Test
    fun `a aquisicao conecta com o DriverManager vazio`() {
        PostgreSQLContainer("postgres:16-alpine").use { postgres ->
            postgres.start()

            semDriversRegistrados {
                // O canario, e ele vem **antes** da afirmacao que importa (P13). Sem ele, este teste
                // passaria num processo onde o desregistro nao funcionou — e entao ele estaria
                // afirmando "conecta com os drivers registrados", que e o que sempre funcionou.
                val buscaFalhou = assertFailsWith<SQLException> {
                    DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password)
                }
                assertContains(
                    buscaFalhou.message.orEmpty(),
                    "No suitable driver found",
                    message = "o desregistro nao produziu a condicao que este teste precisa",
                )

                conectarAoPostgres(postgres.jdbcUrl, postgres.username, postgres.password).use { conexao ->
                    assertFalse(conexao.isClosed, "a conexao veio fechada")
                    conexao.createStatement().use { statement ->
                        statement.executeQuery("select 1").use { linhas ->
                            assertTrue(linhas.next(), "a conexao nao respondeu a consulta mais simples")
                            assertEquals(1, linhas.getInt(1))
                        }
                    }
                }
            }
        }
    }

    /**
     * Desregistra todo driver, roda o bloco, e **devolve** o estado.
     *
     * O `DriverManager` e global ao processo. Deixar o daemon do Gradle sem drivers registrados
     * depois deste teste envenenaria qualquer teste vizinho que dependesse da busca — o defeito que
     * este arquivo investiga, plantado por quem o investiga.
     */
    private fun semDriversRegistrados(bloco: () -> Unit) {
        val registrados: List<Driver> = DriverManager.getDrivers().toList()
        registrados.forEach { DriverManager.deregisterDriver(it) }
        try {
            bloco()
        } finally {
            registrados.forEach { DriverManager.registerDriver(it) }
        }
    }
}
