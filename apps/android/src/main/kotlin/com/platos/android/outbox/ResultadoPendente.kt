package com.platos.android.outbox

import com.platos.domain.scoring.ObjectiveScore

/**
 * Uma correcao apurada que ainda nao subiu.
 *
 * **O resultado duravel e a linha da fila sao a mesma coisa**, e isso e decisao registrada no
 * `design.md` desta fatia, nao economia. A classe H compreende "observacoes pendentes de
 * sincronizacao" e manda elimina-las "apos a sincronizacao bem-sucedida"; um historico local
 * separado dos pendentes seria uma **segunda** copia de dado no aparelho, fora do que a classe H
 * descreve, sem consumidor no fluxo e com regra de apagamento a inventar.
 *
 * *Consequencia aceita:* depois de sincronizado, o resultado nao e mais consultavel no aparelho. A
 * nota mora no servidor, e a tela mostra a folha recem-escaneada durante a sessao.
 *
 * **[captureId] nasce aqui, uma vez por captura, e nunca e recalculado.** E ele que separa "e o
 * mesmo resultado" de "e a folha de novo": reenvio do mesmo pendente carrega o mesmo valor e o
 * servidor nao grava nada novo; uma segunda passada da folha pela camera e outra captura, outro
 * valor, e vira revisao nova. Derivar o identificador do conteudo faria uma recaptura que desse
 * exatamente a mesma nota ser confundida com reenvio — e ela nao e.
 *
 * **[studentToken] e nulo na folha avulsa**, e nunca string vazia: a nota do aluno fora da lista e
 * valida, e o que falta e a atribuicao (§7).
 *
 * **Nao ha campo de nome, turma ou matricula, e a ausencia e o requisito.** O nome e resolvido para
 * a tela a partir do roster e nunca gravado no resultado (`IdAlunoDaFolha`); grava-lo aqui criaria
 * uma segunda copia de dado pessoal no aparelho, com vida propria e fora da lista que o apagamento
 * enumera. Uma copia, um lugar, uma regra de apagamento.
 */
data class ResultadoPendente(
    val captureId: String,
    val organizacao: String,
    /** O `short_id` da prova, que e o que o QR carrega e o que a rota recebe. */
    val prova: String,
    val studentToken: String?,
    /** Quando o aparelho apurou, em milissegundos de epoch. Nao e quando o servidor recebeu. */
    val apuradoEm: Long,
    val nota: ObjectiveScore,
)

/**
 * Um pendente como ele sai da fila, pronto para subir.
 *
 * **O que esta guardado e o corpo que sobe**, e nao uma estrutura que precise ser traduzida na hora
 * do envio. Traduzir na saida abriria a possibilidade de o corpo enviado divergir do que foi
 * apurado — um caminho de serializacao que mudasse depois reescreveria notas antigas ao envia-las,
 * em silencio. Aqui o corpo e congelado no momento da apuracao, junto com a nota que o produziu.
 *
 * [organizacao] e [prova] ficam fora do corpo porque sao o **endereco**: eles montam o caminho da
 * rota, e o servidor os recebe pela URL.
 */
data class EnvelopeDeEnvio(
    val captureId: String,
    val organizacao: String,
    val prova: String,
    /** O JSON exato do corpo, como ele sera enviado. */
    val corpo: String,
)

/**
 * O que o aparelho guarda de resultado pendente.
 *
 * **Verbos nomeados um a um**, como `RostersGuardados`, `PacotesGuardados` e `VisoesGuardadas`, e
 * pela mesma razao registrada nos tres: com cada operacao nomeada, um teste de JVM afirma que
 * **sair nao leva o pendente junto** e que **a revogacao tambem nao leva**. Escondido dentro de uma
 * limpeza generica do adaptador, nenhum teste desta camada alcancaria isso — e aqui o que escaparia
 * e correcao que nao existe em nenhum outro lugar.
 *
 * **Nao ha `apagarDaOrganizacao`, e a ausencia e o requisito.** As outras tres guardas tem esse
 * verbo porque sair e a revogacao as apagam. O pendente **nao** e apagado por nenhum evento local:
 * so [apagarConfirmado], e so depois de o servidor confirmar. Um verbo de apagamento em massa aqui
 * seria a ferramenta pronta para alguem chamar de dentro de `sair` sem perceber o que destruiu.
 */
interface ResultadosPendentes {

    /** Guarda uma correcao recem-apurada. Substitui a de mesmo [ResultadoPendente.captureId]. */
    fun guardar(resultado: ResultadoPendente)

    /** Os pendentes de uma organizacao, do mais antigo para o mais novo. */
    fun pendentesDa(organizacao: String): List<EnvelopeDeEnvio>

    /** Quantos pendentes a organizacao tem. E o que a tela de saida informa. */
    fun quantosPendentes(organizacao: String): Int

    /**
     * Apaga um pendente **depois** de o servidor confirmar a gravacao dele.
     *
     * O nome diz a condicao de proposito. `apagar(captureId)` seria chamavel de qualquer lugar, e o
     * lugar errado e o tratamento de erro — apagar o que falhou e o defeito que esta fatia existe
     * para nao ter.
     */
    fun apagarConfirmado(captureId: String)
}
