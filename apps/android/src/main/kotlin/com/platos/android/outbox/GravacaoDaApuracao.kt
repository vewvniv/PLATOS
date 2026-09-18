package com.platos.android.outbox

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch

/**
 * Grava a correcao apurada e, **depois de ela estar em disco**, agenda o envio.
 *
 * **Existe como funcao, e fora da `Activity`, por causa do defeito que ela conserta.** A primeira
 * versao gravava direto no fio principal, de dentro do laco da camera, e o Room recusa isso por
 * padrao: `IllegalStateException: Cannot access database on the main thread`. O aplicativo morria ao
 * escanear uma folha valida, em aparelho real, e **nenhum teste pegou** — o teste instrumentado
 * abria o banco com `allowMainThreadQueries`, afrouxando exatamente a trava que a producao impoe.
 * Com a gravacao aqui, ha um ponto que um teste consegue chamar **do fio principal**, com o banco
 * aberto como a producao o abre, e afirmar que ele nao estoura.
 *
 * *A KDoc anterior afirmava que gravar no fio principal era deliberado — "e uma linha numa tabela
 * local, e o custo dela e menor que o do quadro que acabou de ser analisado". A frase raciocinava
 * sobre uma propriedade do Room que nunca foi medida, e fica dita em vez de apagada (P7).*
 *
 * **`Dispatchers.IO` e `NonCancellable`, e os dois sao requisito.** O primeiro tira a escrita do fio
 * principal. O segundo existe porque quem escaneia baixa o aparelho logo depois: se a `Activity`
 * morrer no meio, o cancelamento levaria junto a unica copia de uma correcao ja feita — e este
 * caminho inteiro existe para que isso nao aconteca.
 *
 * **O agendamento vem depois da gravacao, e dentro da mesma corrotina.** A ordem e a que a spec
 * exige: o resultado e duravel **antes** de qualquer tentativa de envio. Agendar em paralelo abriria
 * a chance de o trabalho rodar e nao achar a linha, e concluir que a fila esta vazia.
 *
 * Devolve o [Job] para que um teste consiga esperar a gravacao terminar. Nenhum chamador de producao
 * o usa: a `Activity` dispara e segue desenhando.
 */
fun CoroutineScope.gravarEAgendar(
    pendentes: ResultadosPendentes,
    resultado: ResultadoPendente,
    agendarEnvio: () -> Unit,
): Job = launch(Dispatchers.IO + NonCancellable) {
    pendentes.guardar(resultado)
    agendarEnvio()
}
