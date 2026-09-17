package com.platos.android.outbox

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.platos.android.BuildConfig
import com.platos.android.api.ApiPlatos
import com.platos.android.net.clienteHttp
import com.platos.android.session.SessaoGuardadaAndroid

/**
 * Empurra a fila quando ha rede, e sobrevive ao fim do processo do aplicativo.
 *
 * **O `WorkManager` esta aqui pelo que ele garante, e nao por conveniencia de agendamento:** o
 * professor escaneia a turma no ginasio sem sinal e guarda o aparelho no bolso. O envio precisa
 * acontecer quando a rede voltar, com o aplicativo fechado — e nenhuma corrotina amarrada a um
 * `Activity` faz isso.
 *
 * **A decisao de enviar e apagar nao mora aqui.** Ela esta em [EnvioDeResultados], que nao conhece
 * Android e por isso e exercitada na JVM. O que sobra para esta classe e agendamento e fiacao, que
 * e o que so o aparelho decide.
 *
 * **`ExistingWorkPolicy.KEEP`, e nao `REPLACE`.** Escanear vinte folhas seguidas enfileira vinte
 * pedidos; com `REPLACE`, cada um cancelaria o anterior e o envio so comecaria depois da ultima
 * folha. `KEEP` deixa o trabalho ja agendado seguir, e ele varre a fila inteira de qualquer jeito —
 * uma passada pega tudo o que estiver la.
 */
class EnvioDeResultadosWorker(
    context: Context,
    parametros: WorkerParameters,
) : CoroutineWorker(context, parametros) {

    override suspend fun doWork(): Result {
        val organizacao = inputData.getString(ORGANIZACAO) ?: return Result.failure()

        val guardada = SessaoGuardadaAndroid(applicationContext)
        // Sem credencial nao ha o que tentar, e **nao e falha**: e o aparelho depois de sair. O
        // pendente fica onde esta, e sobe quando um membro da organizacao entrar — que e o que a
        // decisao 6 do `design.md` fixa, e o motivo de este `return` nao apagar nada.
        if (guardada.credencial() == null) return Result.success()

        val http = clienteHttp()
        // `aoExpirarSessao` vazio, e nao uma volta a tela de entrada: nao ha tela aqui. Sessao
        // expirada chega como recusa, o pendente fica, e quem trata a expiracao e a `SessaoActivity`
        // na proxima abertura. Levar a navegacao para dentro de um trabalho de fundo faria o
        // aplicativo se reposicionar sozinho no bolso de quem o guardou.
        val api = ApiPlatos(
            http = http,
            urlBase = BuildConfig.API_URL,
            credencial = { guardada.credencial() },
            aoExpirarSessao = {},
        )
        val pendentes = ResultadosEmRoom(ResultadosEmRoom.abrir(applicationContext).pendentes())
        val envio = EnvioDeResultados(pendentes) { envelope ->
            api.enviarResultado(envelope.organizacao, envelope.prova, envelope.corpo)
        }

        val resumo = try {
            envio.enviarPendentesDa(organizacao)
        } finally {
            http.close()
        }

        // `retry` so pelo que a rede explica. Recusa do servidor nao melhora tentando de novo em
        // seguida: ela sobe na proxima vez que houver trabalho agendado, ou quando outro membro da
        // organizacao entrar. Repetir agora seria laco quente contra um servidor que ja disse nao.
        return if (resumo.semRede > 0) Result.retry() else Result.success()
    }

    companion object {
        private const val ORGANIZACAO = "organizacao"
        private const val TRABALHO = "envio-de-resultados"

        fun agendar(context: Context, organizacao: String) {
            val pedido = OneTimeWorkRequestBuilder<EnvioDeResultadosWorker>()
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                )
                .setInputData(Data.Builder().putString(ORGANIZACAO, organizacao).build())
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork("$TRABALHO-$organizacao", ExistingWorkPolicy.KEEP, pedido)
        }
    }
}
