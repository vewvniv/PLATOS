package com.platos.android.outbox

import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A migration 1 -> 2 nao perde pendente (`slice-5c-3-a-nota-no-aparelho`): a linha antiga vira `resultado`, e a coluna
 * nova nasce nula. O banco v1 e criado **a mao**, porque o schema nao e exportado e nao ha `MigrationTestHelper`.
 */
@RunWith(AndroidJUnit4::class)
class MigracaoDoOutboxInstrumentedTest {

    private val contexto = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before
    fun banco1() {
        ResultadosEmRoom.reiniciarParaTeste()
        contexto.deleteDatabase("outbox.db")
        SQLiteDatabase.openOrCreateDatabase(contexto.getDatabasePath("outbox.db"), null).use { db ->
            db.execSQL(
                "CREATE TABLE resultado_pendente (capture_id TEXT NOT NULL, organizacao TEXT NOT NULL, " +
                    "prova TEXT NOT NULL, apurado_em INTEGER NOT NULL, corpo TEXT NOT NULL, PRIMARY KEY(capture_id))",
            )
            db.execSQL("INSERT INTO resultado_pendente VALUES ('cap-antiga','org-1','prova-r',1,'{\"antigo\":true}')")
            db.version = 1
        }
    }

    @After
    fun limpa() {
        ResultadosEmRoom.reiniciarParaTeste()
        contexto.deleteDatabase("outbox.db")
    }

    @Test
    fun a_linha_antiga_sobrevive_e_vira_resultado() {
        val guarda = ResultadosEmRoom(ResultadosEmRoom.abrir(contexto).pendentes())

        val pendentes = guarda.pendentesDa("org-1")

        assertEquals(1, pendentes.size)
        assertEquals("cap-antiga", pendentes.single().captureId)
        assertEquals(RotaDoEnvio.RESULTADO, pendentes.single().rota)
        assertEquals("{\"antigo\":true}", pendentes.single().corpo)
        assertEquals(null, pendentes.single().completaCaptura)
    }
}
