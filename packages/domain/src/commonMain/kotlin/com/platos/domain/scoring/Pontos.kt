package com.platos.domain.scoring

import kotlin.jvm.JvmInline

private val NEGATIVA = Regex("""-\d+(\.\d+)?""")
private val MUITAS_CASAS = Regex("""\d+\.\d{3,}""")
private val VALIDA = Regex("""(\d{1,6})(?:\.(\d{1,2}))?""")

/**
 * A pontuacao que o professor da a uma discursiva: decimal **exato**, ate 2 casas, de 0 a 999999.99
 * (`slice-5c-2-a-nota-do-professor`, ADR-0021).
 *
 * **Centesimos em `Long`, e nao `Double`.** Com ponto flutuante binario, `0.1 + 0.2 != 0.3` quebraria
 * "a soma por questao e igual ao total" e a idempotencia do reenvio. O `commonMain` nao tem
 * `BigDecimal` e o dominio compila para JVM, Android e JS: inteiro em centesimos e exato nos tres.
 *
 * **O alcance (999999.99) e o do `numeric(8,2)` do banco.** O `numeric(8,2)` arredonda tres casas e
 * **estoura** acima disto com erro de SQL; as duas coisas teriam de ser recusadas aqui, antes do SQL,
 * para virar 400 com mensagem e nao 500.
 *
 * **Nao e `@Serializable`**: no fio a pontuacao viaja como `String` ("1.75"), e o servidor a le com
 * [parse], que da o motivo com o nome do item. Um serializador sem consumidor seria P18.
 */
@JvmInline
value class Pontos private constructor(val centesimos: Long) : Comparable<Pontos> {

    operator fun plus(outro: Pontos): Pontos = Pontos(centesimos + outro.centesimos)

    override fun compareTo(other: Pontos): Int = centesimos.compareTo(other.centesimos)

    /** A forma canonica: sempre duas casas. `1.5` e `1.50` sao o mesmo valor e saem `1.50`. */
    override fun toString(): String {
        val fracao = (centesimos % 100).toString().padStart(2, '0')
        return "${centesimos / 100}.$fracao"
    }

    companion object {
        val ZERO: Pontos = Pontos(0L)

        /** Uma pontuacao inteira, como as do pacote. */
        fun inteiros(valor: Int): Pontos {
            require(valor in 0..999_999) { "pontuacao inteira $valor fora de 0..999999" }
            return Pontos(valor * 100L)
        }

        /**
         * Le uma pontuacao escrita com **ponto** decimal e ate 2 casas.
         *
         * Tres recusas distintas, para a mensagem dizer o que nao fecha: negativa, mais de 2 casas
         * (recusada, **nunca arredondada**) e "nao e uma pontuacao" (inclui a virgula decimal).
         */
        fun parse(texto: String): Pontos {
            require(!NEGATIVA.matches(texto)) { "pontuacao negativa: '$texto'" }
            require(!MUITAS_CASAS.matches(texto)) { "pontuacao com mais de 2 casas decimais: '$texto'" }
            val partes = VALIDA.matchEntire(texto) ?: throw IllegalArgumentException(
                "'$texto' nao e uma pontuacao: use numero com ponto decimal e ate 2 casas, " +
                    "de 0 ate 999999.99",
            )
            val inteiro = partes.groupValues[1].toLong()
            val fracao = partes.groupValues[2]
            val centesimos = when (fracao.length) {
                0 -> 0
                1 -> fracao.toInt() * 10
                else -> fracao.toInt()
            }
            return Pontos(inteiro * 100 + centesimos)
        }
    }
}
