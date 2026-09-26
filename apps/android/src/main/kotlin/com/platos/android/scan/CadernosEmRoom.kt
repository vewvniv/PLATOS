package com.platos.android.scan

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.serialization.json.Json

/**
 * O caderno em andamento, como o Room o guarda (`slice-5b-3-guardar-a-parcial-e-o-caderno`).
 *
 * **Uma linha por prova, substituida inteira.** [examId] e a chave porque o caderno e do aparelho e
 * da prova, e nao da organizacao nem do aluno: `ScanSession.caderno` e uma variavel so (decisao 4 da
 * `5b-2`), e guardar espelha essa forma.
 *
 * **[corpo] e o JSON exato do `Caderno`**, pela mesma razao de `ResultadoPendenteEntity.corpo`:
 * decompor em colunas e remontar arriscaria uma mudanca de serializacao reescrever, em silencio, um
 * caderno ja guardado.
 */
@Entity(tableName = "caderno_em_andamento")
data class CadernoEntity(
    @PrimaryKey @ColumnInfo(name = "exam_id") val examId: String,
    @ColumnInfo(name = "corpo") val corpo: String,
)

@Dao
interface CadernoDao {

    /**
     * `Upsert`, e nao `Insert` com `REPLACE`: o caderno da mesma prova e substituido inteiro a cada
     * chamada — outro aluno, ou o mesmo com mais regioes —, do mesmo jeito que a variavel em memoria
     * ja faz (design, decisao 2).
     */
    @Upsert
    fun guardar(linha: CadernoEntity)

    @Query("select * from caderno_em_andamento where exam_id = :examId")
    fun ler(examId: String): CadernoEntity?
}

@Database(entities = [CadernoEntity::class], version = 1, exportSchema = false)
abstract class BaseDoCaderno : RoomDatabase() {
    abstract fun cadernos(): CadernoDao
}

/**
 * O contrato de guardar e ler o caderno em andamento, independente de Room.
 *
 * Existe para que `ScanSession` (Kotlin puro, sem Android) e os testes dela dependam disto, e nao de
 * Room — a mesma fronteira que `ResultadosPendentes` ja desenha para o outbox.
 */
interface CadernosGuardados {
    fun guardar(examId: String, caderno: Caderno)
    fun ler(examId: String): Caderno?
}

/**
 * O caderno em andamento em Room.
 *
 * **Base propria (`caderno.db`), e nao o `outbox.db`.** O outbox e fila — percorrida por
 * organizacao, em ordem, apagada linha a linha apos confirmacao. O caderno e visao — um registro so,
 * lido inteiro e substituido inteiro. Compartilhar a base misturaria dois ciclos de vida que nao tem
 * nada em comum alem de serem Room (design, decisao 2).
 *
 * **Recebe o `Dao`, e nao um `Context`**, pela mesma razao que `ResultadosEmRoom` recebe o dela: a
 * fronteira com o Android fica num lugar so.
 */
class CadernosEmRoom(private val dao: CadernoDao) : CadernosGuardados {

    override fun guardar(examId: String, caderno: Caderno) {
        dao.guardar(CadernoEntity(examId, Json.encodeToString(Caderno.serializer(), caderno)))
    }

    override fun ler(examId: String): Caderno? =
        dao.ler(examId)?.let { Json.decodeFromString(Caderno.serializer(), it.corpo) }

    companion object {

        /** A instancia unica do processo. `@Volatile` pela mesma razao de `ResultadosEmRoom`. */
        @Volatile
        private var instancia: BaseDoCaderno? = null

        /** A base do aparelho. Sempre a mesma instancia — ver `ResultadosEmRoom.abrir` para o porque. */
        fun abrir(context: Context): BaseDoCaderno =
            instancia ?: synchronized(this) {
                instancia ?: Room.databaseBuilder(
                    context.applicationContext,
                    BaseDoCaderno::class.java,
                    "caderno.db",
                ).build().also { instancia = it }
            }

        /** Porta de teste, pela mesma razao e com a mesma ressalva de `ResultadosEmRoom.reiniciarParaTeste`. */
        fun reiniciarParaTeste() {
            synchronized(this) {
                instancia?.close()
                instancia = null
            }
        }
    }
}
