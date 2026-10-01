package com.platos.android.scan

import com.platos.domain.scoring.PartialScore
import com.platos.domain.scoring.PontuacaoDada
import com.platos.domain.scoring.Pontos
import com.platos.domain.scoring.lerPontos

/** Uma discursiva na tela de nota: a questao, o numero impresso, o que vale e a resposta guardada. */
internal data class LinhaDaNota(
    val questionId: String,
    val rotulo: String,
    val worth: Int,
    val resposta: RespostaGuardada?,
)

/**
 * As linhas da tela de nota, na ordem do caderno: cada regiao discursiva casa com o `AwaitingEssay` da parcial
 * pela questao que o mapa declara (`RegiaoDoCaderno.questionId`). Regiao cuja questao a parcial nao aguarda fica de
 * fora — `CorrecaoDoProfessor.completar` recusaria a nota, e a tela nao a monta.
 */
internal fun linhasDaNota(caderno: Caderno, parcial: PartialScore): List<LinhaDaNota> {
    val valor = parcial.awaiting.associate { it.questionId to it.points }
    return caderno.regioes.filter { !it.gabarito }.mapNotNull { regiao ->
        val questao = regiao.questionId ?: return@mapNotNull null
        val worth = valor[questao] ?: return@mapNotNull null
        LinhaDaNota(questao, regiao.rotulo, worth, regiao.resposta)
    }
}

/** O que o professor digitou, lido: as pontuacoes e o total exato, ou o motivo que serve para a tela. */
internal sealed interface EntradaDaNota {
    data class Valida(val pontuacoes: List<PontuacaoDada>, val total: Pontos) : EntradaDaNota
    data class Invalida(val motivo: String) : EntradaDaNota
}

/**
 * Le os textos digitados com o **mesmo** `lerPontos` que o servidor usa, e nomeia a questao pelo **numero impresso**.
 * `1,5` (virgula, como o pt-BR digita) e recusado pela mensagem do dominio — que diz "ponto decimal" — e nunca lido
 * como `15`. [objetivos] e a parte objetiva da parcial, para o total.
 */
internal fun interpretarEntrada(
    linhas: List<LinhaDaNota>,
    textos: Map<String, String>,
    objetivos: Int,
): EntradaDaNota {
    val dadas = mutableListOf<PontuacaoDada>()
    var total = Pontos.inteiros(objetivos)
    for (linha in linhas) {
        val de = "a questao ${linha.rotulo}"
        val texto = textos[linha.questionId]?.trim().orEmpty()
        if (texto.isEmpty()) return EntradaDaNota.Invalida("$de ainda nao tem pontuacao")
        val pontos = try {
            lerPontos(texto, de)
        } catch (e: IllegalArgumentException) {
            return EntradaDaNota.Invalida(e.message ?: "$de: pontuacao invalida")
        }
        if (pontos > Pontos.inteiros(linha.worth)) {
            return EntradaDaNota.Invalida("$de vale no maximo ${linha.worth}")
        }
        dadas += PontuacaoDada(linha.questionId, pontos)
        total += pontos
    }
    return EntradaDaNota.Valida(dadas, total)
}
