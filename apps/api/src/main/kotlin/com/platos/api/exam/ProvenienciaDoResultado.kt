package com.platos.api.exam

import com.platos.api.http.dto.ApuracaoSubmetida
import com.platos.api.http.dto.ParteObjetivaSubmetida
import com.platos.domain.exam.ExamPackage
import com.platos.domain.exam.PackageVariant
import com.platos.domain.exam.QuestionKind
import com.platos.domain.scoring.ApuracaoParaEnvio
import com.platos.domain.scoring.AwaitingEssay
import com.platos.domain.scoring.PartialScore

/**
 * O desfecho da conferencia de proveniencia de um resultado (achado 2.2 da auditoria de 2026-09-18).
 *
 * **Nao confere e diferente de nao existe.** Prova inexistente, prova sem pacote e prova de
 * organizacao alheia sao **ausencia**, e continuam indistinguiveis entre si na rota. O que este tipo
 * representa e a outra coisa: a prova existe, o pacote existe, e o corpo apresentado nao fecha com
 * ele. E decisao do servidor sobre o pedido, e a rota a traduz em 400.
 */
sealed interface Proveniencia {

    /**
     * O pacote e a variante declarados sao os do pacote publicado desta prova, e [apuracao] e o
     * valor pronto para [com.platos.api.exam.ResultQueries.record] — para uma parcial, e aqui que
     * [PartialScore] passa a existir de verdade, com `awaiting` e o maximo objetivo derivados do
     * pacote (`slice-5b-4-envio-da-parcial`, design decisao 4, Fase 2).
     */
    data class Confere(val apuracao: ApuracaoParaEnvio) : Proveniencia

    /** O [motivo] nomeia **qual** dos dois campos nao fecha, e contra o que. */
    data class NaoConfere(val motivo: String) : Proveniencia
}

/**
 * Confere a proveniencia declarada contra o pacote publicado da prova.
 *
 * **Por que isto existe.** `grading_result.package_hash` e `variant_id` eram gravados exatamente
 * como o aparelho os enviou, sem nada os comparar com o pacote publicado. A coluna existe para ser
 * prova — a migration diz "nota sem dizer de qual pacote e vira numero sem prova" —, e §9 da
 * arquitetura da a razao de fundo: a rastreabilidade existe para **auditoria de contestacao de
 * nota**. O fato e append-only: proveniencia errada nao tem conserto, so revisao nova, que nao apaga
 * a anterior. Identificar sem conferir e declarar.
 *
 * **Isto nao e uma segunda validacao de [com.platos.domain.scoring.ObjectiveScore] ou
 * [PartialScore].** [com.platos.api.http.dto.paraApuracaoSubmetida] continua sendo o ponto unico da
 * coerencia **interna** do corpo, com o **mesmo** codigo que rodou no aparelho (regra 7 do
 * `CLAUDE.md`). O que se confere aqui e informacao que nenhum dos dois tem e nao deve ter: eles sao
 * do dominio compartilhado e rodam offline no aparelho, onde o pacote publicado do servidor nao
 * existe. Nao e a mesma regra escrita duas vezes; e uma regra que o ponto unico nao podia ter.
 *
 * **E nao se recalcula a nota a partir do gabarito.** O gabarito esta neste mesmo `content`, a um
 * campo de distancia, e a tentacao e exatamente por isso que a proibicao esta escrita: D4 e §10 sao
 * explicitos — correcao objetiva local e definitiva quando nao ha discursivas. Confere-se
 * **proveniencia**, nao aritmetica — inclusive para a parcial: `awaiting` e o maximo objetivo saem
 * de **filtrar** `PackageItem.kind`, o mesmo campo unico que `ObjectiveScoring.scorePartial` ja
 * filtra no aparelho, e nao de reapurar nada.
 *
 * **A ordem das duas travas nao e arbitraria.** O hash vem primeiro porque e ele que decide se
 * [pacote] e mesmo o artefato contra o qual a nota foi apurada; so depois disso faz sentido perguntar
 * o que ele declara. Conferir a variante antes seria le-la de um pacote que ainda nao se sabe ser o
 * certo.
 *
 * Um `content` que nao parseia e pacote corrompido no banco, e nao pedido ruim: a excecao sobe, e a
 * falha e do servidor. Ela nao acontece depois do hash bater sem que algo muito pior tenha
 * acontecido antes.
 */
fun conferirProveniencia(pacote: PackageContent, apuracao: ApuracaoSubmetida): Proveniencia {
    val packageHash = when (apuracao) {
        is ApuracaoSubmetida.Completa -> apuracao.nota.packageHash
        is ApuracaoSubmetida.Parcial -> apuracao.parte.packageHash
    }
    val variantId = when (apuracao) {
        is ApuracaoSubmetida.Completa -> apuracao.nota.variantId
        is ApuracaoSubmetida.Parcial -> apuracao.parte.variantId
    }

    val base = conferirPacoteEVariante(pacote, packageHash, variantId)
    if (base is ConferenciaDoPacote.Falha) return Proveniencia.NaoConfere(base.motivo)
    base as ConferenciaDoPacote.Ok

    return when (apuracao) {
        is ApuracaoSubmetida.Completa -> Proveniencia.Confere(ApuracaoParaEnvio.Completa(apuracao.nota))
        is ApuracaoSubmetida.Parcial -> conferirParcial(base.publicado, base.variante, apuracao.parte)
    }
}

