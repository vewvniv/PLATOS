package com.platos.android.outbox

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.platos.android.api.corpoDoEnvio
import com.platos.domain.capture.QuestionAnswer
import com.platos.domain.scoring.DiscursivaCorrigida
import com.platos.domain.scoring.NotaDoProfessor
import com.platos.domain.scoring.Pontos
import com.platos.domain.scoring.QuestionOutcome
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** A nota do professor grava no outbox, sobrevive a reabrir o banco, e sai so quando confirmada. */
@RunWith(AndroidJUnit4::class)
class NotaPendenteInstrumentedTest {

    private val contexto = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun nota() = NotaPendente(
        captureId = "cap-nota-1",
        completaCaptura = "cap-parcial-1",
        organizacao = "org-1",
        prova = "prova-r",
        studentToken = "aluno-1",
        apuradoEm = 1_789_646_400_000L,
        nota = NotaDoProfessor(
            packageHash = "a".repeat(64),
            variantId = "v1",
            objectivePoints = 1,
            objectiveMaxScore = 1,
            maxScore = 4,
            pending = emptyList(),
            outcomes = listOf(QuestionOutcome("q01", QuestionAnswer.Marcada("q01", "A"), worth = 1, earned = 1)),
            essays = listOf(DiscursivaCorrigida("d1", worth = 3, earned = Pontos.parse("1.75"))),
        ),
    )

    @Before
    fun limpo() {
        ResultadosEmRoom.reiniciarParaTeste()
        contexto.deleteDatabase("outbox.db")
    }

    @After
    fun limpa() {
        ResultadosEmRoom.reiniciarParaTeste()
        contexto.deleteDatabase("outbox.db")
    }

    @Test
    fun a_nota_sobrevive_a_reabrir_o_banco_e_sai_so_na_confirmacao() {
        ResultadosEmRoom(ResultadosEmRoom.abrir(contexto).pendentes()).guardarNota(nota())
        ResultadosEmRoom.reiniciarParaTeste()

        val guarda = ResultadosEmRoom(ResultadosEmRoom.abrir(contexto).pendentes())
        val envelope = guarda.pendentesDa("org-1").single()

        assertEquals(RotaDoEnvio.NOTA, envelope.rota)
        assertEquals("cap-parcial-1", envelope.completaCaptura)
        assertEquals(nota().corpoDoEnvio(), envelope.corpo)
        assertEquals(1, guarda.quantosPendentes("org-1"))

        guarda.apagarConfirmado("cap-nota-1")

        assertEquals(0, guarda.quantosPendentes("org-1"))
    }
}
