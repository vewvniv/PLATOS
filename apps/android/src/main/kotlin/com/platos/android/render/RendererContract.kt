package com.platos.android.render

import com.platos.domain.layout.DrawImage
import com.platos.domain.layout.LayoutMap
import com.platos.domain.layout.Primitive

/** O mapa exige um renderizador mais novo que este (D24). */
class RendererVersionException(message: String) : IllegalStateException(message)

/** O mapa traz uma primitiva que este renderizador nao desenha. */
class UnknownPrimitiveException(message: String) : IllegalStateException(message)

/** O mapa referencia um recurso cujos bytes nao foram fornecidos. */
class MissingResourceException(message: String) : IllegalStateException(message)

/**
 * A parte do renderizador que nao toca `android.graphics`.
 *
 * Fica separada de proposito: guarda de versao e conversao de unidade sao exatamente o que precisa
 * ser identico ao renderizador web, e aqui elas rodam em teste local de JVM, sem emulador.
 */
object RendererContract {

    /** Versao deste renderizador (D24). Espelha `RENDERER_VERSION` do lado web. */
    const val RENDERER_VERSION = 1

    /**
     * Recusa desenhar quando a versao exigida pelo mapa e maior que a deste renderizador.
     *
     * Nenhum documento parcial e produzido: um cliente desatualizado que imprimisse "quase certo"
     * geraria folha que o OMR nao le, e o erro so apareceria com a prova na mao.
     */
    fun assertSupports(map: LayoutMap) {
        if (map.minRendererVersion > RENDERER_VERSION) {
            throw RendererVersionException(
                "mapa exige renderizador versao ${map.minRendererVersion}, este e o " +
                    "$RENDERER_VERSION; atualize o aplicativo para imprimir esta prova",
            )
        }
    }

    /**
     * Recusa uma primitiva cujo desenho nao pode ser cumprido.
     *
     * Fica aqui, e nao dentro do `when` sobre o `Canvas`, para poder ser verificada em teste local
     * de JVM. A regra e a mesma do lado web: nada de pagina com o elemento omitido — um documento
     * silenciosamente incompleto e pior que uma falha, porque so aparece depois de impresso.
     *
     * A partir da fatia 1.5 `image` deixa de ser recusada por natureza e passa a ser recusada por
     * falta de bytes: [available] responde se a referencia foi fornecida.
     */
    fun assertDrawable(primitive: Primitive, available: (String) -> Boolean = { false }) {
        if (primitive is DrawImage && !available(primitive.reference)) {
            throw MissingResourceException(
                "imagem `${primitive.id}` referencia o recurso `${primitive.reference}`, que nao " +
                    "foi fornecido; nenhum documento parcial e entregue",
            )
        }
    }

    /**
     * Converte micrometros para pontos PostScript.
     *
     * A ordem das operacoes e contrato com o renderizador web, que faz `(um * 72) / 25400` na
     * mesma sequencia. Ambos usam IEEE 754 de 64 bits, entao a mesma ordem da o mesmo bit; trocar
     * por `um / 25400 * 72` faria a paridade acusar diferenca onde nao ha divergencia real.
     */
    fun umToPt(um: Int): Double = (um * 72.0) / 25400.0

    /**
     * Tamanho da pagina em pontos inteiros.
     *
     * O `PdfDocument` do Android so aceita inteiro. O lado web arredonda igual, para que a caixa
     * de pagina seja a mesma nos dois; o conteudo continua posicionado pela conversao exata, entao
     * as medidas impressas nao mudam.
     */
    fun pagePoints(um: Int): Int = kotlin.math.round(umToPt(um)).toInt()
}
