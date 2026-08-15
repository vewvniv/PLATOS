package com.platos.domain.geometry

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * D-1.2 e uma regra sobre o codigo, nao sobre um resultado — nenhum teste de valor a captura.
 *
 * Um `Double` introduzido em qualquer ponto do calculo continuaria passando em toda a suite na
 * JVM e so divergiria no alvo JS, possivelmente muito depois, ja com pacotes publicados e
 * hasheados. Entao a guarda varre o proprio fonte de `commonMain`: se alguem escrever ponto
 * flutuante ali, este teste falha apontando arquivo e linha.
 *
 * Roda so na JVM porque precisa do sistema de arquivos; o que ele protege vale para os tres alvos.
 */
class IntegerArithmeticGuardTest {

    private val forbidden = Regex("""\b(Float|Double)\b|\b\d+\.\d+[fF]?\b""")

    /**
     * Remove o que nao e codigo antes de procurar: comentario de linha, corpo de KDoc e literal
     * de string. Sem isso a guarda acusa a propria documentacao — "D-1.2" e um numero com ponto —
     * e uma guarda que grita a toa e desligada na primeira semana.
     */
    private fun codeOf(line: String): String {
        val trimmed = line.trim()
        if (trimmed.startsWith("*") || trimmed.startsWith("/*") || trimmed.startsWith("//")) return ""
        return line.substringBefore("//").replace(Regex("\"[^\"]*\""), "\"\"")
    }

    @Test
    fun `nenhum ponto flutuante no codigo de calculo`() {
        val root = File(
            System.getProperty("platos.domain.commonMain")
                ?: fail("propriedade `platos.domain.commonMain` nao definida pelo build"),
        )
        assertTrue(root.isDirectory, "fonte de commonMain nao encontrado em $root")

        val offences = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                file.readLines().asSequence().withIndex().mapNotNull { (index, line) ->
                    val match = forbidden.find(codeOf(line)) ?: return@mapNotNull null
                    "${file.name}:${index + 1}: ${match.value} em `${line.trim()}`"
                }
            }
            .toList()

        assertTrue(
            offences.isEmpty(),
            "ponto flutuante no calculo de layout (D-1.2):\n" + offences.joinToString("\n"),
        )
    }

    @Test
    fun `a guarda reconhece ponto flutuante quando ele existe`() {
        // Sem esta assercao, um regex quebrado deixaria a guarda passar para sempre em silencio.
        val amostras = listOf(
            "val escala: Double = 1",
            "val fator = 2.5",
            "fun medir(x: Float): Int",
            "val f = 0.3f",
        )
        for (amostra in amostras) {
            assertTrue(forbidden.containsMatchIn(codeOf(amostra)), "guarda nao pegou: `$amostra`")
        }
        assertTrue(forbidden.find(codeOf("val passo = Um(4_200)")) == null)
        assertTrue(forbidden.find(codeOf("val total: Long = units * size")) == null)
        // E precisa ignorar documentacao e literal de string, senao vira ruido.
        assertTrue(forbidden.find(codeOf(" * D-1.2: aritmetica inteira")) == null)
        assertTrue(forbidden.find(codeOf("""const val V: String = "Version 4.005"""")) == null)
    }
}
