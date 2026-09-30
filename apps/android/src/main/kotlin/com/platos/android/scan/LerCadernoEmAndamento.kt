package com.platos.android.scan

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async

/**
 * Le o caderno em andamento fora do fio principal (`o-caderno-e-lido-fora-do-fio-principal`).
 *
 * **Existe como funcao, e fora da `Activity`, pela mesma razao de [guardarCadernoEmAndamento].** O
 * Room recusa consulta no fio principal, e `ScanActivity.onCreate` roda nele: a leitura que ele
 * fazia direto (`cadernos.ler(...)`) derrubava o escaneamento ao abrir, com caderno guardado ou
 * nao, porque a consulta roda sempre (medido em 2026-09-30, `AbrirEscaneamentoComCadernoInstrumentedTest`).
 * Com a leitura aqui, ha um ponto que um teste consegue chamar **do fio principal**, com o banco
 * aberto como a producao o abre, e afirmar que ele nao estoura.
 *
 * **`Dispatchers.IO`, e sem `NonCancellable`** — a diferenca da escrita e deliberada: cancelar uma
 * escrita no meio perderia o caderno, e cancelar uma leitura nao perde nada. `Activity` destruida
 * antes do fim da leitura cancela o escopo, e quem espera o resultado recebe a
 * `CancellationException`, que e o que impede a tela de ser montada sobre uma `Activity` morta.
 *
 * Excecao da leitura (JSON corrompido, por exemplo) **nao** e tratada aqui: propaga para quem espera
 * o resultado, como se propagava antes desta funcao existir. Degradar caderno ilegivel e decisao que
 * toca a spec, e nao foi tomada.
 */
fun CoroutineScope.lerCadernoEmAndamento(
    cadernos: CadernosGuardados,
    organizacao: String,
    examId: String,
): Deferred<Caderno?> = async(Dispatchers.IO) {
    cadernos.ler(organizacao, examId)
}
