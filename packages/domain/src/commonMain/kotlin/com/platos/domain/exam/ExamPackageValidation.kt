package com.platos.domain.exam

/**
 * Recusa pacote incoerente **antes** de qualquer gravacao.
 *
 * A coerencia aqui e interna: o pacote precisa fechar consigo mesmo. Nao ha consulta a banco nem a
 * rede, entao a mesma verificacao vale no servidor que publica e no dispositivo que recebe — e a
 * fatia 4 vai precisar exatamente disso ao puxar a referencia imutavel.
 *
 * Cada recusa existe por um modo de falha que chegaria a folha impressa ou ao OMR:
 *
 * - posicao apontando item inexistente imprime uma questao em branco;
 * - item sem gabarito produz prova que nao pode ser corrigida offline, que e a proposta de valor
 *   inteira do Basic (§15, fatia 3);
 * - atribuicao para variante inexistente imprime folha sem prova;
 * - layout divergente dos itens poe bolha onde nao ha questao, e o OMR le lixo em silencio.
 */
fun ExamPackage.requireCoherent() {
    if (items.isEmpty()) {
        throw ExamPackageException("pacote `${meta.examId}` nao tem itens")
    }

    val duplicados = items.groupingBy { it.id }.eachCount().filterValues { it > 1 }.keys
    if (duplicados.isNotEmpty()) {
        throw ExamPackageException(
            "pacote `${meta.examId}` tem itens com identificador repetido: " +
                duplicados.sorted().joinToString(),
        )
    }

    // I1: a barreira executavel. Item sem habilidade nao e publicado.
    val semHabilidade = items.filter { item ->
        item.skills.isEmpty() || item.skills.any { it.code.isBlank() }
    }
    if (semHabilidade.isNotEmpty()) {
        throw ExamPackageException(
            "itens sem habilidade declarada: " + semHabilidade.map { it.id }.sorted().joinToString() +
                "; toda questao nasce marcada por habilidade (I1), e retro-marcar depois e o custo " +
                "que a invariante existe para evitar",
        )
    }

    val idsDeItem = items.map { it.id }.toSet()

    if (variants.isEmpty()) {
        throw ExamPackageException("pacote `${meta.examId}` nao tem variante")
    }
    for (variante in variants) {
        val orfas = variante.positions.filterValues { it !in idsDeItem }
        if (orfas.isNotEmpty()) {
            throw ExamPackageException(
                "variante `${variante.variantId}` mapeia posicao para item inexistente: " +
                    orfas.entries.sortedBy { it.key }.joinToString { "${it.key} -> ${it.value}" },
            )
        }
    }

    val idsDeVariante = variants.map { it.variantId }.toSet()
    val atribuicoesOrfas = assignments.filter { it.variantId !in idsDeVariante }
    if (atribuicoesOrfas.isNotEmpty()) {
        throw ExamPackageException(
            "atribuicoes apontam variante inexistente: " +
                atribuicoesOrfas.map { "${it.studentToken} -> ${it.variantId}" }.sorted().joinToString(),
        )
    }

    val comGabarito = answerKey.map { it.itemId }.toSet()
    val semGabarito = idsDeItem - comGabarito
    if (semGabarito.isNotEmpty()) {
        throw ExamPackageException(
            "itens sem alternativa correta declarada: " + semGabarito.sorted().joinToString() +
                "; sem gabarito a prova nao pode ser corrigida offline",
        )
    }
    val gabaritoOrfao = comGabarito - idsDeItem
    if (gabaritoOrfao.isNotEmpty()) {
        throw ExamPackageException(
            "gabarito aponta item inexistente: " + gabaritoOrfao.sorted().joinToString(),
        )
    }

    // O layout precisa falar das mesmas questoes que os itens declaram. Divergencia aqui poe bolha
    // onde nao ha questao, e o OMR le a folha inteira deslocada sem nada acusar.
    for (variante in variants) {
        val mapa = layout[variante.variantId] ?: throw ExamPackageException(
            "variante `${variante.variantId}` nao tem layout no pacote",
        )
        val noLayout = mapa.regions.flatMap { regiao -> regiao.bubbles.map { it.questionId } }.toSet()
        if (noLayout != idsDeItem) {
            val sobrando = (noLayout - idsDeItem).sorted()
            val faltando = (idsDeItem - noLayout).sorted()
            throw ExamPackageException(
                "o layout da variante `${variante.variantId}` declara questoes diferentes dos " +
                    "itens do pacote; no layout e nao nos itens: ${sobrando.joinToString()}; " +
                    "nos itens e nao no layout: ${faltando.joinToString()}",
            )
        }
    }
}
