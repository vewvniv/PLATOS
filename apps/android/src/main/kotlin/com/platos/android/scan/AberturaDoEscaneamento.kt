package com.platos.android.scan

import com.platos.domain.exam.ExamPackage

/**
 * Por que o escaneamento nao abriu. **Dois, e cada um pede uma coisa diferente.**
 *
 * **Estes nao sao os motivos do gate de pre-voo** (`MotivoDaBarragem`, cinco). Aquele decide **antes**
 * de montar o `Intent`, em `SessaoActivity`, e nao tem como saber que um extra vai faltar — quem
 * monta o `Intent` e ele proprio. Estes decidem **depois**, com o `Intent` na mao, e o `Intent`
 * sobrevive a morte do processo: e essa propriedade que faz o caso existir.
 *
 * Sao enum, e nao texto, pela razao que a fatia 4a-zero registrou: distinguir causa por mensagem faz
 * quem le depender da palavra escolhida, e trocar a palavra quebra a regra sem quebrar teste nenhum.
 */
enum class MotivoDeNaoAbrir {

    /**
     * O pacote sumiu ou deixou de conferir entre o gate e esta tela — disco cheio, arquivo removido,
     * corrupcao em repouso. Quem escolheu a prova volta a escolher, e ela e baixada de novo.
     */
    PACOTE_NAO_CONFERIDO,

    /**
     * O `Intent` nao diz **de qual prova** a sessao e.
     *
     * **Nao e o mesmo que pacote ausente, e colapsar os dois seria dizer a coisa errada:** o pacote
     * pode estar conferido e no lugar. Mandar baixar a prova de novo nao conserta um extra que nao
     * veio, e a acao sugerida ficaria sendo a errada — que e o defeito que separar motivos existe
     * para impedir.
     */
    PROVA_NAO_IDENTIFICADA,
}

/**
 * O que acontece quando o escaneamento e pedido: abre, ou nao abre com motivo.
 *
 * **[Abre] carrega os valores nao-nulaveis**, e e isso que torna o estado silencioso
 * inconstruivel. Antes, `organizacao` e `prova` eram campos nulaveis da `Activity` e `gravar` tinha
 * `?: return` para os dois: folha medida, nota desenhada na tela, nada gravado, nada agendado, sem
 * mensagem. §10 diz "nunca falha em silencio". A correcao nao e tratar o nulo — e nao ter como
 * chegar ate ele.
 */
sealed interface AberturaDoEscaneamento {

    data class Abre(
        val organizacao: String,
        val prova: String,
        val pacote: ExamPackage,
    ) : AberturaDoEscaneamento

    data class NaoAbre(val motivo: MotivoDeNaoAbrir) : AberturaDoEscaneamento
}

/**
 * Decide, **antes de a camera ligar**, se ha tudo o que a sessao precisa.
 *
 * Fora da `Activity` de proposito, e pela mesma razao que `ResultadosEmRoom` recebe o `Dao` em vez de
 * um `Context`: a fronteira com o Android fica num lugar so, e quem **decide** continua ao alcance de
 * teste. A decisao morava dentro de `onCreate` e nao tinha um unico cenario — e a auditoria encontrou
 * um caminho silencioso a vinte linhas dela.
 *
 * **A ordem das duas recusas preserva a que ja existia:** o pacote e conferido primeiro. Sem ele nao
 * ha sessao possivel de jeito nenhum, e saber de qual prova se trata nao ajudaria em nada.
 *
 * [lerPacote] e a leitura do cache conferido, injetada para que este arquivo nao dependa de disco.
 */
fun decidirAbertura(
    organizacao: String?,
    contentHash: String?,
    shortId: String?,
    lerPacote: (organizacao: String, contentHash: String) -> ExamPackage?,
): AberturaDoEscaneamento {
    if (organizacao == null || contentHash == null) {
        return AberturaDoEscaneamento.NaoAbre(MotivoDeNaoAbrir.PACOTE_NAO_CONFERIDO)
    }

    val pacote = lerPacote(organizacao, contentHash)
        ?: return AberturaDoEscaneamento.NaoAbre(MotivoDeNaoAbrir.PACOTE_NAO_CONFERIDO)

    // O `short_id` vem do `Intent`, e **nao** de `pacote.meta.examId`. Os dois sao iguais hoje, mas
    // por um contrato que nada nesta base prende no aparelho: os escritores do roster — o pull e o
    // gate — usam `prova.shortId`. Ler por outro caminho faria o leitor depender de uma igualdade
    // que ninguem afirma aqui. A etapa 3 afirmou essa igualdade **no dominio**, sobre o pacote
    // publicado, e usa-la como licenca para ler por outro caminho no aparelho seria estender uma
    // afirmacao para alem do que ela afirma.
    if (shortId == null) {
        return AberturaDoEscaneamento.NaoAbre(MotivoDeNaoAbrir.PROVA_NAO_IDENTIFICADA)
    }

    return AberturaDoEscaneamento.Abre(organizacao = organizacao, prova = shortId, pacote = pacote)
}
