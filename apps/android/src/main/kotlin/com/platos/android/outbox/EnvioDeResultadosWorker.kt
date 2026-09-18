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
import com.platos.android.net.Retorno
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
        if (guardada.credencial() == null) return Result.success(diagnostico("sem-credencial"))

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
        // O ultimo status recusado, so para o diagnostico. Sem isto, "rodou e nao drenou" nao
        // distingue credencial ausente de 401, de 404 e de 502 — e foi exatamente essa
        // indistinguibilidade que travou a conferencia em aparelho.
        var ultimoStatus = 0
        val envio = EnvioDeResultados(pendentes) { envelope ->
            val retorno = api.enviarResultado(envelope.organizacao, envelope.prova, envelope.corpo)
            if (retorno is Retorno.Recusou) ultimoStatus = retorno.status
            retorno
        }

        val resumo = try {
            envio.enviarPendentesDa(organizacao)
        } finally {
            http.close()
        }

        val saida = diagnostico(
            desfecho = if (resumo.confirmados > 0) "enviou" else "nada-confirmado",
            confirmados = resumo.confirmados,
            semRede = resumo.semRede,
            transitorios = resumo.transitorios,
            recusados = resumo.recusados,
            ultimoStatus = ultimoStatus,
        )
        return if (valeTentarDeNovo(resumo)) Result.retry() else Result.success(saida)
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

/**
 * O resumo da passada, gravado no `output` do trabalho.
 *
 * **Existe porque "rodou e devolveu sucesso" nao diz nada.** Na conferencia em aparelho o `WorkSpec`
 * marcava `SUCCEEDED` com a fila intacta, e os dois caminhos que produzem isso — credencial ausente e
 * recusa do servidor — eram indistinguiveis de fora. `output` e lido por `adb` sem depender de
 * logcat, que ja tinha sido limpo quando a pergunta apareceu.
 */
private fun diagnostico(
    desfecho: String,
    confirmados: Int = 0,
    semRede: Int = 0,
    transitorios: Int = 0,
    recusados: Int = 0,
    ultimoStatus: Int = 0,
): Data = Data.Builder()
    .putString("desfecho", desfecho)
    .putInt("confirmados", confirmados)
    .putInt("sem_rede", semRede)
    .putInt("transitorios", transitorios)
    .putInt("recusados", recusados)
    .putInt("ultimo_status", ultimoStatus)
    .build()

/**
 * Se a passada merece outra tentativa agendada pelo proprio `WorkManager`.
 *
 * **Funcao, e nao uma linha dentro de `doWork`.** Dentro do `doWork` esta decisao so e alcancavel por
 * teste instrumentado, e ficaria coberta por inferencia — os quatro casos que importam sao
 * combinacoes de tres numeros, e eles se exercitam na JVM em milissegundos.
 *
 * **`transitorios` entrou; `recusados` nao.** Falha do servidor pode aceitar o mesmo envio depois,
 * sem que nada no aparelho mude — foi o 500 da migration ausente, lido como recusa definitiva, que
 * deixou um pendente parado ate alguem escanear outra folha. Recusa definitiva nao melhora tentando
 * de novo em seguida: ela sobe quando houver trabalho agendado de novo — ao escanear outra folha, ou
 * ao abrir sessao, que e o caminho que `SessaoActivity.escoarPendentes` criou.
 *
 * **Sem teto de tentativas, e isso e deliberado.** O backoff exponencial do `WorkManager` ja limita a
 * frequencia, e um numero escolhido aqui sem evidencia de pressao seria numero sem consumidor. Tentar
 * indefinidamente e o desfecho certo para uma fila cujo conteudo nao existe em nenhum outro lugar: o
 * fato e append-only e o servidor e idempotente por `capture_id`, entao a repeticao nao degrada nada.
 */
internal fun valeTentarDeNovo(resumo: ResumoDoEnvio): Boolean =
    resumo.semRede > 0 || resumo.transitorios > 0
