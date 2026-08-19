package com.platos.build

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.security.MessageDigest
import java.util.Base64

/**
 * Embute os rasters de formula como constantes Kotlin em `commonTest` (D-1.5.5).
 *
 * Mesma razao de [EmbedFontTask], e mesma exigencia: **os dois renderizadores precisam desenhar os
 * mesmos bytes**, porque e disso que D-1.5.1 depende inteiramente. Se cada lado resolvesse a
 * referencia por conta propria — um lendo do disco, outro de assets —, a igualdade por construcao
 * viraria igualdade por coincidencia de configuracao.
 *
 * O PNG versionado em `fixtures/formulas/` continua sendo a fonte da verdade; isto aqui e
 * transporte, e o `sha256` gerado junto e o que permite a um teste comum afirmar, nos tres alvos,
 * que os bytes sao os que o manifesto declara.
 */
abstract class EmbedRastersTask : DefaultTask() {

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
            appendLine("// Gerado por EmbedRastersTask a partir de fixtures/formulas/. Nao edite a mao.")
            appendLine("@file:OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)")
            appendLine()
            appendLine("package $pkg")
            appendLine()
            appendLine("import kotlin.io.encoding.Base64")
            appendLine()
            appendLine("/** Rasters de formula embutidos, identicos nos tres alvos por construcao. */")
            appendLine("object FormulaRasters {")
            appendLine()

            val ids = mutableListOf<String>()
            for (file in entries) {
                val id = file.nameWithoutExtension
                val constant = id.replace(Regex("[^A-Za-z0-9]"), "_").uppercase()
                ids += id
                val bytes = file.readBytes()
                val sha256 = MessageDigest.getInstance("SHA-256").digest(bytes)
                    .joinToString("") { byte ->
                        ((byte.toInt() and 0xFF) + 0x100).toString(16).substring(1)
                    }
                val chunks = Base64.getEncoder().encodeToString(bytes).chunked(CHUNK_SIZE)

                // Uma constante por pedaco: o pool de constantes da JVM limita cada UTF-8 a 65535
                // bytes.
                chunks.forEachIndexed { index, chunk ->
                    appendLine("    private const val ${constant}_$index: String =")
                    appendLine("        \"$chunk\"")
                }
                appendLine("    private const val ${constant}_SHA256: String = \"$sha256\"")
                appendLine()
            }

            appendLine("    /** Identificadores na ordem em que estao versionados. */")
            appendLine("    val ids: List<String> = listOf(")
            for (id in ids) appendLine("        \"$id\",")
            appendLine("    )")
            appendLine()
            appendLine("    /** Bytes do PNG de [id], ou nulo se ele nao existir.  */")
            appendLine("    fun bytesOf(id: String): ByteArray? = when (id) {")
            for (file in entries) {
                val id = file.nameWithoutExtension
                val constant = id.replace(Regex("[^A-Za-z0-9]"), "_").uppercase()
                val parts = Base64.getEncoder().encodeToString(file.readBytes())
                    .chunked(CHUNK_SIZE).indices
                    .joinToString(" + ") { "${constant}_$it" }
                appendLine("        \"$id\" -> Base64.decode($parts)")
            }
            appendLine("        else -> null")
            appendLine("    }")
            appendLine()
            appendLine("    /** `sha256` do PNG de [id], ou nulo se ele nao existir. */")
            appendLine("    fun sha256Of(id: String): String? = when (id) {")
            for (file in entries) {
                val id = file.nameWithoutExtension
                val constant = id.replace(Regex("[^A-Za-z0-9]"), "_").uppercase()
                appendLine("        \"$id\" -> ${constant}_SHA256")
            }
            appendLine("        else -> null")
            appendLine("    }")
            appendLine("}")
        }

        outDir.resolve("FormulaRasters.kt").writeText(source)
        logger.lifecycle(
            "Rasters embutidos: ${entries.size} formulas, " +
                "${entries.sumOf { it.length() }} bytes",
        )
    }

    private companion object {
        const val CHUNK_SIZE = 32_768
    }
}
