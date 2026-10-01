package com.platos.api.http.dto

import com.platos.domain.scoring.PontuacaoDada
import com.platos.domain.scoring.Pontos
import com.platos.domain.scoring.QuestionOutcome
import com.platos.domain.transport.GradedResultSubmissionDto

private const val ORIGEM_ACEITA = "teacher"
private const val CAMINHO_ACEITO = "image"

/**
 * O que a Fase 1 da nota do professor consegue validar **sem o pacote** (`slice-5c-2-a-nota-do-professor`,
 * design secao 3): forma, origem, caminho e decimais. O que depende do pacote publicado — as discursivas,
 * a faixa, o total — e a Fase 2, em `conferirNotaDoProfessor`.
 */
data class NotaDoProfessorSubmetida(
    val packageHash: String,
    val variantId: String,
    val maxScoreDeclarado: Int,
    val totalDeclarado: Pontos,
    val closedDeclarado: Boolean,
    val outcomes: List<QuestionOutcome>,
    val pontuacoes: List<PontuacaoDada>,
)

/**
 * Lanca [IllegalArgumentException] com a mensagem que a rota devolve em 400.
 *
 * **Recusa a origem e o caminho de fatia posterior** (`ai`, `text`) aqui, antes de qualquer SQL. A parte
 * objetiva vem como `observations`, e `paraOutcome` recusa `answer_kind` que nao exista — inclusive
 * `discursiva_corrigida`, que nao e uma resposta objetiva e nao pode entrar por esse caminho.
 */
fun GradedResultSubmissionDto.paraNotaSubmetida(): NotaDoProfessorSubmetida {
    require(origin == ORIGEM_ACEITA) { "origem '$origin' nao e aceita nesta rota: so '$ORIGEM_ACEITA'" }
    require(path == CAMINHO_ACEITO) { "caminho '$path' nao e aceito nesta rota: so '$CAMINHO_ACEITO'" }
    require(completesCaptureId.isNotBlank()) {
        "completes_capture_id e obrigatorio: diz qual captura da parcial esta nota completa"
    }

    return NotaDoProfessorSubmetida(
        packageHash = packageHash,
        variantId = variantId,
        maxScoreDeclarado = maxScore,
        totalDeclarado = lerPontos(points, "o total declarado"),
        closedDeclarado = closed,
        outcomes = observations.map { it.paraOutcome() },
        pontuacoes = essayGrades.map { PontuacaoDada(it.itemId, lerPontos(it.earned, "a discursiva '${it.itemId}'")) },
    )
}

/** Le a pontuacao e, se falhar, diz **de qual campo** — a mensagem de 400 nomeia a questao. */
private fun lerPontos(texto: String, de: String): Pontos =
    try {
        Pontos.parse(texto)
    } catch (erro: IllegalArgumentException) {
        throw IllegalArgumentException("$de: ${erro.message}")
    }
