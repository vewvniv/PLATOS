package com.platos.android.corpus

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Nenhum codigo que fala com a rede toca o corpus (`slice-5d-corpus-de-medicao`, spec `measurement-corpus`: "nada
 * passa pelo servidor"). E o `grep` que a 5c-1 registrou para `respostas/`, agora num teste: uma leitura nova escrita
 * amanha reprova na hora, e nao na proxima vez que alguem lembrar de rodar o `grep`.
 *
 * **O que ele nao prova:** que nenhum caminho novo, fora destes quatro pacotes, leia o corpus. `ARespostaNaoSaiDoAparelhoTest`
 * continua afirmando sobre o corpo do envio (P16: esta e a camada vizinha).
 */
class NenhumEnvioLeOCorpusTest {

    private val raiz = File("src/main/kotlin/com/platos/android")
    private val pacotesDeRede = listOf("api", "outbox", "net", "auth")
    private val proibidos = listOf("corpus", "ColetaDoCorpus", "AmostraDoCorpus", "coleta-ligada")

    @Test
    fun `api, outbox, net e auth nao citam o corpus`() {
        val arquivos = pacotesDeRede.flatMap { pacote ->
            File(raiz, pacote).walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        }
        // Piso (P13): varredura que nao abre arquivo nenhum passaria em silencio.
        assertTrue(arquivos.size >= 10, "a varredura abriu so ${arquivos.size} arquivo(s): o caminho relativo mudou?")

        val achados = arquivos.flatMap { arquivo ->
            val texto = arquivo.readText()
            proibidos.filter { it in texto }.map { "${arquivo.path}: $it" }
        }

        assertEquals(emptyList<String>(), achados)
    }
}
