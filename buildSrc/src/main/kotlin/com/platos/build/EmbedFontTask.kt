package com.platos.build

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.security.MessageDigest
import java.util.Base64

/**
 * Gera uma fonte Kotlin em `commonMain` contendo os bytes do TTF embarcado.
 *
 * D-1.1 exige que nada do calculo seja codigo de plataforma, e D36 exige fonte embarcada. Ler um
 * recurso e a unica parte disso que o KMP nao resolve em `common`: JVM le do classpath, Android de
 * assets e JS nao tem sistema de arquivos no navegador. Passar os bytes por codigo gerado mantem a
 * medicao inteiramente comum e garante, por construcao, que os tres alvos leem os mesmos bytes.
 *
 * Mesmo padrao ja usado por [GenerateJooqTask]: dado derivado nasce do artefato versionado, nunca
 * e mantido a mao.
 */
abstract class EmbedFontTask : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val fontFile: RegularFileProperty

    @get:Input
    abstract val packageName: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val font = fontFile.get().asFile
        val bytes = font.readBytes()
        val sha256 = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { byte -> ((byte.toInt() and 0xFF) + 0x100).toString(16).substring(1) }
        val base64 = Base64.getEncoder().encodeToString(bytes)
        val chunks = base64.chunked(CHUNK_SIZE)

        val pkg = packageName.get()
        val outDir = outputDir.get().asFile.resolve(pkg.replace('.', '/'))
        outDir.mkdirs()

        val source = buildString {
            appendLine("// Gerado por EmbedFontTask a partir de ${font.name}. Nao edite a mao.")
            appendLine("@file:OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)")
            appendLine()
            appendLine("package $pkg")
            appendLine()
            appendLine("import kotlin.io.encoding.Base64")
            appendLine()
            appendLine("/** Bytes do TTF embarcado, identicos nos tres alvos por construcao. */")
            appendLine("internal object EmbeddedFontBytes {")
            appendLine("    const val FILE_NAME: String = \"${font.name}\"")
            appendLine("    const val SHA256: String = \"$sha256\"")
            appendLine("    const val SIZE_BYTES: Int = ${bytes.size}")
            appendLine()
            // Uma constante por pedaco: o pool de constantes da JVM limita cada UTF-8 a 65535 bytes.
            chunks.forEachIndexed { index, chunk ->
                appendLine("    private const val PART_$index: String =")
                appendLine("        \"$chunk\"")
            }
            appendLine()
            appendLine("    fun bytes(): ByteArray = Base64.decode(")
            appendLine(chunks.indices.joinToString(" +\n") { "        PART_$it" },)
            appendLine("    )")
            appendLine("}")
        }

        outDir.resolve("EmbeddedFontBytes.kt").writeText(source)
        logger.lifecycle("Fonte embarcada: ${font.name}, ${bytes.size} bytes, sha256 $sha256")
    }

    private companion object {
        const val CHUNK_SIZE = 32_768
    }
}
