package com.platos.domain.layout

import com.platos.domain.geometry.Ppm

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
        if (region.markerIds.size != 4 || region.markerIds.toSet().size != 4) {
            problems += "regiao ${region.index} precisa de quatro marcadores distintos, veio " +
                "${region.markerIds}"
        }
        val expected = (4 * region.index)..(4 * region.index + 3)
        if (region.markerIds.sorted() != expected.toList()) {
            problems += "regiao ${region.index} deveria usar os marcadores " +
                "${expected.toList()}, veio ${region.markerIds}"
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
    }

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

private fun overlaps(a: ScannableRegion, b: ScannableRegion): Boolean {
    val separatedHorizontally =
        a.quadX + a.quadWidth <= b.quadX || b.quadX + b.quadWidth <= a.quadX
    val separatedVertically =
        a.quadY + a.quadHeight <= b.quadY || b.quadY + b.quadHeight <= a.quadY
    return !separatedHorizontally && !separatedVertically
}
