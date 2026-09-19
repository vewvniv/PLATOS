package com.platos.api.exam

import com.platos.domain.exam.ExamPackage
import com.platos.domain.scoring.ObjectiveScore

/**
 * O desfecho da conferencia de proveniencia de um resultado (achado 2.2 da auditoria de 2026-09-18).
 *
 * **Nao confere e diferente de nao existe.** Prova inexistente, prova sem pacote e prova de
 * organizacao alheia sao **ausencia**, e continuam indistinguiveis entre si na rota. O que este tipo
 * representa e a outra coisa: a prova existe, o pacote existe, e o corpo apresentado nao fecha com
 * ele. E decisao do servidor sobre o pedido, e a rota a traduz em 400.
 */
sealed interface Proveniencia {

    /** O pacote e a variante declarados sao os do pacote publicado desta prova. */
    data object Confere : Proveniencia

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
 * **Isto nao e uma segunda [ObjectiveScore].** [com.platos.api.http.dto.paraNota] continua sendo o
 * ponto unico da coerencia **interna** do corpo — nota na escala, evidencia que soma a nota, item
 * repetido, `closed` contra as pendencias —, e com o **mesmo** codigo que rodou no aparelho (regra 7
 * do `CLAUDE.md`). O que se confere aqui e informacao que `ObjectiveScore` nao tem e nao deve ter:
 * ele e do dominio compartilhado e roda offline no aparelho, onde o pacote publicado do servidor nao
 * existe. Nao e a mesma regra escrita duas vezes; e uma regra que o ponto unico nao podia ter.
 *
 * **E nao se recalcula a nota a partir do gabarito.** O gabarito esta neste mesmo `content`, a um
 * campo de distancia, e a tentacao e exatamente por isso que a proibicao esta escrita: D4 e §10 sao
 * explicitos — correcao objetiva local e definitiva quando nao ha discursivas. Confere-se
 * **proveniencia**, nao aritmetica.
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
fun conferirProveniencia(pacote: PackageContent, nota: ObjectiveScore): Proveniencia {
    if (nota.packageHash != pacote.contentHash) {
        return Proveniencia.NaoConfere(
            "o resultado diz ter sido apurado contra o pacote `${nota.packageHash}`, " +
                "e o pacote publicado desta prova e `${pacote.contentHash}`",
        )
    }

    // A unica lista de variantes desta prova. Uma segunda — coluna, tabela ou constante — seria o
    // segundo oraculo que a decisao 6 do design recusa.
    val publicado = ExamPackage.JSON.decodeFromString(ExamPackage.serializer(), pacote.content)
    val declaradas = publicado.variants.map { it.variantId }
    if (nota.variantId !in declaradas) {
        return Proveniencia.NaoConfere(
            "o resultado diz a variante `${nota.variantId}`, e o pacote publicado desta prova " +
                "declara ${declaradas.joinToString(", ") { "`$it`" }.ifEmpty { "nenhuma" }}",
        )
    }

    return Proveniencia.Confere
}
