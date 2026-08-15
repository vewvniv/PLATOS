package com.platos.domain.geometry

import kotlin.jvm.JvmInline

/**
 * Comprimento em micrometros.
 *
 * D-1.2: toda a aritmetica de layout e inteira. `Float` em Kotlin/JS e emulado sobre o `number`
 * de 64 bits do JavaScript, entao a mesma expressao pode arredondar diferente de JVM e Android —
 * e a fatia 2 vai tirar hash do LayoutMap. Inteiro elimina a divergencia na origem e ainda torna
 * o mapa comparavel byte a byte, sem tolerancia.
 *
 * Toda divisao passa por um dos `div*` daqui, que declaram o arredondamento no nome. Nao existe
 * divisao implicita: `a / b` truncaria em direcao a zero sem ninguem notar.
 */
@JvmInline
value class Um(val raw: Int) : Comparable<Um> {

    operator fun plus(other: Um): Um = Um(raw + other.raw)

    operator fun minus(other: Um): Um = Um(raw - other.raw)

    operator fun times(factor: Int): Um = Um(raw * factor)

    operator fun unaryMinus(): Um = Um(-raw)

    override fun compareTo(other: Um): Int = raw.compareTo(other.raw)

    /** Divisao truncando para baixo (em direcao a -infinito, tambem para negativos). */
    fun divFloor(divisor: Int): Um = Um(floorDiv(raw, divisor))

    /** Divisao arredondando para cima (em direcao a +infinito, tambem para negativos). */
    fun divCeil(divisor: Int): Um = Um(-floorDiv(-raw, divisor))

    /** Menor multiplo de [step] maior ou igual a este comprimento. */
    fun ceilToMultipleOf(step: Um): Um {
        require(step.raw > 0) { "passo precisa ser positivo, veio ${step.raw}" }
        return Um(-floorDiv(-raw, step.raw) * step.raw)
    }

    /** Verdadeiro quando este comprimento e multiplo exato de [step]. */
    fun isMultipleOf(step: Um): Boolean {
        require(step.raw > 0) { "passo precisa ser positivo, veio ${step.raw}" }
        return raw % step.raw == 0
    }

    override fun toString(): String = "${raw}um"

    companion object {
        val ZERO = Um(0)

        /** Milimetros inteiros. */
        fun mm(value: Int): Um = Um(value * 1_000)

        /**
         * Milimetros com uma casa decimal, escritos como decimos de milimetro: 4,2 mm e `mmTenths(42)`.
         * Existe para que nenhuma medida do §7 precise passar por literal fracionario.
         */
        fun mmTenths(tenths: Int): Um = Um(tenths * 100)

        /** Centesimos de milimetro: 0,22 mm de traco e `mmHundredths(22)`. */
        fun mmHundredths(hundredths: Int): Um = Um(hundredths * 10)

        private fun floorDiv(dividend: Int, divisor: Int): Int {
            require(divisor != 0) { "divisao por zero" }
            var quotient = dividend / divisor
            if (dividend % divisor != 0 && (dividend xor divisor) < 0) {
                quotient -= 1
            }
            return quotient
        }
    }
}

/**
 * Coordenada normalizada ao quadrilatero de uma regiao escaneavel, em partes por milhao.
 *
 * A spec exige `(u,v) in [0,1]`. Guardar como inteiro em ppm representa esse intervalo de forma
 * exata e serializa sem numero fracionario, que e o que permite comparar mapas byte a byte.
 */
@JvmInline
value class Ppm(val raw: Int) : Comparable<Ppm> {

    override fun compareTo(other: Ppm): Int = raw.compareTo(other.raw)

    val isInUnitRange: Boolean get() = raw in 0..ONE.raw

    override fun toString(): String = "${raw}ppm"

    companion object {
        val ZERO = Ppm(0)
        val ONE = Ppm(1_000_000)

        /**
         * Projeta [offset] sobre um lado de comprimento [extent], arredondando ao ppm mais proximo
         * com desempate para cima. O arredondamento e declarado aqui e em nenhum outro lugar.
         */
        fun of(offset: Um, extent: Um): Ppm {
            require(extent.raw > 0) { "extensao precisa ser positiva, veio ${extent.raw}" }
            val scaled = offset.raw.toLong() * ONE.raw.toLong()
            val half = extent.raw.toLong() / 2
            val rounded = (scaled + half) / extent.raw.toLong()
            return Ppm(rounded.toInt())
        }
    }
}
