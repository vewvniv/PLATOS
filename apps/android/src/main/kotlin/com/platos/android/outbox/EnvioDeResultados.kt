package com.platos.android.outbox

import com.platos.android.net.Retorno

/**
 * O que aconteceu com a fila numa passada.
 *
 * Os tres numeros existem separados porque significam coisas diferentes para quem decide reagendar:
 * [semRede] pede outra tentativa quando houver rede, [recusados] nao pede tentativa nenhuma tao
 * cedo, e [confirmados] e o unico que reduz a fila.
 */
data class ResumoDoEnvio(
    val confirmados: Int,
    val semRede: Int,
    val recusados: Int,
) {
    val pendentesRestantes: Int get() = semRede + recusados
}

/**
 * Empurra os pendentes de uma organizacao, e apaga **so** o que o servidor confirmou.
 *
 * **Sem `Context`, sem `WorkManager` e sem cliente HTTP.** Recebe a guarda e uma funcao de envio, e
 * e isso que poe a decisao inteira ao alcance de um teste de JVM — a mesma fronteira que
 * `ScanSession` e `IdAlunoDaFolha` ja usam. O que sobra para o aparelho e agendamento, que e do
 * `WorkManager` e nao desta classe.
 *
 * **O expurgo acontece depois da confirmacao, e so por causa dela.** Falha de rede, tempo esgotado e
 * recusa do servidor deixam a linha onde esta. A ordem no codigo nao e detalhe: apagar antes de
 * enviar, ou apagar num `finally`, destruiria a unica copia de uma correcao que nao subiu.
 *
 * **Um recusado nao bloqueia a fila.** O laco continua, e o proximo e tentado. Parar no primeiro
 * erro faria uma folha que o servidor recusa por contrato — um corpo que uma versao antiga do
 * aplicativo produz errado, por exemplo — prender atras de si toda a turma escaneada depois dela.
 */
class EnvioDeResultados(
    private val pendentes: ResultadosPendentes,
    private val enviar: suspend (EnvelopeDeEnvio) -> Retorno<Unit>,
) {

    suspend fun enviarPendentesDa(organizacao: String): ResumoDoEnvio {
        var confirmados = 0
        var semRede = 0
        var recusados = 0

        for (envelope in pendentes.pendentesDa(organizacao)) {
            when (enviar(envelope)) {
                is Retorno.Respondeu -> {
                    // A unica linha do aplicativo que apaga um pendente, e ela esta atras de uma
                    // confirmacao do servidor.
                    pendentes.apagarConfirmado(envelope.captureId)
                    confirmados++
                }

                // Sem rede nao e informacao sobre o resultado: e informacao sobre o caminho. A
                // linha fica, e a proxima passada tenta de novo.
                is Retorno.SemRede -> semRede++

                // O servidor respondeu, e disse nao. Tambem fica: a recusa pode ser 404 de prova que
                // este usuario nao alcanca mais — vinculo revogado —, e outro membro da organizacao
                // consegue enviar o mesmo pendente depois.
                is Retorno.Recusou -> recusados++
            }
        }

        return ResumoDoEnvio(confirmados = confirmados, semRede = semRede, recusados = recusados)
    }
}
