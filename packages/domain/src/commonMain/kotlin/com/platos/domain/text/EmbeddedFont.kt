package com.platos.domain.text

/**
 * A fonte embarcada do projeto (D36).
 *
 * Identidade verificada na carga: tamanho e versao declarada precisam bater com o esperado. Trocar
 * o TTF sem atualizar estas constantes derruba a carga com mensagem propria, em vez de mudar
 * silenciosamente a medicao — e, com ela, a geometria de toda folha ja impressa.
 */
object EmbeddedFont {

    const val EXPECTED_FAMILY: String = "Source Serif 4"
    const val EXPECTED_VERSION: String = "Version 4.005;hotconv 1.1.0;makeotfexe 2.6.0"

    /** Bytes do TTF, para o renderizador embarcar no documento. */
    fun bytes(): ByteArray = EmbeddedFontBytes.bytes()

    val fileName: String get() = EmbeddedFontBytes.FILE_NAME

    val sha256: String get() = EmbeddedFontBytes.SHA256

    /** O programa de fonte, ja verificado. */
    val program: FontProgram by lazy { load(bytes()) }

    /**
     * Interpreta [data] como a fonte embarcada, recusando qualquer coisa que nao seja exatamente ela.
     */
    fun load(data: ByteArray): FontProgram {
        if (data.size != EmbeddedFontBytes.SIZE_BYTES) {
            throw FontFormatException(
                "fonte embarcada com ${data.size} bytes, esperados ${EmbeddedFontBytes.SIZE_BYTES}",
            )
        }
        val program = FontProgram.parse(data)
        if (program.familyName != EXPECTED_FAMILY) {
            throw FontFormatException(
                "familia da fonte embarcada e `${program.familyName}`, esperada `$EXPECTED_FAMILY`",
            )
        }
        if (program.versionName != EXPECTED_VERSION) {
            throw FontFormatException(
                "versao da fonte embarcada e `${program.versionName}`, esperada `$EXPECTED_VERSION`",
            )
        }
        return program
    }
}
