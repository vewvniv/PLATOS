package com.platos.domain.layout

import com.platos.domain.geometry.Um
import com.platos.domain.text.TextMeasurer
import com.platos.domain.text.TextStyle

internal data class InkBox(val left: Int, val top: Int, val right: Int, val bottom: Int)

/**
 * Caixa que a tinta de uma primitiva pode ocupar, em micrometros.
 *
 * Para texto ela e **generosa de proposito**: a altura de maiuscula e menor que o corpo e a
 * descendente e menor que um quarto dele, entao a caixa cobre mais do que a tinta real. Uma caixa
 * justa passaria a mao na primeira invasao de zona de silencio, que e o que ela existe para pegar.
 *
 * Fica em arquivo proprio porque a folha de teste de impressao precisa das mesmas afirmacoes que a
 * prova — zona de silencio livre, nada fora da pagina —, e duas copias da mesma regra divergem.
 */
internal fun inkBoxOf(primitive: Primitive, measurer: TextMeasurer): InkBox = when (primitive) {
    is DrawText -> InkBox(
        left = primitive.x,
        top = primitive.baseline - primitive.size,
        right = primitive.x + measurer.width(
            primitive.text,
            TextStyle(size = Um(primitive.size), lineHeight = Um(primitive.size)),
        ).raw,
        bottom = primitive.baseline + primitive.size / 4,
    )

    is DrawRect -> InkBox(
        left = primitive.x - primitive.stroke / 2,
        top = primitive.y - primitive.stroke / 2,
        right = primitive.x + primitive.width + primitive.stroke / 2,
        bottom = primitive.y + primitive.height + primitive.stroke / 2,
    )

    is DrawCircle -> {
        val radius = primitive.diameter / 2 + primitive.stroke / 2
        InkBox(
            left = primitive.centerX - radius,
            top = primitive.centerY - radius,
            right = primitive.centerX + radius,
            bottom = primitive.centerY + radius,
        )
    }

    is DrawImage -> InkBox(
        left = primitive.x,
        top = primitive.y,
        right = primitive.x + primitive.width,
        bottom = primitive.y + primitive.height,
    )

    is DrawAruco -> InkBox(
        left = primitive.x,
        top = primitive.y,
        right = primitive.x + primitive.side,
        bottom = primitive.y + primitive.side,
    )

    is DrawQr -> InkBox(
        left = primitive.x,
        top = primitive.y,
        right = primitive.x + primitive.side,
        bottom = primitive.y + primitive.side,
    )
}

/** Verdadeiro quando a tinta de [primitive] respeita a zona de silencio de [aruco]. */
internal fun clearsQuietZone(
    primitive: Primitive,
    aruco: DrawAruco,
    quiet: Int,
    measurer: TextMeasurer,
): Boolean {
    if (primitive === aruco) return true
    val box = inkBoxOf(primitive, measurer)
    val separatedHorizontally =
        box.right <= aruco.x - quiet || box.left >= aruco.x + aruco.side + quiet
    val separatedVertically =
        box.bottom <= aruco.y - quiet || box.top >= aruco.y + aruco.side + quiet
    return separatedHorizontally || separatedVertically
}
