package com.platos.android.scan

/**
 * A marca de processo "o escaneamento esta aberto" (`slice-5c-3-a-nota-no-aparelho`, design D4).
 *
 * **Vale porque o `WorkManager` roda no processo do aplicativo.** Se um dia houver segundo processo, a marca deixa
 * de valer e o requisito "a eliminacao em segundo plano nao elimina o que o escaneamento esta gravando" deve ser
 * reaberto.
 *
 * [varrer] executa o bloco **dentro da trava**, com o valor da marca naquele instante; [abrir] espera a varredura em
 * curso terminar. Sem a trava, o escaneamento poderia abrir entre a leitura da marca e a eliminacao, e a varredura
 * apagaria a resposta que o analisador acabou de gravar.
 */
object EscaneamentoAberto {
    private val trava = Any()
    private var aberto = false

    fun abrir() = synchronized(trava) { aberto = true }

    fun fechar() = synchronized(trava) { aberto = false }

    fun <T> varrer(bloco: (aberto: Boolean) -> T): T = synchronized(trava) { bloco(aberto) }
}
