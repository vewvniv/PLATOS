package com.platos.android.scan

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** A marca de processo que segura a varredura em segundo plano enquanto o escaneamento esta aberto. */
class EscaneamentoAbertoTest {

    @AfterEach
    fun fecha() = EscaneamentoAberto.fechar()

    @Test
    fun `fechado por padrao, aberto depois de abrir, fechado depois de fechar`() {
        assertEquals(false, EscaneamentoAberto.varrer { it })
        EscaneamentoAberto.abrir()
        assertEquals(true, EscaneamentoAberto.varrer { it })
        EscaneamentoAberto.fechar()
        assertEquals(false, EscaneamentoAberto.varrer { it })
    }

    @Test
    fun `abrir espera a varredura em curso terminar, e a seguinte ja o ve aberto`() {
        val dentro = CountDownLatch(1)
        val liberar = CountDownLatch(1)
        val vistoPelaVarredura = AtomicReference<Boolean>()
        val varredura = Thread {
            EscaneamentoAberto.varrer { aberto ->
                vistoPelaVarredura.set(aberto)
                dentro.countDown()
                liberar.await()
            }
        }
        varredura.start()
        dentro.await()

        val abriu = Thread { EscaneamentoAberto.abrir() }
        abriu.start()
        Thread.sleep(100)
        assertEquals(
            true,
            abriu.isAlive,
            "abrir tem de esperar a varredura em curso, que decidiu com o escaneamento fechado",
        )

        liberar.countDown()
        varredura.join()
        abriu.join()
        assertEquals(false, vistoPelaVarredura.get())
        assertEquals(true, EscaneamentoAberto.varrer { it })
    }
}
