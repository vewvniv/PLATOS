package com.platos.android.render

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.platos.domain.layout.DrawAruco
import com.platos.domain.layout.DrawCircle
import com.platos.domain.layout.DrawImage
import com.platos.domain.layout.DrawLine
import com.platos.domain.layout.DrawQr
import com.platos.domain.layout.DrawRect
import com.platos.domain.layout.DrawText
import com.platos.domain.layout.LayoutMap
import com.platos.domain.layout.Primitive
import java.io.OutputStream

/**
 * Renderizador Android: primitiva do `LayoutMap` -> `Canvas` de um `PdfDocument`.
 *
 * Nao mede texto, nao quebra linha e nao pagina. Toda posicao ja veio pronta do KMP (§6); aqui so
 * ha conversao de unidade e chamada de API.
 *
 * O eixo vertical do `Canvas` desce, igual ao do `LayoutMap`, entao — ao contrario do lado web,
 * que desenha em PDF de origem inferior — nao ha inversao de Y.
 */
class LayoutMapRenderer(
    private val typeface: Typeface,
    /**
     * Referencia do `LayoutMap` -> bytes do raster (D-1.5.5).
     *
     * Quem resolve a referencia e quem chama, e nao o renderizador: sao **os mesmos bytes** que vao
     * para o lado web, e e disso que a paridade da formula depende. Se cada renderizador procurasse
     * o arquivo do seu jeito, a igualdade por construcao viraria coincidencia de configuracao.
     */
    private val imageBytes: Map<String, ByteArray> = emptyMap(),
) {

    // Decodifica uma vez por referencia, e nao por ocorrencia: o mesmo raster em duas questoes nao
    // precisa de dois bitmaps.
    private val bitmaps = mutableMapOf<String, Bitmap>()

    fun render(map: LayoutMap, output: OutputStream) {
        RendererContract.assertSupports(map)

        val document = PdfDocument()
        val widthPt = RendererContract.pagePoints(map.pageWidth)
        val heightPt = RendererContract.pagePoints(map.pageHeight)

        try {
            for (source in map.pages.sortedBy { it.index }) {
                val info = PdfDocument.PageInfo.Builder(widthPt, heightPt, source.index + 1).create()
                val page = document.startPage(info)
                try {
                    for (primitive in source.primitives) {
                        draw(page.canvas, primitive)
                    }
                } finally {
                    // Fecha a pagina mesmo quando o desenho falha. Sem isto, `close()` no `finally`
                    // de baixo lanca "Current page not finished!" e essa excecao **substitui** a
                    // que diz o que realmente aconteceu — quem chamou receberia um erro de estado
                    // do `PdfDocument` no lugar de "faltam os bytes da formula tal".
                    document.finishPage(page)
                }
            }
            // So depois de todas as paginas: uma falha no meio nao pode deixar meio documento
            // escrito no destino.
            document.writeTo(output)
        } finally {
            document.close()
        }
    }

    private fun draw(canvas: Canvas, primitive: Primitive) {
        RendererContract.assertDrawable(primitive) { it in imageBytes }
        when (primitive) {
            is DrawRect -> {
                // A trama vai primeiro e o traco por cima, para o contorno nao ser comido pelo
                // preenchimento. Ate a fatia 2b este ramo ignorava `fill` por completo: o web
                // preenchia, o Android nao, e a paridade nao tinha como ver — ela compara
                // centroides de marcador e bolha, e faixa ausente nao move nenhum deles.
                primitive.fill?.let { fill ->
                    canvas.drawRect(
                        pt(primitive.x),
                        pt(primitive.y),
                        pt(primitive.x + primitive.width),
                        pt(primitive.y + primitive.height),
                        fillPaint(fill),
                    )
                }
                // Traco zero e **sem traco**: `strokeWidth = 0` no Android desenha hairline de um
                // pixel, que poria contorno em volta de uma faixa que deveria ser so trama.
                if (primitive.stroke > 0) {
                    canvas.drawRect(
                        pt(primitive.x),
                        pt(primitive.y),
                        pt(primitive.x + primitive.width),
                        pt(primitive.y + primitive.height),
                        strokePaint(primitive.stroke),
                    )
                }
            }

            is DrawCircle -> canvas.drawCircle(
                pt(primitive.centerX),
                pt(primitive.centerY),
                pt(primitive.diameter) / 2f,
                strokePaint(primitive.stroke),
            )

            is DrawText -> canvas.drawText(
                primitive.text,
                pt(primitive.x),
                pt(primitive.baseline),
                textPaint(primitive.size, primitive.tone),
            )

            is DrawAruco -> drawModuleGrid(
                canvas,
                primitive.x,
                primitive.y,
                primitive.module,
                primitive.modules,
            )

            is DrawQr -> drawModuleGrid(
                canvas,
                primitive.x,
                primitive.y,
                primitive.module,
                primitive.modules,
            )

            is DrawImage -> canvas.drawBitmap(
                bitmapOf(primitive.reference),
                null,
                // A caixa vem do mapa e o raster foi gerado exatamente nela (D-1.5.1). O destino
                // usa a mesma conversao de unidade dos demais elementos, entao a formula cai na
                // grade de pontos do documento junto com o resto da folha.
                RectF(
                    pt(primitive.x),
                    pt(primitive.y),
                    pt(primitive.x + primitive.width),
                    pt(primitive.y + primitive.height),
                ),
                imagePaint,
            )

            is DrawLine -> throw UnknownPrimitiveException(
                "primitiva `line` (${primitive.id}) ainda nao e desenhada por este renderizador; " +
                    "nenhum documento parcial e entregue",
            )
        }
    }

    private fun bitmapOf(reference: String): Bitmap = bitmaps.getOrPut(reference) {
        // `assertDrawable` ja garantiu que a referencia existe; o que pode falhar aqui e o PNG
        // estar corrompido, e isso tambem nao pode virar pagina sem a formula.
        val bytes = imageBytes.getValue(reference)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: throw MissingResourceException(
                "o recurso `$reference` tem ${bytes.size} bytes que nao decodificam como imagem; " +
                    "nenhum documento parcial e entregue",
            )
    }

    /** Um retangulo por modulo preto: o padrao ja veio resolvido no mapa (D-1.10). */
    private fun drawModuleGrid(
        canvas: Canvas,
        x: Int,
        y: Int,
        moduleUm: Int,
        modules: List<String>,
    ) {
        val paint = Paint().apply {
            isAntiAlias = false
            style = Paint.Style.FILL
            color = android.graphics.Color.BLACK
        }
        modules.forEachIndexed { rowIndex, row ->
            for (column in row.indices) {
                if (row[column] != '1') continue
                canvas.drawRect(
                    pt(x + column * moduleUm),
                    pt(y + rowIndex * moduleUm),
                    pt(x + (column + 1) * moduleUm),
                    pt(y + (rowIndex + 1) * moduleUm),
                    paint,
                )
            }
        }
    }

    private fun pt(um: Int): Float = RendererContract.umToPt(um).toFloat()

    private fun strokePaint(strokeUm: Int) = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = pt(strokeUm)
        color = android.graphics.Color.BLACK
    }

    /**
     * Sem filtragem e sem dithering.
     *
     * O raster ja foi gerado no tamanho exato da caixa, entao nao ha reamostragem a suavizar — e
     * ligar o filtro faria o Android decidir sozinho pixels que o lado web nao decide, que e
     * justamente a divergencia que a fatia existe para eliminar.
     */
    private val imagePaint = Paint().apply {
        isAntiAlias = false
        isFilterBitmap = false
        isDither = false
    }

    private fun textPaint(sizeUm: Int, tone: Int?) = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        color = grayOf(tone)
        typeface = this@LayoutMapRenderer.typeface
        textSize = pt(sizeUm)
    }

    private fun fillPaint(permille: Int) = Paint().apply {
        isAntiAlias = false
        style = Paint.Style.FILL
        color = grayOf(permille)
    }

    /**
     * Permilagem de preto -> cinza de 8 bits (D-2b.1).
     *
     * Nulo e preto pleno, como o contrato declara. O renderizador nao escolhe tom: ele converte o
     * que o mapa mandou, e a conversao e a mesma do `grayOf` do web.
     */
    private fun grayOf(permille: Int?): Int {
        if (permille == null) return android.graphics.Color.BLACK
        val level = ((1000 - permille) * 255 + 500) / 1000
        return android.graphics.Color.rgb(level, level, level)
    }
}
