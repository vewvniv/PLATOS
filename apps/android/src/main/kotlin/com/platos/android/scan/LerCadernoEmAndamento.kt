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

/**
 * Retoma o caderno em andamento: **varre, le e normaliza, nessa ordem**
 * (`slice-5c-1-a-resposta-fica-no-aparelho`, design, decisao 5). E a unica via de leitura que a
 * `ScanActivity` usa, e **compoe** [lerCadernoEmAndamento]: a consulta do Room continua fora do fio
 * principal, no mesmo `Dispatchers.IO`.
 *
 * **A ordem e o requisito.** Varrer antes de ler elimina a resposta vencida e o orfao antes de o caderno ser
 * lido, e a normalizacao depois da leitura devolve a regiao cuja resposta nao existe mais como nao vista.
 * Lido antes da varredura, o caderno ainda veria o arquivo vencido, e a regiao voltaria capturada com o
 * arquivo eliminado logo em seguida. Varrer, ler e normalizar no mesmo bloco garante a ordem sem o `onCreate`
 * tocar em Room. A varredura nunca lanca ([varrerRespostas]), entao nao impede o escaneamento de abrir.
 */
fun CoroutineScope.retomarCadernoEmAndamento(
    cadernos: CadernosGuardados,
    respostas: RespostasGuardadas,
    organizacao: String,
    examId: String,
    relogio: () -> Long = System::currentTimeMillis,
    aoVarrer: (Varredura) -> Unit = {},
): Deferred<Caderno?> = async(Dispatchers.IO) {
    aoVarrer(varrerRespostas(respostas, cadernos, relogio()))
    lerCadernoEmAndamento(cadernos, organizacao, examId).await()?.normalizado(respostas::existe)
}
