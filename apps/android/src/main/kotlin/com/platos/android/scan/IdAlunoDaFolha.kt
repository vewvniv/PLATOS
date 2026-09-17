package com.platos.android.scan

import com.platos.android.roster.RosterDaProva
import com.platos.android.session.MarcaDeLeitura
import com.platos.android.session.Procedencia
import com.platos.android.session.ROTULO_LISTA_BAIXADA
import com.platos.android.session.marcaDeLeitura
import java.time.ZoneId

/**
 * De **qual aluno** e a folha lida, pronta para a tela desenhar.
 *
 * **Nao confundir com a identidade da folha de `IdentidadeDaFolhaTest`**, que e a camada (c) de
 * ADR-0013: aquela decide de qual **prova** a folha e, conferindo o `short_id` do QR contra o pacote
 * carregado, e recusa a folha quando divergem. Esta pressupoe aquela ja resolvida e responde outra
 * pergunta — de quem e a folha que ja se sabe ser desta prova.
 *
 * **Resolvida na hora de apresentar, e nunca gravada no resultado.** [ScanState.Scored] carrega a
 * leitura e a nota, e nao carrega nome: gravar o nome ali criaria uma **segunda** copia de dado
 * pessoal no aparelho, com vida propria e fora da lista que "Sair apaga" enumera — e o apagamento
 * passaria a depender de alguem lembrar de enumera-la. Uma copia, um lugar, uma regra de apagamento.
 *
 * **Consequencia aceita:** corrigir o nome no servidor e puxar o roster de novo muda o nome exibido
 * em resultados ja apurados. E o comportamento certo — o roster e mutavel por construcao (ADR-0002),
 * e a nota nao muda.
 */
sealed interface IdAlunoDaFolha {

    /** A marca de dado cacheado, que a tela desenha ao lado do que ela identifica. */
    val marca: MarcaDeLeitura?

    /** O token esta no roster guardado, e o aluno tem nome. */
    data class Nomeada(val nome: String, override val marca: MarcaDeLeitura?) : IdAlunoDaFolha

    /**
     * O token nao tem linha no roster guardado desta prova.
     *
     * **Nao e falha de leitura, e a distincao importa.** A nota foi apurada e e valida; o que falta e
     * saber de quem e a folha. `exam-package` separa os dois casos ao entregar roster vazio em vez de
     * negar a prova, e e a folha avulsa do aluno fora da lista que depende disso.
     */
    data class ForaDoRoster(val token: String, override val marca: MarcaDeLeitura?) : IdAlunoDaFolha
}

/**
 * Resolve o token lido contra o roster guardado.
 *
 * **A marca vem sempre**, e nao so quando o aparelho esta sem rede. O que a tela apresenta aqui saiu
 * do disco por definicao — o escaneamento acontece depois do gate, sobre o que foi puxado —, e o
 * requisito de `device-session` manda marcar dado guardado e **dizer de quando ele e**. Um roster
 * puxado ha dez segundos carrega a marca igual, e a idade e que diz isso a quem le; marcar so
 * quando a rede cai faria a ausencia da marca significar duas coisas diferentes.
 *
 * **O rotulo e [ROTULO_LISTA_BAIXADA], e nao o da visao.** Como a marca vem sempre, um rotulo que
 * dissesse "sem conexao" seria falso em toda leitura feita com o aparelho on-line — que sao quase
 * todas.
 *
 * [roster] nulo e tratado como token fora do roster, e nao como erro. O gate garante que ha roster
 * puxado antes de a camera abrir, mas entre o gate e a leitura o vinculo pode cair e o apagamento
 * levar o roster junto; apresentar o token e a resposta honesta, e inventar um nome ou estourar nao
 * sao.
 *
 * Funcao, e nao metodo de [ScanState]: ela nao tem estado, e e isso que a poe ao alcance de um
 * cenario de JVM — o que a tela decide, nenhum teste desta base alcanca.
 */
fun idAlunoDaFolha(
    token: String,
    roster: RosterDaProva?,
    zona: ZoneId,
): IdAlunoDaFolha {
    val marca = roster?.let {
        marcaDeLeitura(Procedencia.Cacheada(it.puxadoEm), zona, ROTULO_LISTA_BAIXADA)
    }
    val aluno = roster?.alunos?.firstOrNull { it.token == token }

    return when (aluno) {
        null -> IdAlunoDaFolha.ForaDoRoster(token, marca)
        else -> IdAlunoDaFolha.Nomeada(aluno.nome, marca)
    }
}
