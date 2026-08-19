package com.platos.build

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Embute arquivos de texto versionados como constantes Kotlin em `commonTest`.
 *
 * Mesma razao de [EmbedFontTask]: o KMP nao tem API comum de recurso, e o alvo JS roda em Node sem
 * o mesmo sistema de arquivos do JVM. Como a fixture e o golden precisam ser lidos *pelos tres
 * alvos* para que a paridade de calculo signifique alguma coisa, eles entram por codigo gerado.
 *
 * Os arquivos continuam sendo a fonte da verdade no repositorio; isto aqui e so transporte.
 */
abstract class EmbedFixturesTask : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val files: ConfigurableFileCollection

    @get:Input
    abstract val packageName: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val pkg = packageName.get()
        val outDir = outputDir.get().asFile.resolve(pkg.replace('.', '/'))
        outDir.mkdirs()

        val entries = files.files.sortedBy { it.name }
        val source = buildString {
            appendLine("// Gerado por EmbedFixturesTask. Nao edite a mao.")
            appendLine()
            appendLine("package $pkg")
            appendLine()
            appendLine("/** Arquivos de `fixtures/` embutidos para leitura nos tres alvos. */")
            appendLine("object Fixtures {")
            for (file in entries) {
                val constant = file.name
                    .replace(Regex("[^A-Za-z0-9]"), "_")
                    .uppercase()
                val chunks = file.readText().chunked(CHUNK_SIZE)
                appendLine()
                // Juntado em tempo de execucao, e nao com `const val`.
                //
                // `const val` exige constante de compilacao, entao o compilador dobra a soma dos
                // pedacos num literal so — e o pool de constantes da JVM recusa qualquer UTF-8
                // acima de 65535 bytes. Quebrar em pedacos nao adiantava nada enquanto o todo
                // continuasse `const`: o golden passou de 65 KB ao ganhar formulas e o build caiu
                // com "UTF8 string too large". A lista impede a dobra.
                appendLine("    private val ${constant}_PARTS: List<String> = listOf(")
                for (chunk in chunks) {
                    appendLine("        \"${escape(chunk)}\",")
                }
                appendLine("    )")
                appendLine()
                appendLine("    val $constant: String = ${constant}_PARTS.joinToString(\"\")")
            }
            appendLine("}")
        }

        outDir.resolve("Fixtures.kt").writeText(source)
        logger.lifecycle("Fixtures embutidas: ${entries.joinToString { it.name }}")
    }

    private fun escape(text: String): String = buildString {
        for (char in text) {
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '$' -> append("\\$")
                else -> append(char)
            }
        }
    }

    private companion object {
        // Bem abaixo do limite de 65535 bytes por UTF-8 do pool de constantes da JVM, com folga
        // para o inchaco do escape.
        const val CHUNK_SIZE = 16_000
    }
}
