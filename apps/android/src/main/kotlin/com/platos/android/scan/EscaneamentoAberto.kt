package com.platos.android.scan

/**
 * A marca de processo "o escaneamento esta aberto" (`slice-5c-3-a-nota-no-aparelho`, design D4).
 *
 * **Vale porque o `WorkManager` roda no processo do aplicativo.** Se um dia houver segundo processo, a marca deixa
 * de valer e o requisito "a eliminacao em segundo plano nao elimina o que o escaneamento esta gravando" deve ser
 * reaberto.
 *
 * **Sem trava, e de proposito** (revisao final): a primeira versao segurava um monitor durante a varredura inteira, e
 * `onStart` o esperava na thread principal. Agora a marca e `@Volatile`, [abrir] nao espera ninguem, e a varredura a
 * le **a cada arquivo, na hora de eliminar**. Isso basta: um arquivo orfao so pode ter sido gravado depois de
 * [abrir] (os da sessao anterior estao no caderno que o `onStop` guardou antes de [fechar]), e a listagem da varredura
 * e anterior a ele — um arquivo gravado depois de [abrir] nao esta na listagem, e o que esta e ou ja era orfao ou e
 * poupado pela leitura que vem logo antes da eliminacao.
 */
object EscaneamentoAberto {
    @Volatile
    private var aberto = false

    fun abrir() {
        aberto = true
    }

    fun fechar() {
        aberto = false
    }

    fun estaAberto(): Boolean = aberto
}
