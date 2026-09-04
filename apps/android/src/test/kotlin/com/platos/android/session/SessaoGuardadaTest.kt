package com.platos.android.session

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.io.IOException
import java.security.GeneralSecurityException
import javax.crypto.AEADBadTagException

/**
 * O keyset corrompido, forcado de proposito.
 *
 * E o unico modo de falha conhecido da escolha da decisao 5, e desde que `security-crypto` esta
 * depreciada ele e responsabilidade desta base. Verifica-lo exigiria emulador se a decisao morasse
 * dentro do adaptador; ela mora em duas funcoes que nao conhecem Android, e por isso o defeito pode
 * ser forcado aqui — a corrupcao entra como excecao lancada por uma lambda, que e exatamente a
 * forma com que ela chega do `EncryptedSharedPreferences`.
 *
 * `AEADBadTagException` aparece nos testes por ser o caso concreto: e o que a decifragem lanca
 * quando o dado nao bate com a chave, e ela desce de `GeneralSecurityException`.
 */
class SessaoGuardadaTest {

    // --- Abrir: tem conserto, entao descarta e tenta de novo ---

    @Test
    fun `abertura normal devolve o valor e nao descarta nada`() {
        var descartes = 0

        val aberto = abrindoOuDescartando(abrir = { "prefs" }, descartar = { descartes++ })

        assertEquals("prefs", aberto)
        assertEquals(0, descartes)
    }

    @Test
    fun `keyset corrompido e descartado, e a segunda tentativa abre`() {
        var descartes = 0
        var tentativas = 0

        val aberto = abrindoOuDescartando(
            abrir = {
                tentativas++
                if (tentativas == 1) throw AEADBadTagException("keyset corrompido") else "prefs"
            },
            descartar = { descartes++ },
        )

        assertEquals("prefs", aberto)
        assertEquals(1, descartes, "descarta uma vez, e nao a cada tentativa")
        assertEquals(2, tentativas)
    }

    @Test
    fun `corrompido nas duas tentativas vira ausencia de sessao, e nao excecao`() {
        var descartes = 0

        val aberto = abrindoOuDescartando<String>(
            abrir = { throw AEADBadTagException("corrompido de novo") },
            descartar = { descartes++ },
        )

        // Sem sessao guardada o aplicativo abre na entrada. E o pior desfecho aceitavel: pedir a
        // senha outra vez. Deixar a excecao subir daria um aplicativo que nao abre.
        assertNull(aberto)
        assertEquals(1, descartes)
    }

    @Test
    fun `arquivo ilegivel e tratado como keyset corrompido`() {
        var descartes = 0

        val aberto = abrindoOuDescartando(
            abrir = { if (descartes == 0) throw IOException("arquivo truncado") else "prefs" },
            descartar = { descartes++ },
        )

        assertEquals("prefs", aberto)
        assertEquals(1, descartes)
    }

    @Test
    fun `excecao que nao e corrupcao sobe, e nao vira sessao ausente`() {
        // Engolir o desconhecido aqui transformaria defeito de programacao em "sem sessao", e o
        // sintoma seria o professor reautenticando para sempre sem nada dizer por que.
        assertThrows(IllegalStateException::class.java) {
            abrindoOuDescartando<String>(
                abrir = { throw IllegalStateException("contexto nulo") },
                descartar = { },
            )
        }
    }

    // --- Ler: nao tem conserto, entao e sessao invalida ---

    @Test
    fun `leitura normal devolve o valor`() {
        assertEquals("tok-abc", lendoOuSessaoInvalida { "tok-abc" })
    }

    @Test
    fun `valor que nao decifra e sessao invalida`() {
        assertNull(lendoOuSessaoInvalida<String> { throw AEADBadTagException("nao decifra") })
    }

    @Test
    fun `SecurityException tambem e sessao invalida`() {
        // `EncryptedSharedPreferences` embrulha falha de decifragem nela, e ela **nao** desce de
        // `GeneralSecurityException` — sem esta linha o caso mais provavel escaparia do tratamento.
        assertNull(lendoOuSessaoInvalida<String> { throw SecurityException("keyset invalido") })
    }

    @Test
    fun `leitura com excecao desconhecida sobe`() {
        assertThrows(IllegalStateException::class.java) {
            lendoOuSessaoInvalida<String> { throw IllegalStateException("defeito de programacao") }
        }
    }
}
