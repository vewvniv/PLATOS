package com.platos.android.outbox

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.platos.android.api.corpoDoEnvio

/**
 * A linha da fila, como o Room a guarda.
 *
 * **[corpo] e o JSON exato que sobe**, congelado na apuracao. Guardar a nota decomposta em colunas e
 * remontar o corpo no envio abriria a possibilidade de uma mudanca no caminho de serializacao
 * reescrever notas antigas ao envia-las — em silencio, e com a nota continuando plausivel.
 *
 * [captureId] e a chave primaria porque e a identidade da captura dos dois lados: aqui ele impede
 * duas linhas para a mesma passada da folha, e no servidor e ele que faz o reenvio nao gravar nada
 * novo.
 */
@Entity(tableName = "resultado_pendente")
data class ResultadoPendenteEntity(
    @PrimaryKey @ColumnInfo(name = "capture_id") val captureId: String,
    @ColumnInfo(name = "organizacao") val organizacao: String,
    @ColumnInfo(name = "prova") val prova: String,
    @ColumnInfo(name = "apurado_em") val apuradoEm: Long,
    @ColumnInfo(name = "corpo") val corpo: String,
)

@Dao
interface ResultadoPendenteDao {

    /**
     * `REPLACE` porque a chave e a captura: reapurar a mesma captura substitui a linha dela.
     *
     * Nao e o caso da recaptura — aquela e outra passada da folha pela camera, gera outro
     * `captureId`, e vira **outra linha**, que o servidor transformara em revisao nova.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun inserir(linha: ResultadoPendenteEntity)

    @Query(
        "select * from resultado_pendente where organizacao = :organizacao order by apurado_em asc",
    )
    fun daOrganizacao(organizacao: String): List<ResultadoPendenteEntity>

    @Query("select count(*) from resultado_pendente where organizacao = :organizacao")
    fun quantos(organizacao: String): Int

    @Query("delete from resultado_pendente where capture_id = :captureId")
    fun apagar(captureId: String)
}

@Database(entities = [ResultadoPendenteEntity::class], version = 1, exportSchema = false)
abstract class BaseDoOutbox : RoomDatabase() {
    abstract fun pendentes(): ResultadoPendenteDao
}

/**
 * Os resultados pendentes em Room.
 *
 * **Room aqui, e arquivo nas outras tres guardas do aparelho, e a diferenca e a que ADR-0013
 * nomeou.** O roster, o pacote e a visao sao registros lidos inteiros e substituidos inteiros; este
 * e consultado por organizacao, contado, percorrido em ordem e apagado linha a linha depois de cada
 * confirmacao. ADR-0013 §3 adiou Room com gatilho escrito — "a 4b, onde o **outbox** e de fato
 * relacional" —, e e este.
 *
 * **Recebe o `Dao`, e nao um `Context`**, pela mesma razao que `RostersEmArquivo` recebe um
 * diretorio: a fronteira com o Android fica num lugar so, e quem decide o que apagar e o que
 * preservar continua ao alcance de teste.
 */
class ResultadosEmRoom(private val dao: ResultadoPendenteDao) : ResultadosPendentes {

    override fun guardar(resultado: ResultadoPendente) {
        dao.inserir(
            ResultadoPendenteEntity(
                captureId = resultado.captureId,
                organizacao = resultado.organizacao,
                prova = resultado.prova,
                apuradoEm = resultado.apuradoEm,
                corpo = resultado.corpoDoEnvio(),
            ),
        )
    }

    override fun pendentesDa(organizacao: String): List<EnvelopeDeEnvio> =
        dao.daOrganizacao(organizacao).map {
            EnvelopeDeEnvio(
                captureId = it.captureId,
                organizacao = it.organizacao,
                prova = it.prova,
                corpo = it.corpo,
            )
        }

    override fun quantosPendentes(organizacao: String): Int = dao.quantos(organizacao)

    override fun apagarConfirmado(captureId: String) = dao.apagar(captureId)

    companion object {

        /**
         * A base do aparelho.
         *
         * `createFromAsset` e migracao nao existem: a base nasce nesta versao e nao ha dado anterior
         * para migrar — hoje nada e persistido. A primeira migration de verdade vem quando a
         * primeira coluna mudar, e ai ela tem de existir de fato: `fallbackToDestructiveMigration`
         * **nao** entra, porque apagar a base numa atualizacao do aplicativo destruiria correcao que
         * nao subiu, que e exatamente o que esta fatia existe para nao fazer.
         */
        fun abrir(context: Context): BaseDoOutbox =
            Room.databaseBuilder(context, BaseDoOutbox::class.java, "outbox.db").build()
    }
}