/** O pacote publicado decodificado e a variante declarada, ou o motivo de o corpo nao fechar com eles. */
private sealed interface ConferenciaDoPacote {
    data class Ok(val publicado: ExamPackage, val variante: PackageVariant) : ConferenciaDoPacote
    data class Falha(val motivo: String) : ConferenciaDoPacote
}

/**
 * As duas travas de proveniencia, na ordem que o KDoc de [conferirProveniencia] justifica: o hash vem
 * primeiro porque e ele que decide se [pacote] e o artefato certo; so depois faz sentido perguntar o que
 * ele declara. **As mensagens sao as de antes, byte a byte**: os testes de rota as leem.
 */
private fun conferirPacoteEVariante(
    pacote: PackageContent,
    packageHash: String,
    variantId: String,
): ConferenciaDoPacote {
    if (packageHash != pacote.contentHash) {
        return ConferenciaDoPacote.Falha(
            "o resultado diz ter sido apurado contra o pacote `$packageHash`, " +
                "e o pacote publicado desta prova e `${pacote.contentHash}`",
        )
    }

    // A unica lista de variantes desta prova. Uma segunda — coluna, tabela ou constante — seria o
    // segundo oraculo que a decisao 6 do design recusa.
    val publicado = ExamPackage.JSON.decodeFromString(ExamPackage.serializer(), pacote.content)
    val declaradas = publicado.variants.map { it.variantId }
    val variante = publicado.variants.firstOrNull { it.variantId == variantId }
        ?: return ConferenciaDoPacote.Falha(
            "o resultado diz a variante `$variantId`, e o pacote publicado desta prova " +
                "declara ${declaradas.joinToString(", ") { "`$it`" }.ifEmpty { "nenhuma" }}",
        )

    return ConferenciaDoPacote.Ok(publicado, variante)
}

private sealed interface Derivacao {
    data class Ok(val discursivas: List<AwaitingEssay>) : Derivacao
    data class Falha(val motivo: String) : Derivacao
}

/** As discursivas da variante, cada uma com o que vale (a soma da rubrica), lidas do pacote publicado. */
private fun derivarDiscursivas(publicado: ExamPackage, variante: PackageVariant): Derivacao {
    val itens = publicado.items.associateBy { it.id }
    val discursivas = mutableListOf<AwaitingEssay>()
    for (itemId in variante.positions.values) {
        val item = itens[itemId] ?: return Derivacao.Falha(
            "item `$itemId` da variante `${variante.variantId}` nao existe no pacote publicado",
        )
        if (item.kind == QuestionKind.ESSAY) {
            val rubrica = item.rubric ?: return Derivacao.Falha(
                "discursiva `${item.id}` nao tem rubrica no pacote publicado",
            )
            discursivas += AwaitingEssay(item.id, rubrica.criteria.sumOf { it.points })
        }
    }
    return Derivacao.Ok(discursivas)
}

/** O maximo da parte objetiva: o gabarito dos itens que o pacote declara objetivos. */
private fun maximoObjetivo(publicado: ExamPackage): Int {
    val itens = publicado.items.associateBy { it.id }
    return publicado.answerKey
        .filter { entrada -> itens.getValue(entrada.itemId).kind == QuestionKind.OBJECTIVE }
        .sumOf { it.points }
}

/** A Fase 2 de uma parcial: deriva `awaiting` e o maximo objetivo do pacote, e constroi a [PartialScore]. */
private fun conferirParcial(
    publicado: ExamPackage,
    variante: PackageVariant,
    parte: ParteObjetivaSubmetida,
): Proveniencia {
    val discursivas = when (val derivacao = derivarDiscursivas(publicado, variante)) {
        is Derivacao.Falha -> return Proveniencia.NaoConfere(derivacao.motivo)
        is Derivacao.Ok -> derivacao.discursivas
    }

    if (discursivas.isEmpty()) {
        return Proveniencia.NaoConfere(
            "o resultado diz ser parcial de discursiva, e a variante `${variante.variantId}` do " +
                "pacote publicado nao declara nenhuma questao discursiva",
        )
    }

    return try {
        val partial = PartialScore(
            packageHash = parte.packageHash,
            variantId = parte.variantId,
            objectivePoints = parte.objectivePoints,
            objectiveMaxScore = maximoObjetivo(publicado),
            maxScore = parte.maxScoreDeclarado,
            awaiting = discursivas,
            pending = parte.pending,
            outcomes = parte.outcomes,
        )
        Proveniencia.Confere(ApuracaoParaEnvio.Parcial(partial))
    } catch (incoerente: IllegalArgumentException) {
        Proveniencia.NaoConfere(incoerente.message ?: "parcial incoerente com o pacote publicado")
    }
}
