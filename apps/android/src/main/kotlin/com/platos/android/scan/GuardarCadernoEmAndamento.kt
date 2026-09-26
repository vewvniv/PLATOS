package com.platos.android.scan

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch

/**
 * Guarda o caderno em andamento no `onStop`, fora do fio principal (`slice-5b-3-guardar-a-parcial-e-o-caderno`).
 *
 * **Existe como funcao, e fora da `Activity`, pela mesma razao de `gravarEAgendar`.** O Room recusa
 * acesso ao banco no fio principal, e `onStop` roda nele. Com a escrita aqui, ha um ponto que um
 * teste consegue chamar **do fio principal**, com o banco aberto como a producao o abre, e afirmar
 * que ele nao estoura — sem precisar de uma `Activity` real.
 *
 * **`Dispatchers.IO` e `NonCancellable`, e os dois sao requisito, pela mesma razao de
 * `gravarEAgendar`.** `onStop` e seguido, de perto, por `onDestroy` quando o usuario troca de app ou
 * o aparelho desliga a tela para sempre naquela sessao — exatamente o caso que esta mudanca existe
 * para proteger. Cancelar a escrita no meio devolveria ao caderno o mesmo destino que ele tinha
 * antes desta mudanca: perdido.
 */
fun CoroutineScope.guardarCadernoEmAndamento(
    cadernos: CadernosGuardados,
    organizacao: String,
    examId: String,
    caderno: Caderno,
): Job = launch(Dispatchers.IO + NonCancellable) {
    cadernos.guardar(organizacao, examId, caderno)
}
