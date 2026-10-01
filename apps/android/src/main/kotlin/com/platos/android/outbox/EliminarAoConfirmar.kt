package com.platos.android.outbox

import com.platos.android.scan.CadernosGuardados
import com.platos.android.scan.RespostasGuardadas
import com.platos.android.scan.corrigidoSemRespostas

/** O que a eliminacao depois da confirmacao fez: quantos arquivos sairam e quantos falharam. */
data class EliminacaoAoConfirmar(val eliminados: Int, val naoEliminados: Int)

/**
 * Elimina as imagens do caderno que a nota **completa**, depois de o servidor confirmar a nota
 * (`slice-5c-3-a-nota-no-aparelho`, design D3): o gatilho "apos a sincronizacao" da classe H.
 *
 * **Funcao pura sobre as guardas**, sem `Context` nem `WorkManager`: o worker a liga, e a decisao se exercita na JVM.
 *
 * - So a rota `nota` elimina. O resultado e a parcial **nao** (spec `scan-session`: enviar a parcial nao elimina).
 * - O caderno e achado por `(organizacao, prova)` e **so e tocado se `capturaDaParcial` for a que a nota completa**.
 *   Se outro aluno ja o substituiu, as imagens antigas ficaram sem referencia e a varredura as elimina.
 * - A chave e a captura, e **nao** `corrigido`: a queda entre gravar a nota e gravar o caderno deixa a nota pendente
 *   com o caderno ainda nao corrigido, e a confirmacao precisa eliminar mesmo assim. O caderno e regravado como
 *   `corrigidoSemRespostas`.
 * - Arquivo que falha nao interrompe os outros e e contado; como o caderno regravado nao o referencia mais, a varredura
 *   o elimina na proxima passada.
 */
fun eliminarAoConfirmar(
    envelope: EnvelopeDeEnvio,
    cadernos: CadernosGuardados,
    respostas: RespostasGuardadas,
): EliminacaoAoConfirmar {
    val nada = EliminacaoAoConfirmar(0, 0)
    if (envelope.rota != RotaDoEnvio.NOTA) return nada
    val caderno = cadernos.ler(envelope.organizacao, envelope.prova) ?: return nada
    val captura = envelope.completaCaptura ?: return nada
    if (caderno.capturaDaParcial != captura) return nada

    var eliminados = 0
    var naoEliminados = 0
    for (arquivo in caderno.regioes.mapNotNull { it.resposta?.arquivo }) {
        try {
            respostas.eliminar(arquivo)
            eliminados++
        } catch (e: Exception) {
            naoEliminados++
        }
    }
    cadernos.guardar(envelope.organizacao, envelope.prova, caderno.corrigidoSemRespostas())
    return EliminacaoAoConfirmar(eliminados, naoEliminados)
}
