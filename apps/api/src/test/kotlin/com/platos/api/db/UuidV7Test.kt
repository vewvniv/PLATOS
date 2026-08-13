package com.platos.api.db

import com.platos.api.support.PostgresSupport
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** D11 / D-0.6. */
class UuidV7Test {

    @BeforeTest
    fun setUp() = PostgresSupport.start()

    @Test
    fun `uuid_generate_v7 devolve uuid de versao 7 e variante RFC 4122`() {
        val gerado = PostgresSupport.adminDataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("select uuid_generate_v7()").use { rows ->
                    check(rows.next())
                    rows.getObject(1, UUID::class.java)
                }
            }
        }

        assertEquals(7, gerado.version(), "nibble de versao precisa ser 7")
        assertEquals(2, gerado.variant(), "variante RFC 4122 (10xx)")
    }

    @Test
    fun `ids gerados em sequencia sao crescentes`() {
        // A ordenacao temporal e a razao de escolher v7 em vez de v4: preserva localidade de indice.
        // D-0.6 define o layout como 48 bits de milissegundos mais bits aleatorios, sem contador
        // intra-milissegundo: o que se garante — e o que sustenta a localidade de indice — e que o
        // prefixo de tempo nunca regrida. Exigir ordem total entre ids do mesmo milissegundo seria
        // exigir monotonicidade que a funcao nao promete.
        val ids = PostgresSupport.adminDataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(
                    "select uuid_generate_v7() from generate_series(1, 50)",
                ).use { rows ->
                    buildList {
                        while (rows.next()) add(rows.getObject(1, UUID::class.java))
                    }
                }
            }
        }

        val prefixos = ids.map { it.mostSignificantBits ushr 16 }
        assertEquals(prefixos.sorted(), prefixos, "o prefixo de tempo nao pode regredir")
        assertEquals(ids.size, ids.toSet().size, "nao pode haver colisao")
    }

    @Test
    fun `ids gerados em milissegundos distintos ordenam lexicograficamente`() {
        // Uma chamada por milissegundo: e essa a granularidade que o layout de D-0.6 registra.
        val ids = PostgresSupport.adminDataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                (1..5).map {
                    Thread.sleep(2)
                    statement.executeQuery("select uuid_generate_v7()").use { rows ->
                        check(rows.next())
                        rows.getObject(1, UUID::class.java)
                    }
                }
            }
        }

        val comoTexto = ids.map { it.toString() }
        assertEquals(comoTexto.sorted(), comoTexto, "UUIDv7 deve ordenar lexicograficamente por tempo")
    }

    @Test
    fun `timestamp embutido corresponde ao momento da geracao`() {
        val antes = System.currentTimeMillis()
        val gerado = PostgresSupport.adminDataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("select uuid_generate_v7()").use { rows ->
                    check(rows.next())
                    rows.getObject(1, UUID::class.java)
                }
            }
        }
        val depois = System.currentTimeMillis()

        val millis = gerado.mostSignificantBits ushr 16
        assertTrue(
            millis in (antes - 5_000)..(depois + 5_000),
            "timestamp embutido ($millis) fora da janela [$antes, $depois]",
        )
    }
}
