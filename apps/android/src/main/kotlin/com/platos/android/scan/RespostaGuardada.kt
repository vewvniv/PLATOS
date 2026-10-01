package com.platos.android.scan

import com.platos.android.vision.FrameOutcome
import kotlinx.serialization.Serializable

/**
 * A resposta de uma regiao discursiva, como o caderno a **referencia** (`slice-5c-1-a-resposta-fica-no-aparelho`,
 * design, decisao 3): o nome do arquivo em `respostas/`, o instante da captura e o sinal de desvio que o
 * dominio ja classificou. A imagem nao mora aqui.
 *
 * Guardam-se `desvioSinalizado` e `foraPpm`, e nao o `DesvioDaResposta` inteiro: a classificacao e do
 * dominio (regra 7 do `CLAUDE.md`) e ja foi feita; `dentro`/`fora` em centesimos de mm2 nao tem leitor.
 */
@Serializable
data class RespostaGuardada(
    /** O nome do arquivo em `filesDir/respostas/`: um UUID, sem aluno, prova nem organizacao. */
    val arquivo: String,
    /** O instante da captura, pelo relogio injetado no analisador. E dele que o prazo de 30 dias conta. */
    val capturadaEm: Long,
    val desvioSinalizado: Boolean,
    /** A parte da tinta do aluno fora da area, em partes por milhao. */
    val foraPpm: Int,
)

/** O que o analisador entregou de uma regiao discursiva reconhecida: a resposta guardada, ou a recusa. */
sealed interface RespostaDoQuadro {

    /** Gravada inteira: o arquivo existe quando esta entrega chega. */
    data class Guardada(val resposta: RespostaGuardada) : RespostaDoQuadro

    /** O recorte foi recusado, ou a imagem nao pode ser gravada. Nenhum arquivo ficou. */
    data class Recusada(val motivo: String) : RespostaDoQuadro
}

/**
 * Um quadro analisado: o que a analise concluiu, e o que o analisador gravou por regiao discursiva
 * (indice da regiao). O quadro em si ja foi liberado quando isto chega a sessao.
 */
data class QuadroAnalisado(
    val resultado: FrameOutcome,
    val respostas: Map<Int, RespostaDoQuadro> = emptyMap(),
)
