package com.platos.android.roster

/**
 * Uma linha do roster, como o aparelho a guarda.
 *
 * **Dois campos, e a estreiteza e o requisito.** A entrega do servidor ja traz so estes dois
 * (`exam-package`), e o que nao desce nao precisa ser apagado de onde nunca chegou. Turma, matricula
 * e referencia externa ficam no servidor: acrescentar um campo aqui e acrescentar dado pessoal no
 * aparelho, e isso exige requisito que o justifique, nao conveniencia de tela.
 */
data class AlunoDoRoster(
    val token: String,
    val nome: String,
)

/**
 * O roster de uma prova, com o instante em que foi puxado.
 *
 * **O instante nao e enfeite.** A marca de dado cacheado de `device-session` manda dizer "de quando
 * ele e", e o nome de um aluno pode ter sido corrigido no servidor depois do ultimo pull — o roster
 * e mutavel por construcao (ADR-0002). Sem o instante gravado, a idade teria de vir do
 * `lastModified` do arquivo, que e ancora que muda sozinha quando alguem copia ou restaura o
 * diretorio, e que nao avisa quando envelhece (P3).
 *
 * [puxadoEm] entra por parametro, em milissegundos de epoch, e nao e lido de um relogio aqui dentro,
 * pela mesma razao registrada em [com.platos.android.session.VisaoDaOrganizacao]: relogio dentro da
 * estrutura torna a idade impossivel de afirmar num teste.
 *
 * **Lista vazia e um roster.** Prova publicada sem aluno atribuido e caso legitimo em
 * `exam-package`, e o gate de pre-voo abre com ela. O que o gate barra e a **ausencia** deste objeto
 * — "nao sei quem sao" —, que e estado diferente de "nao ha ninguem".
 */
data class RosterDaProva(
    val alunos: List<AlunoDoRoster>,
    val puxadoEm: Long,
)

/**
 * O que o aparelho guarda de roster, por organizacao e por prova.
 *
 * **Tres verbos, nomeados um a um**, como `VisoesGuardadas` e `PacotesGuardados`, e pela mesma razao
 * registrada nos dois: com o apagamento nomeado, um teste de JVM afirma que **sair leva o roster
 * junto** e que **a revogacao tambem leva**; escondido dentro de uma limpeza generica do adaptador,
 * nenhum teste desta camada o alcanca — e aqui o que escaparia e nome de aluno.
 *
 * O escopo tem **dois niveis**, e nao um: a organizacao, porque o aparelho e compartilhado entre
 * escolas e um roster que atravessasse a troca entregaria a quem entrou depois o que a API
 * recusaria; e a prova, porque cada prova tem o seu. [apagarDaOrganizacao] apaga o nivel inteiro, que
 * e a unidade que sair e a revogacao usam.
 */
interface RostersGuardados {

    /** O roster daquela prova, ou `null` quando o aparelho nunca o puxou. */
    fun ler(organizacao: String, prova: String): RosterDaProva?

    /**
     * Substitui **por inteiro** o roster daquela prova.
     *
     * Substituir, e nao emendar: mesclar preservaria no aparelho a linha de um aluno que saiu do
     * roster no servidor — uma copia de dado pessoal que o servidor ja nao tem, e que nenhuma
     * correcao la alcancaria.
     */
    fun guardar(organizacao: String, prova: String, roster: RosterDaProva)

    /** Apaga todos os rosters de uma organizacao. Os das outras ficam. */
    fun apagarDaOrganizacao(organizacao: String)
}
