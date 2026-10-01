package com.platos.android.scan

import android.content.Context
import android.util.Log
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A etiqueta do log da varredura: o numero de eliminacoes que falharam so aparece aqui (lacuna conhecida, P8). */
internal const val ETIQUETA_DA_VARREDURA = "RespostasVarredura"

/** Registra o que uma varredura fez, no log do aparelho. */
internal fun registrarVarredura(varredura: Varredura) {
    Log.i(
        ETIQUETA_DA_VARREDURA,
        "eliminadas=${varredura.eliminados} naoEliminadas=${varredura.naoEliminados} " +
            "semLeituraDosCadernos=${varredura.semLeituraDosCadernos}",
    )
}

/**
 * A varredura do prazo das respostas, **fora do fio principal**, para a porta de entrada do aplicativo
 * (`SessaoActivity`; `slice-5c-1-a-resposta-fica-no-aparelho`, design, decisao 5). Abre o Room e le o disco, e
 * por isso roda em `Dispatchers.IO`. **Nunca lanca**: qualquer falha ao abrir o armazenamento vira uma
 * varredura sem leitura dos cadernos, e o aplicativo abre do mesmo jeito.
 *
 * O escaneamento nao usa esta funcao: a varredura dele esta **dentro** de [retomarCadernoEmAndamento], antes
 * da leitura do caderno.
 */
suspend fun varrerRespostasDoAparelho(context: Context): Varredura = withContext(Dispatchers.IO) {
    val varredura = try {
        varrerRespostas(
            respostas = RespostasEmArquivo(RespostasEmArquivo.diretorioDe(context.filesDir)),
            cadernos = CadernosEmRoom(CadernosEmRoom.abrir(context).cadernos()),
            agora = System.currentTimeMillis(),
        )
    } catch (e: Exception) {
        Varredura(eliminados = 0, naoEliminados = 0, semLeituraDosCadernos = true)
    }
    registrarVarredura(varredura)
    varredura
}
