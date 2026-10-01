package com.platos.android.scan

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.serialization.json.Json

/**
 * O caderno em andamento, como o Room o guarda (`slice-5b-3-guardar-a-parcial-e-o-caderno`).
 *
 * **Uma linha por organizacao e prova, substituida inteira.** A chave e composta —
 * ([organizacao], [examId]) —, e nao so `examId`: o aparelho e compartilhado entre escolas
 * (`DeviceSession.sair`), e `examId` nao tem contrato de unicidade entre organizacoes — a mesma
 * distincao que `ScanActivity.EXTRA_SHORT_ID` ja registra para o roster ("sao iguais hoje, mas por
 * um contrato implicito que nada prende"). Sem a organizacao, um `examId` que colidisse entre duas
 * escolas no mesmo aparelho misturaria o caderno de uma na sessao da outra.
 *
 * **[corpo] e o JSON exato do `Caderno`**, pela mesma razao de `ResultadoPendenteEntity.corpo`:
 * decompor em colunas e remontar arriscaria uma mudanca de serializacao reescrever, em silencio, um
 * caderno ja guardado.
 */
@Entity(tableName = "caderno_em_andamento", primaryKeys = ["organizacao", "exam_id"])
data class CadernoEntity(
    @ColumnInfo(name = "organizacao") val organizacao: String,
    @ColumnInfo(name = "exam_id") val examId: String,
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

    @Query("select * from caderno_em_andamento where organizacao = :organizacao and exam_id = :examId")
    fun ler(organizacao: String, examId: String): CadernoEntity?

    /** Todos os cadernos do aparelho, **sem filtro de organizacao** (ver [CadernosGuardados.todos]). */
    @Query("select * from caderno_em_andamento")
    fun todos(): List<CadernoEntity>
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
    fun guardar(organizacao: String, examId: String, caderno: Caderno)
    fun ler(organizacao: String, examId: String): Caderno?

    /**
     * Todos os cadernos guardados no aparelho, de qualquer organizacao e prova
     * (`slice-5c-1-a-resposta-fica-no-aparelho`, design, decisao 5). Existe para o prazo das respostas: o
     * aparelho e compartilhado entre escolas, e o prazo de 30 dias nao pertence a sessao corrente.
     * Nenhum chamador deve usa-lo para mostrar caderno de outra organizacao.
     */
    fun todos(): List<Caderno>
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

    override fun guardar(organizacao: String, examId: String, caderno: Caderno) {
        dao.guardar(CadernoEntity(organizacao, examId, Json.encodeToString(Caderno.serializer(), caderno)))
    }

    override fun ler(organizacao: String, examId: String): Caderno? =
        dao.ler(organizacao, examId)?.let { Json.decodeFromString(Caderno.serializer(), it.corpo) }

    override fun todos(): List<Caderno> =
        dao.todos().map { Json.decodeFromString(Caderno.serializer(), it.corpo) }

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
