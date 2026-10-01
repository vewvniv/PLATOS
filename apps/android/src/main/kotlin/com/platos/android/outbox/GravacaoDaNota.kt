package com.platos.android.outbox

import com.platos.android.scan.Caderno
import com.platos.android.scan.CadernosGuardados

/**
 * A gravacao composta da nota do professor (`slice-5c-3-a-nota-no-aparelho`): a nota na fila, o caderno corrigido no
 * Room e o agendamento do envio. **Bloqueante**: quem chama a poe em `Dispatchers.IO` e sem cancelamento.
 *
 * **So a nota decide o retorno.** Sao dois bancos Room (`outbox.db` e `caderno.db`) sem transacao entre eles. Se a nota
 * esta duravel, o professor **nao** pode ser mandado "tentar de novo": a nova tentativa cunharia outra chave e deixaria
 * duas notas na fila (revisao final). Falhar o caderno e inofensivo — o gancho de confirmacao acha o caderno pela
 * captura, e `onStop` o guarda corrigido — e falhar o agendamento tambem — o envio sobe na proxima sessao. Os dois
 * erros sao engolidos de proposito, **depois** de a nota estar duravel. Falhar a nota devolve `false` e nao toca em mais
 * nada: o caderno nao pode ser corrigido sem a nota na fila.
 *
 * A ordem (nota, caderno, agendamento) e a que torna a queda do processo entre os passos inofensiva (design, desvio 2).
 */
fun gravarNota(
    pendentes: ResultadosPendentes,
    cadernos: CadernosGuardados,
    nota: NotaPendente,
    cadernoCorrigido: Caderno,
    examId: String,
    agendarEnvio: () -> Unit,
): Boolean {
    try {
        pendentes.guardarNota(nota)
    } catch (e: Exception) {
        return false
    }
    try {
        cadernos.guardar(nota.organizacao, examId, cadernoCorrigido)
    } catch (e: Exception) {
        // Inofensivo: ver a KDoc.
    }
    try {
        agendarEnvio()
    } catch (e: Exception) {
        // Inofensivo: ver a KDoc.
    }
    return true
}
