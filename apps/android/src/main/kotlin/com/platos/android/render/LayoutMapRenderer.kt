package com.platos.android.render

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.platos.domain.layout.DrawAruco
import com.platos.domain.layout.DrawCircle
import com.platos.domain.layout.DrawImage
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
class LayoutMapRenderer(private val typeface: Typeface) {

    fun render(map: LayoutMap, output: OutputStream) {
        RendererContract.assertSupports(map)

        val document = PdfDocument()
        val widthPt = RendererContract.pagePoints(map.pageWidth)
        val heightPt = RendererContract.pagePoints(map.pageHeight)

        try {
            for (source in map.pages.sortedBy { it.index }) {
                val info = PdfDocument.PageInfo.Builder(widthPt, heightPt, source.index + 1).create()
                val page = document.startPage(info)
                for (primitive in source.primitives) {
                    draw(page.canvas, primitive)
                }
                document.finishPage(page)
            }
            document.writeTo(output)
        } finally {
            document.close()
        }
    }

    private fun draw(canvas: Canvas, primitive: Primitive) {
        RendererContract.assertDrawable(primitive)
        when (primitive) {
            is DrawRect -> canvas.drawRect(
                pt(primitive.x),
                pt(primitive.y),
                pt(primitive.x + primitive.width),
                pt(primitive.y + primitive.height),
                strokePaint(primitive.stroke),
            )

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
                textPaint(primitive.size),
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

            // `assertDrawable` ja recusou este caso antes do `when`; o ramo existe porque o tipo
            // e selado e o compilador exige exaustividade.
            is DrawImage -> throw UnknownPrimitiveException(
                "primitiva `image` (${primitive.id}) nao e desenhada nesta fatia",
            )
        }
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

    private fun textPaint(sizeUm: Int) = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        color = android.graphics.Color.BLACK
        typeface = this@LayoutMapRenderer.typeface
        textSize = pt(sizeUm)
    }
}
