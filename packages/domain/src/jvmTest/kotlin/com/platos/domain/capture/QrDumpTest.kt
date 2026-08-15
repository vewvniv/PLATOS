package com.platos.domain.capture

import java.io.File
import kotlin.test.Test

/**
 * Nao e uma assercao: despeja matrizes para que um decodificador independente as confira.
 *
 * Um codificador QR errado de um jeito sutil produz uma imagem que parece um QR e nao decodifica —
 * exatamente o defeito que so aparece com a folha ja impressa. Entao a validacao vem de fora, por
 * um leitor que nao compartilha uma linha de codigo com este.
 */
class QrDumpTest {

    @Test
    fun `despeja matrizes para conferencia externa`() {
        val destination = System.getProperty("platos.qr.dump") ?: return
        val payloads = listOf(
            "prova-demo.0.4F2A",
            "p1.0.0001",
            "prova-de-referencia-slice-1.0.BEEF",
            "x",
            // Versao 7 ou maior: a partir dela o QR carrega tambem o bloco de informacao de
            // versao, que e um caminho de codigo proprio.
            "A".repeat(100),
            "B".repeat(150),
            "C".repeat(213),
        )
        val out = StringBuilder()
        for (payload in payloads) {
            val matrix = QrEncoder.encode(payload)
            out.append(payload).append('\n')
            out.append(matrix.size).append('\n')
            for (row in matrix.modules) {
                out.append(row.joinToString("") { if (it) "1" else "0" }).append('\n')
            }
            out.append("---\n")
        }
        File(destination).writeText(out.toString())
    }
}
