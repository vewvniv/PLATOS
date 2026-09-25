package com.platos.domain.layout

import com.platos.domain.geometry.Ppm
import com.platos.domain.geometry.Um

/** Resultado da validacao do mapa. */
sealed interface ValidationResult {

    data object Valid : ValidationResult

    data class Invalid(val problems: List<String>) : ValidationResult

    val isValid: Boolean get() = this is Valid
}

/**
 * Valida o `LayoutMap` sem renderizar (§6).
 *
 * O ponto e custo: conferir schema, unicidade, sobreposicao e faixa de coordenadas e desprezivel
 * perto de rasterizar, e impede que um bug de client publique prova ilegivel. A funcao e pura e
 * nao altera o mapa.
 *
 * Acumula todos os problemas em vez de parar no primeiro: quem recebe um mapa invalido quer saber
 * tudo que esta errado, nao descobrir um defeito por rodada.
 */
fun LayoutMap.validate(): ValidationResult {
    val problems = mutableListOf<String>()

    if (layoutEngineVersion < 1) {
        problems += "layout_engine_version invalido: $layoutEngineVersion"
    }
    if (minRendererVersion < 1) {
        problems += "min_renderer_version invalido: $minRendererVersion"
    }
    if (pageWidth <= 0 || pageHeight <= 0) {
        problems += "pagina com dimensao nao positiva: ${pageWidth}x$pageHeight"
    }
    if (fontSha256.isBlank()) {
        problems += "mapa sem identificacao da fonte embarcada"
    }
    if (pages.isEmpty()) {
        problems += "mapa sem paginas"
    }

    val pageIndices = pages.map { it.index }
    if (pageIndices != pageIndices.sorted() || pageIndices.toSet().size != pageIndices.size) {
        problems += "indices de pagina fora de ordem ou repetidos: $pageIndices"
    }

    val seenIds = mutableSetOf<String>()
    for (page in pages) {
        for (primitive in page.primitives) {
            if (!seenIds.add(primitive.id)) {
                problems += "identificador de elemento repetido: `${primitive.id}`"
            }
            // A imagem e a unica primitiva cujo desenho depende de um recurso externo: sem
            // referencia ou com area nula ela vira buraco na folha, e o buraco so aparece depois
            // de impresso.
            if (primitive is DrawImage) {
                if (primitive.reference.isBlank()) {
                    problems += "imagem `${primitive.id}` nao referencia recurso nenhum"
                }
                if (primitive.width <= 0 || primitive.height <= 0) {
                    problems += "imagem `${primitive.id}` com dimensao nao positiva: " +
                        "${primitive.width}x${primitive.height}"
                }
                val fits = primitive.x >= 0 && primitive.y >= 0 &&
                    primitive.x + primitive.width <= pageWidth &&
                    primitive.y + primitive.height <= pageHeight
                if (!fits) {
                    problems += "imagem `${primitive.id}` sai da pagina: " +
                        "${primitive.x}+${primitive.width} x ${primitive.y}+${primitive.height}"
                }
            }
            // Tinta fora da faixa nao tem desenho possivel, e trama chapada acima do teto de §7 e
            // o que borra na impressora de escola — e o que some para o OMR se ela for fraca. O
            // teto vale para area chapada, e nao para o tom de um glifo: a letra dentro do circulo
            // e texto, nao trama.
            when (primitive) {
                is DrawRect -> primitive.fill?.let { fill ->
                    if (fill !in 0..LayoutMap.TONE_FULL) {
                        problems += "trama de `${primitive.id}` fora da faixa de permilagem: $fill"
                    } else if (fill > LayoutMap.FLAT_TONE_CEILING) {
                        problems += "trama de `${primitive.id}` acima do teto de " +
                            "${LayoutMap.FLAT_TONE_CEILING} por mil: $fill"
                    }
                }

                is DrawText -> primitive.tone?.let { tone ->
                    if (tone !in 0..LayoutMap.TONE_FULL) {
                        problems += "tom de `${primitive.id}` fora da faixa de permilagem: $tone"
                    }
                }

                // O tom de uma linha e o do traco, e nao trama: o teto de 80 por mil e de tinta
                // chapada, que e area. A pauta a 300 por mil e o caso (ADR-0016).
                is DrawLine -> primitive.tone?.let { tone ->
                    if (tone !in 0..LayoutMap.TONE_FULL) {
                        problems += "tom de `${primitive.id}` fora da faixa de permilagem: $tone"
                    }
                }

                else -> Unit
            }
        }
    }

    val regionIndices = regions.map { it.index }
    if (regionIndices.toSet().size != regionIndices.size) {
        problems += "indices de regiao repetidos: $regionIndices"
    }

    for (region in regions) {
        if (region.page !in pageIndices) {
            problems += "regiao ${region.index} aponta para a pagina ${region.page}, que nao existe"
        }
        if (region.quadWidth <= 0 || region.quadHeight <= 0) {
            problems += "regiao ${region.index} com quadrilatero degenerado"
        }
        // A regiao discursiva imprime dois marcadores, `4k` e `4k+3`, na diagonal (ADR-0018); o
        // gabarito e a folha de teste continuam com os quatro. Uma regra por tipo, e nao "dois ou
        // quatro": uma discursiva com quatro, ou um gabarito com dois, e o mapa de outra geometria.
        if (region.kind == LayoutEngine.ESSAY_KIND) {
            val expected = listOf(4 * region.index, 4 * region.index + 3)
            if (region.markerIds.sorted() != expected || region.markerIds.size != 2) {
                problems += "regiao ${region.index} e discursiva e deveria declarar exatamente os " +
                    "marcadores $expected, veio ${region.markerIds}"
            }
        } else {
            if (region.markerIds.size != 4 || region.markerIds.toSet().size != 4) {
                problems += "regiao ${region.index} precisa de quatro marcadores distintos, veio " +
                    "${region.markerIds}"
            }
            val expected = (4 * region.index)..(4 * region.index + 3)
            if (region.markerIds.sorted() != expected.toList()) {
                problems += "regiao ${region.index} deveria usar os marcadores " +
                    "${expected.toList()}, veio ${region.markerIds}"
            }
        }

        for (bubble in region.bubbles) {
            if (!Ppm(bubble.u).isInUnitRange || !Ppm(bubble.v).isInUnitRange) {
                problems += "bolha ${bubble.questionId}/${bubble.option} da regiao " +
                    "${region.index} fora do intervalo unitario: u=${bubble.u}, v=${bubble.v}"
            }
        }
        val bubbleKeys = region.bubbles.map { it.questionId to it.option }
        if (bubbleKeys.toSet().size != bubbleKeys.size) {
            problems += "regiao ${region.index} tem bolha repetida para a mesma alternativa"
        }

        val qr = region.qr
        val qrInRange = Ppm(qr.u).isInUnitRange && Ppm(qr.v).isInUnitRange &&
            Ppm(qr.u + qr.uSize).isInUnitRange && Ppm(qr.v + qr.vSize).isInUnitRange
        if (!qrInRange) {
            problems += "QR da regiao ${region.index} sai do quadrilatero: " +
                "u=${qr.u}+${qr.uSize}, v=${qr.v}+${qr.vSize}"
        }

        // A ligacao regiao -> QR e declarada, e e por ela que a folha de cada aluno troca o QR
        // certo. Um `qr_id` que nao existe na pagina da regiao e folha cujo QR nunca seria trocado
        // — o aluno receberia o payload da variante, sem identidade, e ninguem notaria.
        val qrDaPagina = pages.firstOrNull { it.index == region.page }?.primitives
            ?.any { it is DrawQr && it.id == region.qrId } == true
        if (!qrDaPagina) {
            problems += "regiao ${region.index} declara o QR `${region.qrId}`, que nao esta entre " +
                "as primitivas da pagina ${region.page}"
        }

        if (region.kind == LayoutEngine.ESSAY_KIND) {
            val primitivas = pages.firstOrNull { it.index == region.page }?.primitives.orEmpty()
            checkEssayRegion(region, primitivas, problems)
        }
    }

    // Duas regioes para a mesma questao sao duas molduras para uma resposta: a captura recortaria
    // as duas, e qual delas vale seria decidido por quem as lesse primeiro.
    val porQuestao = regions.mapNotNull { it.questionId }.groupingBy { it }.eachCount()
        .filterValues { it > 1 }.keys
    if (porQuestao.isNotEmpty()) {
        problems += "questoes com mais de uma regiao discursiva: ${porQuestao.sorted().joinToString()}"
    }

    checkInkBudget(problems)

    for (first in regions.indices) {
        for (second in first + 1 until regions.size) {
            val a = regions[first]
            val b = regions[second]
            if (a.page == b.page && overlaps(a, b)) {
                problems += "regioes ${a.index} e ${b.index} se sobrepoem na pagina ${a.page}"
            }
        }
    }

    return if (problems.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(problems)
}

/**
 * A regiao discursiva declara a questao, marcadores que existem na pagina e a area de resposta, a
 * area nao cobre o QR, e a pauta dentro dela e decoracao.
 *
 * A area e o que a captura vai recortar (§8). Fora de `[0,1]` ela recortaria alem dos marcadores, e
 * sobre o QR ela entregaria o codigo como se fosse escrita do aluno. Bolha numa regiao discursiva e
 * questao lida por OMR onde a spec diz que nao ha OMR.
 */
private fun checkEssayRegion(
    region: ScannableRegion,
    primitivas: List<Primitive>,
    problems: MutableList<String>,
) {
    // O marcador declarado e o que a captura procura na foto; um que nao foi desenhado e uma regiao
    // que nunca fecha, e a folha so seria descoberta assim na sala de aula.
    val desenhados = primitivas.filterIsInstance<DrawAruco>().map { it.markerId }.toSet()
    for (marcador in region.markerIds.filterNot { it in desenhados }) {
        problems += "regiao ${region.index} declara o marcador $marcador, que nao esta entre as " +
            "primitivas da pagina ${region.page}"
    }
    if (region.questionId == null) {
        problems += "regiao ${region.index} e discursiva e nao declara questao"
    }
    if (region.bubbles.isNotEmpty()) {
        problems += "regiao ${region.index} e discursiva e declara ${region.bubbles.size} bolha(s)"
    }
    val area = region.answerArea
    if (area == null) {
        problems += "regiao ${region.index} e discursiva e nao declara area de resposta"
        return
    }
    val dentro = Ppm(area.u).isInUnitRange && Ppm(area.v).isInUnitRange &&
        Ppm(area.u + area.uSize).isInUnitRange && Ppm(area.v + area.vSize).isInUnitRange
    if (!dentro) {
        problems += "a area de resposta da regiao ${region.index} sai do quadrilatero: " +
            "u=${area.u}+${area.uSize}, v=${area.v}+${area.vSize}"
    }
    val qr = region.qr
    val cruza = area.u < qr.u + qr.uSize && qr.u < area.u + area.uSize &&
        area.v < qr.v + qr.vSize && qr.v < area.v + area.vSize
    if (cruza) {
        problems += "a area de resposta da regiao ${region.index} sobrepoe o QR da regiao"
    }
    checkPauta(region, area, primitivas, problems)
}

/**
 * A linha dentro da area de resposta e pauta, e a pauta e decoracao (ADR-0016): tom declarado, e
 * abaixo do teto decorativo da regiao (ADR-0010).
 *
 * Sem tom ela e preta, e preta ela disputa a leitura com a letra do aluno e entra na medicao de
 * geometria da paridade, que poe o piso de escuridao no teto decorativo. No teto ou acima dele, pelo
 * mesmo motivo: "tinta que a paridade enxerga nao e decoracao, e geometria".
 */
private fun checkPauta(
    region: ScannableRegion,
    area: NormalizedRect,
    primitivas: List<Primitive>,
    problems: MutableList<String>,
) {
    val esquerda = region.quadX + scale(area.u, region.quadWidth)
    val direita = region.quadX + scale(area.u + area.uSize, region.quadWidth)
    val topo = region.quadY + scale(area.v, region.quadHeight)
    val base = region.quadY + scale(area.v + area.vSize, region.quadHeight)
    val teto = region.inkBudget.decorativeToneMax

    for (linha in primitivas.filterIsInstance<DrawLine>()) {
        val alcanca = minOf(linha.x1, linha.x2) <= direita && maxOf(linha.x1, linha.x2) >= esquerda &&
            minOf(linha.y1, linha.y2) <= base && maxOf(linha.y1, linha.y2) >= topo
        if (!alcanca) continue
        val tom = linha.tone
        if (tom == null) {
            problems += "regiao ${region.index}: a linha `${linha.id}` dentro da area de resposta nao " +
                "declara tom, e sai preta; a pauta e decoracao, abaixo do teto de $teto por mil"
        } else if (tom >= teto) {
            problems += "regiao ${region.index}: a linha `${linha.id}` dentro da area de resposta tem " +
                "tom $tom, e a pauta precisa ficar abaixo do teto decorativo de $teto por mil"
        }
    }
}

/**
 * O que a validacao consegue **provar** sobre tinta decorativa dentro das bolhas (ADR-0010,
 * D-2b.3.1).
 *
 * Ela nao rasteriza, e o programa de fonte desta base le avanco e espacamento — nao o contorno do
 * glifo. O unico limite superior que daria para calcular para uma letra e "a caixa inteira e
 * tinta", e medido na folha de referencia esse limite da 20,4% para bolhas cuja tinta real e 7,2%:
 * recusaria a folha que o raster aprova. Quem julga cobertura e o documento rasterizado.
 *
 * O que sobra aqui e exato e barato, e pega o erro grosso:
 *
 * - **trama chapada** sobre uma bolha contribui exatamente com o proprio valor, entao trama acima
 *   do orcamento estoura o orcamento sozinha, sem estimativa nenhuma;
 * - **elemento decorativo opaco** dentro de uma bolha e defeito qualquer que seja a area — preto
 *   pleno dentro do disco que o OMR mede nao e decoracao.
 */
private fun LayoutMap.checkInkBudget(problems: MutableList<String>) {
    for (region in regions) {
        val budget = region.inkBudget
        if (budget.decorativeMax !in 0..LayoutMap.TONE_FULL ||
            budget.decorativeToneMax !in 0..LayoutMap.TONE_FULL ||
            budget.thresholdFloor !in 0..LayoutMap.TONE_FULL ||
            budget.thresholdCeiling !in 0..LayoutMap.TONE_FULL ||
            budget.thresholdFloor >= budget.thresholdCeiling
        ) {
            problems += "regiao ${region.index} declara orcamento de tinta incoerente: $budget"
            continue
        }
        if (budget.decorativeMax >= budget.thresholdFloor) {
            problems += "regiao ${region.index}: o orcamento decorativo " +
                "(${budget.decorativeMax}) invade o corredor do limiar, que comeca em " +
                "${budget.thresholdFloor}"
        }

        val page = pages.firstOrNull { it.index == region.page } ?: continue
        val radius = Um(bubbleRadiusOf(page))
        if (radius <= Um.ZERO) continue

        for (bubble in region.bubbles) {
            val centerX = Um(region.quadX + scale(bubble.u, region.quadWidth))
            val centerY = Um(region.quadY + scale(bubble.v, region.quadHeight))

            for (primitive in page.primitives) {
                when (primitive) {
                    is DrawRect -> {
                        val fill = primitive.fill ?: continue
                        if (!touchesBubble(primitive, centerX, centerY, radius)) continue
                        if (fill > budget.decorativeMax) {
                            problems += "bolha ${bubble.questionId}/${bubble.option} recebe a " +
                                "trama `${primitive.id}` de $fill por mil, acima do orcamento " +
                                "decorativo de ${budget.decorativeMax}"
                        }
                    }

                    is DrawText -> {
                        if (!touchesBubble(primitive, centerX, centerY, radius)) continue
                        val tone = primitive.tone
                        if (tone == null) {
                            problems += "bolha ${bubble.questionId}/${bubble.option} tem o texto " +
                                "`${primitive.id}` em preto pleno dentro dela"
                        } else if (tone > budget.decorativeToneMax) {
                            problems += "bolha ${bubble.questionId}/${bubble.option} tem o texto " +
                                "`${primitive.id}` com tom $tone, acima do teto decorativo de " +
                                "${budget.decorativeToneMax}"
                        }
                    }

                    else -> Unit
                }
            }
        }
    }
}

/** Raio da bolha desenhada na pagina, para saber o que cai dentro dela. */
private fun bubbleRadiusOf(page: Page): Int =
    page.primitives.filterIsInstance<DrawCircle>().minOfOrNull { it.diameter / 2 } ?: 0

/** Desnormaliza uma coordenada em ppm de volta para micrometros sobre o lado do quadrilatero. */
private fun scale(ppm: Int, extent: Int): Int =
    ((ppm.toLong() * extent.toLong() + Ppm.ONE.raw / 2) / Ppm.ONE.raw).toInt()

/**
 * Verdadeiro quando a caixa da primitiva alcanca o disco da bolha.
 *
 * Para texto a caixa e a do avanco pelo corpo, generosa de proposito: aqui interessa **nao deixar
 * passar** um elemento que caia dentro da bolha, e nao medir o quanto ele ocupa.
 */
private fun touchesBubble(primitive: Primitive, centerX: Um, centerY: Um, radius: Um): Boolean {
    val (left, top, right, bottom) = when (primitive) {
        is DrawRect -> Quad(
            primitive.x,
            primitive.y,
            primitive.x + primitive.width,
            primitive.y + primitive.height,
        )

        is DrawText -> Quad(
            primitive.x,
            primitive.baseline - primitive.size,
            // Sem medir texto aqui: o avanco maximo de um glifo cabe no corpo com folga, e uma
            // caixa larga demais so torna a guarda mais conservadora.
            primitive.x + primitive.size * primitive.text.length,
            primitive.baseline + primitive.size / 4,
        )

        else -> return false
    }
    val overlapsHorizontally =
        left <= (centerX + radius).raw && right >= (centerX - radius).raw
    val overlapsVertically =
        top <= (centerY + radius).raw && bottom >= (centerY - radius).raw
    return overlapsHorizontally && overlapsVertically
}

private data class Quad(val left: Int, val top: Int, val right: Int, val bottom: Int)

private fun overlaps(a: ScannableRegion, b: ScannableRegion): Boolean {
    val separatedHorizontally =
        a.quadX + a.quadWidth <= b.quadX || b.quadX + b.quadWidth <= a.quadX
    val separatedVertically =
        a.quadY + a.quadHeight <= b.quadY || b.quadY + b.quadHeight <= a.quadY
    return !separatedHorizontally && !separatedVertically
}
