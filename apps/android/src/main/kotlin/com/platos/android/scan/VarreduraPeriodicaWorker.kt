package com.platos.android.scan

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * O teto de 30 dias das respostas em segundo plano (`slice-5c-3-a-nota-no-aparelho`; paga a linha `5c` do §16):
 * aparelho que guarda a resposta e nunca mais abre o aplicativo tambem expurga. Roda ao menos uma vez por dia, e
 * so lida com o que a varredura ja lida; a trava e [EscaneamentoAberto].
 */
class VarreduraPeriodicaWorker(context: Context, parametros: WorkerParameters) : CoroutineWorker(context, parametros) {

    override suspend fun doWork(): Result {
        withContext(Dispatchers.IO) {
            EscaneamentoAberto.varrer { aberto -> varrerAgora(applicationContext, escaneamentoAberto = aberto) }
        }
        return Result.success()
    }

    companion object {
        private const val TRABALHO = "varredura-periodica-das-respostas"

        /** Idempotente: `KEEP` deixa o agendamento que ja existe seguir. */
        fun agendar(context: Context) {
            val pedido = PeriodicWorkRequestBuilder<VarreduraPeriodicaWorker>(24, TimeUnit.HOURS).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(TRABALHO, ExistingPeriodicWorkPolicy.KEEP, pedido)
        }
    }
}
