package com.platos.android.outbox

import com.platos.android.net.Retorno

/**
 * O que aconteceu com a fila numa passada.
 *
 * Os quatro numeros existem separados porque significam coisas diferentes para quem decide
 * reagendar: [semRede] pede outra tentativa quando houver rede, [transitorios] pede outra tentativa
 * porque o servidor falhou e pode aceitar o mesmo envio depois, [recusados] nao pede tentativa
 * nenhuma tao cedo, e [confirmados] e o unico que reduz a fila.
 *
 * **[transitorios] e separado de [semRede], e nao somado a ele.** Os dois pedem retentativa, mas nao
 * pela mesma razao — um e o caminho, o outro e o destino —, e colapsa-los devolveria o diagnostico ao
 * estado em que "rodou e nao drenou" nao distinguia credencial ausente de 500. Foi essa
 * indistinguibilidade que travou a conferencia em aparelho da fatia anterior.
 */
data class ResumoDoEnvio(
    val confirmados: Int,
    val semRede: Int,
    val transitorios: Int,
    val recusados: Int,
) {
    val pendentesRestantes: Int get() = semRede + transitorios + recusados
}

/**
 * A faixa de status que o servidor atribui **a si mesmo**.
 *
 * 5xx e o servidor dizendo que falhou; 4xx e o servidor decidindo sobre o pedido. A diferenca importa
 * porque so a primeira pode aceitar **o mesmo envio** depois, sem que nada no aparelho mude — foi o
 * que aconteceu com o 500 da migration ausente, que ficou lido como recusa definitiva.
 *
 * **408 e 429 nao entram**, apesar de a semantica HTTP os descrever como repetiveis: nenhum dos dois
 * foi observado nesta base, a rota nao tem limitador de taxa nem tempo limite proprio, e faixa
 * escolhida sem caso que a pague e numero sem consumidor. Entram quando houver o primeiro.
 */
private fun Retorno.Recusou.eTransitoria(): Boolean = status >= 500

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
        var transitorios = 0
        var recusados = 0

        for (envelope in pendentes.pendentesDa(organizacao)) {
            when (val retorno = enviar(envelope)) {
                is Retorno.Respondeu -> {
                    // A unica linha do aplicativo que apaga um pendente, e ela esta atras de uma
                    // confirmacao do servidor.
                    pendentes.apagarConfirmado(envelope.captureId)
                    confirmados++
                }

                // Sem rede nao e informacao sobre o resultado: e informacao sobre o caminho. A
                // linha fica, e a proxima passada tenta de novo.
                is Retorno.SemRede -> semRede++

                // O servidor respondeu, e nao foi sucesso. **O pendente fica nos dois casos**, e a
                // classificacao decide so quando se tenta de novo — nunca o que acontece com a fila.
                //
                // A recusa definitiva pode ser 404 de prova que este usuario nao alcanca mais —
                // vinculo revogado —, e outro membro da organizacao consegue enviar o mesmo pendente
                // depois. Repetir contra ela seria laco quente contra um servidor que ja disse nao.
                is Retorno.Recusou -> if (retorno.eTransitoria()) transitorios++ else recusados++
            }
        }

        return ResumoDoEnvio(
            confirmados = confirmados,
            semRede = semRede,
            transitorios = transitorios,
            recusados = recusados,
        )
    }
}
