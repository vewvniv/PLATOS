package com.platos.android.scan

import com.platos.domain.exam.PackageVariant
import com.platos.domain.layout.LayoutEngine
import com.platos.domain.layout.LayoutMap
import com.platos.domain.scoring.PartialScoringOutcome
import kotlinx.serialization.Serializable

/** O estado de uma regiao no caderno do aluno (§8). */
@Serializable
sealed interface EstadoDaRegiao {

    /** Lida, no gabarito, ou reconhecida, na discursiva, em algum quadro deste aluno. */
    @Serializable
    data object Capturada : EstadoDaRegiao

    /** Presente num quadro e nao lida, com o motivo, e ainda nao capturada. */
    @Serializable
    data class ComProblema(val motivo: String) : EstadoDaRegiao

    /** Ainda nao apareceu inteira em nenhum quadro deste aluno. */
    @Serializable
    data object NaoVista : EstadoDaRegiao
}

/**
 * Uma regiao esperada da folha do aluno, como o indicador dela a mostra.
 *
 * [rotulo] e "Gabarito", ou o **numero que a questao tem na folha impressa**: a chave de `positions`
 * do item da regiao (`slice-5b-2-a-nota-objetiva-parcial`, decisao 5). O `LayoutMap` nao declara o
 * numero, e `positions` e a declaracao de posicao que o pacote publica; derivar da ordem das regioes
 * ou das questoes criaria uma segunda fonte. O teste que prende esta chave ao texto impresso e
 * `ProvaComDiscursivaNaSessaoTest`, e e ele que cai quando a paginacao (ADR-0019) passar a declarar o
 * numero no mapa.
 */
@Serializable
data class RegiaoDoCaderno(
    val regionIndex: Int,
    /** Se esta e a regiao de gabarito: a que o mapa declara e nao e discursiva. */
    val gabarito: Boolean,
    val rotulo: String,
    val estado: EstadoDaRegiao,
    /**
     * A resposta guardada da regiao discursiva (`slice-5c-1-a-resposta-fica-no-aparelho`). Nula no
     * gabarito, e na discursiva nao capturada. **Sem `require` de invariante** ("capturada
     * discursiva implica resposta"): um caderno guardado antes desta mudanca a violaria, e o decode
     * derrubaria o caderno inteiro. A normalizacao acontece na leitura.
     */
    val resposta: RespostaGuardada? = null,
)

/**
 * O caderno do aluno cuja folha esta sendo escaneada (§8, completude), numa prova com discursiva.
 *
 * **Estado da sessao, em memoria, e nunca gravado** (decisao 4). As regioes sao as que o `LayoutMap`
 * da variante declara, e [parcial] e a ultima apuracao parcial do gabarito deste aluno.
 *
 * **Acumulo deliberado do mesmo aluno**, e por isso excecao a "um resultado novo substitui o anterior
 * por inteiro": capturada nao volta atras, e um quadro ruim depois de um bom nao apaga o que ja foi
 * lido. A parcial continua seguindo a regra — a ultima apuracao do gabarito substitui a anterior por
 * inteiro. A folha de outro aluno comeca outro caderno.
 */
@Serializable
data class Caderno(
    /** O token do aluno, pelo QR: vazio na folha avulsa. */
    val aluno: String,
    /** Um por regiao esperada, na ordem do mapa. Vazio quando a variante da folha nao se determina. */
    val regioes: List<RegiaoDoCaderno>,
    /** A ultima apuracao parcial do gabarito deste aluno, ou nula enquanto ele nao foi lido. */
    val parcial: PartialScoringOutcome?,
    /**
     * Se este caderno ja foi entregue para gravar (`slice-5b-4-envio-da-parcial`, design decisao
     * 1). Marcada uma vez, na mesma passada em que completa, e sobrevive ao fechamento do
     * aplicativo pelo mesmo caminho que o resto do caderno — e e essa sobrevivencia que impede a
     * reabertura do aplicativo de entregar de novo o mesmo caderno completo.
     */
    val entregue: Boolean = false,
) {

    val capturadas: Int get() = regioes.count { it.estado == EstadoDaRegiao.Capturada }

    val esperadas: Int get() = regioes.size

    /**
     * Este caderno depois de um quadro do mesmo aluno.
     *
     * [vistas] e o que o quadro disse de cada regiao presente, por indice. [parcialNova] e a apuracao
     * do gabarito lido no quadro, ou nula quando ele nao foi lido, e entao vale a anterior.
     *
     * **Nao mexe em [entregue].** Quem decide entregar e marca a entrega e `ScanSession`, que sabe
     * o que "completo" significa para o disparo (design decisao 1); este metodo so atualiza estado
     * de regiao e parcial, como sempre fez.
     */
    internal fun depoisDe(
        vistas: Map<Int, EstadoDaRegiao>,
        parcialNova: PartialScoringOutcome?,
        respostas: Map<Int, RespostaGuardada> = emptyMap(),
    ): Caderno =
        copy(
            regioes = regioes.map { regiao ->
                val agora = vistas[regiao.regionIndex]
                when {
                    regiao.estado == EstadoDaRegiao.Capturada -> regiao
                    agora == null -> regiao
                    else -> regiao.copy(estado = agora, resposta = respostas[regiao.regionIndex] ?: regiao.resposta)
                }
            },
            parcial = parcialNova ?: parcial,
        )

    /**
     * Este caderno com a resposta de [regionIndex] descartada: a regiao volta a nao vista. Nada mais
     * muda — as outras regioes, a parcial e [entregue] ficam como estavam.
     */
    internal fun semResposta(regionIndex: Int): Caderno =
        copy(
            regioes = regioes.map {
                if (it.regionIndex == regionIndex) it.copy(estado = EstadoDaRegiao.NaoVista, resposta = null) else it
            },
        )

    companion object {

        /** O caderno vazio de [aluno]: todas as regioes do [mapa] nao vistas. */
        internal fun novo(aluno: String, variante: PackageVariant?, mapa: LayoutMap?): Caderno {
            if (variante == null || mapa == null) return Caderno(aluno, emptyList(), null)
            val numeroDe = variante.positions.entries.associate { (posicao, item) -> item to posicao }
            return Caderno(
                aluno = aluno,
                regioes = mapa.regions.map { regiao ->
                    val discursiva = regiao.kind == LayoutEngine.ESSAY_KIND
                    RegiaoDoCaderno(
                        regionIndex = regiao.index,
                        gabarito = !discursiva,
                        rotulo = if (discursiva) {
                            val questao = requireNotNull(regiao.questionId) {
                                "regiao discursiva ${regiao.index} sem questao declarada no mapa"
                            }
                            requireNotNull(numeroDe[questao]) {
                                "a questao '$questao' da regiao ${regiao.index} nao tem posicao na variante " +
                                    "'${variante.variantId}'"
                            }
                        } else {
                            ROTULO_GABARITO
                        },
                        estado = EstadoDaRegiao.NaoVista,
                    )
                },
                parcial = null,
            )
        }

        const val ROTULO_GABARITO = "Gabarito"
    }
}
