package com.platos.domain.exam

import com.platos.domain.capture.PayloadReading
import com.platos.domain.capture.QrPayload

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
 * - atribuicao sem QR proprio imprime folha com o campo de aluno vazio, para um aluno que existe;
 * - token repetido produz duas folhas com a mesma identidade, indistinguiveis na captura;
 * - layout divergente dos itens poe bolha onde nao ha questao, e o OMR le lixo em silencio.
 *
 * E, desde a `slice-5a-regiao-discursiva`:
 *
 * - discursiva com gabarito, ou objetiva com rubrica, e item corrigido pelo caminho errado;
 * - discursiva sem regiao no layout e questao impressa sem onde escrever;
 * - atribuicao sem o QR de uma regiao imprime aquela regiao com o QR da variante, sem aluno;
 * - QR cujo payload diz outra regiao ou outro aluno atribui a resposta errada, em silencio;
 * - `fully_offline_gradable` verdadeiro com discursiva faria o aparelho tratar como definitiva uma
 *   nota que ainda nao tem a parte discursiva;
 * - `max_score` que nao fecha com gabarito e rubricas faz a nota maxima depender de qual numero se le.
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

    // A atribuicao existe para enderecar uma folha, e sem QR proprio ela nao endereca nenhuma.
    // O desfecho errado seria silencioso: a folha sairia impressa com o campo de aluno vazio, para
    // um aluno que existe, e ninguem notaria ate a captura nao ter a quem atribuir.
    val semQr = assignments.filter { it.qrs.isEmpty() }
    if (semQr.isNotEmpty()) {
        throw ExamPackageException(
            "atribuicoes sem QR proprio: " + semQr.map { it.studentToken }.sorted().joinToString() +
                "; atribuicao sem QR nao endereca folha nenhuma, e imprimir a folha da variante no " +
                "lugar dela produziria prova sem dono",
        )
    }

    // Token repetido nao e engano de digitacao: e duas folhas com a mesma identidade. A captura de
    // uma passaria pela outra, e a chave `(exam_id, student_id)` da idempotencia deixaria de
    // distinguir os dois alunos.
    val tokensRepetidos = assignments.groupingBy { it.studentToken }.eachCount()
        .filterValues { it > 1 }.keys
    if (tokensRepetidos.isNotEmpty()) {
        throw ExamPackageException(
            "atribuicoes com token repetido: " + tokensRepetidos.sorted().joinToString() +
                "; duas folhas com a mesma identidade sao indistinguiveis na captura",
        )
    }

    val objetivas = items.filter { it.kind == QuestionKind.OBJECTIVE }.map { it.id }.toSet()
    val discursivas = items.filter { it.kind == QuestionKind.ESSAY }

    conferirQrsPorRegiao()

    val comGabarito = answerKey.map { it.itemId }.toSet()
    val discursivaComGabarito = discursivas.map { it.id }.filter { it in comGabarito }
    if (discursivaComGabarito.isNotEmpty()) {
        throw ExamPackageException(
            "itens discursivos com entrada no gabarito: " + discursivaComGabarito.sorted().joinToString() +
                "; discursiva e corrigida pela rubrica, e nao pelo gabarito",
        )
    }
    val semGabarito = objetivas - comGabarito
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

    // Rubrica e da discursiva, e so dela.
    val objetivaComRubrica = items.filter { it.kind == QuestionKind.OBJECTIVE && it.rubric != null }
    if (objetivaComRubrica.isNotEmpty()) {
        throw ExamPackageException(
            "itens objetivos com rubrica: " + objetivaComRubrica.map { it.id }.sorted().joinToString(),
        )
    }
    val discursivaSemRubrica = discursivas.filter { it.rubric == null || it.rubric.criteria.isEmpty() }
    if (discursivaSemRubrica.isNotEmpty()) {
        throw ExamPackageException(
            "itens discursivos sem rubrica: " + discursivaSemRubrica.map { it.id }.sorted().joinToString() +
                "; sem rubrica a discursiva nao tem como ser corrigida nem ter moldura",
        )
    }

    // O pacote nao tem pontuacao por item: a da objetiva mora no gabarito, e a da discursiva e a soma
    // da rubrica. O que pode divergir, e e aqui que se confere, e a nota maxima declarada.
    val somaDoGabarito = answerKey.sumOf { it.points }
    val somaDasRubricas = discursivas.sumOf { item -> requireNotNull(item.rubric).criteria.sumOf { it.points } }
    if (scoring.maxScore != somaDoGabarito + somaDasRubricas) {
        throw ExamPackageException(
            "a nota maxima do pacote `${meta.examId}` e ${scoring.maxScore}, e gabarito ($somaDoGabarito) " +
                "mais rubricas ($somaDasRubricas) somam ${somaDoGabarito + somaDasRubricas}",
        )
    }

    // Correcao objetiva local so e definitiva sem discursiva (§10, D4). O campo dizer o contrario
    // faria o aparelho fechar como definitiva uma nota que ainda nao tem a parte discursiva.
    val semDiscursiva = discursivas.isEmpty()
    if (meta.fullyOfflineGradable != semDiscursiva) {
        throw ExamPackageException(
            "o pacote declara fully_offline_gradable = ${meta.fullyOfflineGradable}, e tem " +
                "${discursivas.size} item(ns) discursivo(s)",
        )
    }

    // O layout precisa falar das mesmas questoes que os itens declaram. Divergencia aqui poe bolha
    // onde nao ha questao, e o OMR le a folha inteira deslocada sem nada acusar. A discursiva entra
    // pela regiao dela, e nao por bolha.
    for (variante in variants) {
        val mapa = layout[variante.variantId] ?: throw ExamPackageException(
            "variante `${variante.variantId}` nao tem layout no pacote",
        )
        for (item in discursivas) {
            val regioes = mapa.regions.count { it.questionId == item.id }
            if (regioes != 1) {
                throw ExamPackageException(
                    "o item discursivo `${item.id}` tem $regioes regiao(oes) no layout da variante " +
                        "`${variante.variantId}`, e precisa de exatamente uma",
                )
            }
        }
        val noLayout = mapa.regions.flatMap { regiao -> regiao.bubbles.map { it.questionId } }.toSet() +
            mapa.regions.mapNotNull { it.questionId }.toSet()
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

/**
 * Cada atribuicao traz um QR para cada regiao do layout da variante dela, e cada QR diz a regiao e o
 * aluno certos (D23).
 *
 * O payload e lido pelo **mesmo** leitor que o aparelho usa (`QrPayload.read`): um leitor proprio
 * aqui concordaria com um escritor errado. E a conferencia que o espelho TypeScript de
 * `folhaDaAtribuicao` nao faz — ele troca o QR que o pacote manda, e se o pacote mandar o QR da regiao
 * errada, a folha sai errada nas duas implementacoes ao mesmo tempo.
 */
private fun ExamPackage.conferirQrsPorRegiao() {
    for (atribuicao in assignments) {
        val mapa = layout[atribuicao.variantId] ?: continue // a variante orfa ja foi recusada antes
        val esperadas = mapa.regions.map { it.index }.toSet()
        val trazidas = atribuicao.qrs.map { it.regionIndex }
        if (trazidas.toSet() != esperadas || trazidas.size != esperadas.size) {
            throw ExamPackageException(
                "a atribuicao `${atribuicao.studentToken}` traz QR para as regioes ${trazidas.sorted()}, e " +
                    "o layout da variante `${atribuicao.variantId}` tem ${esperadas.sorted()}",
            )
        }
        for (qr in atribuicao.qrs) {
            val lido = QrPayload.read(qr.payload)
            val payload = (lido as? PayloadReading.Read)?.payload ?: throw ExamPackageException(
                "a atribuicao `${atribuicao.studentToken}` tem QR ilegivel na regiao ${qr.regionIndex}: " +
                    (lido as PayloadReading.Rejected).reason,
            )
            if (payload.regionIndex != qr.regionIndex) {
                throw ExamPackageException(
                    "a atribuicao `${atribuicao.studentToken}` associa a regiao ${qr.regionIndex} a um QR " +
                        "que diz regiao ${payload.regionIndex}",
                )
            }
            if (payload.studentToken != atribuicao.studentToken) {
                throw ExamPackageException(
                    "o QR da regiao ${qr.regionIndex} da atribuicao `${atribuicao.studentToken}` diz o aluno " +
                        "`${payload.studentToken}`",
                )
            }
        }
    }
}
