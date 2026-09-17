package com.platos.android.session

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * A marca de leitura cacheada, pronta para a tela desenhar.
 *
 * **Duas partes, e as duas sao obrigatorias pelo requisito:** [rotulo] e o que se ve de longe — ele
 * vira selo, com cor e borda, e nao frase no meio do texto —, e [idade] diz **de quando** o dado e.
 * Marca sem idade afirmaria menos do que se sabe; idade sem marca some na leitura apressada de quem
 * esta numa sala com trinta alunos.
 *
 * O tipo existe para que a tela nao componha texto: ela recebe as duas cadeias prontas e desenha. E
 * a mesma disciplina de [TextoSemOrganizacao], e pela mesma razao — o que a tela decide, nenhum teste
 * desta base alcanca.
 */
data class MarcaDeLeitura(
    val rotulo: String,
    val idade: String,
)

/**
 * Data e hora absolutas, e nao "ha 2 h".
 *
 * **Decisao desta tarefa, que o `design.md` deixou aberta** ("como apresentar a idade"). Absoluto
 * ganhou por tres razoes: e verificavel numa funcao pura com um fuso fixo, enquanto "ha 2 h" precisa
 * de relogio na composicao e envelhece na tela sem recompor; nao existe teto de validade nesta fatia
 * (decisao 2), entao uma visao de meses atras e possivel e "ha muito tempo" nao ajudaria ninguem; e o
 * professor decide pela data — "de ontem a tarde" e uma informacao com a qual ele sabe o que fazer.
 *
 * O ano entra por causa da mesma ausencia de teto: `10/09` sozinho e ambiguo para uma visao do ano
 * passado, e uma visao do ano passado nao e hipotese, e consequencia da decisao 2.
 */
private val FORMATO = DateTimeFormatter.ofPattern("dd/MM/yyyy 'as' HH:mm")

/** O rotulo do selo da visao guardada: ela so chega a tela **porque** a consulta falhou. */
const val ROTULO_SEM_CONEXAO = "SEM CONEXAO"

/**
 * O rotulo do selo do roster guardado.
 *
 * **Nao fala de conexao, e a diferenca nao e de gosto.** A visao guardada so aparece quando a rede
 * falhou, entao "SEM CONEXAO" e verdade toda vez que ela e vista. O roster guardado aparece em
 * **toda** leitura de folha — o escaneamento acontece sobre o que foi puxado —, inclusive com o
 * aparelho on-line e o pull recem-concluido. Dizer "sem conexao" ali seria falso na maioria das vezes
 * em que o selo aparece, e ensinaria a ignorar o mesmo selo na tela onde ele e verdade.
 */
const val ROTULO_LISTA_BAIXADA = "LISTA BAIXADA"

/**
 * O que marcar na tela, ou `null` quando nao ha nada a marcar.
 *
 * `null` para [Procedencia.Fresca] e deliberado, e nao ausencia de tratamento: dado que acabou de
 * chegar **nao leva selo**, senao o selo perde o significado e a tela passa a marcar tudo. `when`
 * exaustivo sem `else`, entao uma terceira procedencia quebra a compilacao aqui em vez de cair no
 * ramo silencioso.
 *
 * **[rotulo] entra por parametro, e a idade nao.** A regra — dado guardado leva selo, e o selo diz de
 * quando o dado e — e **uma so**, e mora aqui; o que muda entre as telas e a palavra que o selo
 * mostra, porque a visao e o roster chegam a tela por razoes diferentes. Parametrizar o rotulo mantem
 * a regra num lugar; duplicar a funcao para trocar uma palavra faria as duas metades divergirem na
 * primeira mudanca.
 */
fun marcaDeLeitura(
    procedencia: Procedencia,
    zona: ZoneId,
    rotulo: String = ROTULO_SEM_CONEXAO,
): MarcaDeLeitura? = when (procedencia) {
    is Procedencia.Fresca -> null
    is Procedencia.Cacheada -> MarcaDeLeitura(
        rotulo = rotulo,
        idade = "visto em " + FORMATO.format(Instant.ofEpochMilli(procedencia.vistaEm).atZone(zona)),
    )
}

/**
 * O que dizer quando a atualizacao pedida nao deu, ou `null` quando nao houve tentativa frustrada.
 *
 * **As duas frases terminam do mesmo jeito** — "o que esta na tela continua valendo" —, e isso e o
 * requisito escrito para quem le: a tela nao esvazia, e quem tocou em "atualizar" precisa saber que o
 * dado antigo segue ali de proposito, e nao por engano.
 *
 * O que varia e a causa, porque a acao de quem le muda: sem rede se tenta mais tarde, e resposta que
 * nao serve se tenta de novo agora.
 */
fun avisoDeAtualizacao(falha: FalhaDaConsulta?): String? = when (falha) {
    null -> null
    FalhaDaConsulta.SEM_REDE ->
        "Nao foi possivel atualizar: o aparelho nao alcancou o servidor. " +
            "O que esta na tela continua valendo."
    FalhaDaConsulta.OUTRA ->
        "O servidor respondeu, mas nao foi possivel usar a resposta. " +
            "O que esta na tela continua valendo."
}

/**
 * O mesmo, para a listagem de provas.
 *
 * Duas funcoes e nao uma generica: [FalhaDaConsulta] e [FalhaDaListagem] sao enums separados de
 * proposito — cada tela erra por conta propria —, e unifica-las por um tipo comum criaria a
 * abstracao que a regra 8 proibe enquanto nao houver necessidade comprovada. As frases sao as
 * mesmas hoje, e podem deixar de ser sem que nada precise ser desmontado.
 */
fun avisoDeAtualizacao(falha: FalhaDaListagem?): String? = when (falha) {
    null -> null
    FalhaDaListagem.SEM_REDE ->
        "Nao foi possivel atualizar: o aparelho nao alcancou o servidor. " +
            "O que esta na tela continua valendo."
    FalhaDaListagem.OUTRA ->
        "O servidor respondeu, mas nao foi possivel usar a resposta. " +
            "O que esta na tela continua valendo."
}
