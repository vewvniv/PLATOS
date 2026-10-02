package com.platos.android.corpus

import com.platos.android.scan.LinhaDaNota
import com.platos.android.scan.RespostasGuardadas
import com.platos.domain.scoring.PontuacaoDada

/** O aviso quando nem as amostras puderam ser montadas ou a copia inteira falhou. */
internal const val TODAS = "todas"

/**
 * As discursivas da folha como amostras: cada linha casa com a pontuacao dada a **ela** (pela questao). Linha sem foto
 * guardada (caderno anterior a 5c-1) fica de fora, sem inventar falha. A pontuacao **zero** e uma pontuacao.
 */
internal fun amostrasDaNota(
    linhas: List<LinhaDaNota>,
    pontuacoes: List<PontuacaoDada>,
    pacote: String,
): List<AmostraACopiar> {
    val dadas = pontuacoes.associate { it.questionId to it.earned }
    return linhas.mapNotNull { linha ->
        val resposta = linha.resposta ?: return@mapNotNull null
        val pontos = dadas[linha.questionId] ?: return@mapNotNull null
        AmostraACopiar(resposta.arquivo, linha.rotulo, pontos, linha.worth, pacote, linha.questionId)
    }
}

/** O que a gravacao com coleta decidiu: se a nota foi gravada, e quais questoes nao foram copiadas. */
data class GravacaoComColeta(val gravou: Boolean, val falhas: List<String>)

/**
 * A ordem da cooperacao entre a nota e a coleta (`slice-5d-corpus-de-medicao`, spec `scan-session`):
 *
 * 1. **copia primeiro.** A imagem da resposta e eliminada quando o servidor confirma a nota, e o envio em segundo
 *    plano pode confirmar assim que a nota esta duravel; copiar depois perderia essa corrida.
 * 2. **grava a nota**, e e ela que decide o sucesso: a falha da copia vira aviso, nunca falha da nota.
 * 3. **nota nao gravada elimina as amostras que a copia acabou de criar**: nota que nao existe nao deixa amostra.
 *
 * [amostras] e lazy de proposito: calcular o hash do pacote custa, e com a coleta desligada nao ha o que copiar.
 * Bloqueante: quem chama a poe em `Dispatchers.IO`.
 */
internal fun gravarNotaComColeta(
    coleta: ColetaDoCorpus,
    respostas: RespostasGuardadas,
    amostras: () -> List<AmostraACopiar>,
    gravar: () -> Boolean,
): GravacaoComColeta {
    val copia = copiarSeLigada(coleta, respostas, amostras)
    val gravou = gravar()
    if (!gravou && copia.ids.isNotEmpty()) {
        try {
            coleta.eliminar(copia.ids)
        } catch (e: Exception) {
            // Fica para a eliminacao por prazo: a amostra e uma copia, e o prazo a alcanca.
        }
    }
    return GravacaoComColeta(gravou, copia.falhas)
}

private fun copiarSeLigada(
    coleta: ColetaDoCorpus,
    respostas: RespostasGuardadas,
    amostras: () -> List<AmostraACopiar>,
): ResultadoDaCopia = try {
    if (!coleta.ligada()) {
        ResultadoDaCopia.NADA
    } else {
        val aCopiar = amostras()
        if (aCopiar.isEmpty()) ResultadoDaCopia.NADA else coleta.copiar(aCopiar, respostas)
    }
} catch (e: Exception) {
    ResultadoDaCopia(emptyList(), listOf(TODAS))
}
