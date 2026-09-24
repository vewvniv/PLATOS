package com.platos.android.scan

import com.platos.domain.exam.ExamPackage
import java.io.File
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * A montagem do analisador da camera diante de uma prova com discursiva
 * (`slice-5b-1-o-aparelho-reconhece-a-discursiva`, tarefa 3.1).
 *
 * Era aqui que o aplicativo caia: a `ScanActivity` montava o analisador com
 * `region = map.regions.single()`, e a prova com discursiva tem tres regioes. O teste chama a mesma
 * funcao que a `ScanActivity` chama ([CameraFrameAnalyzer.daSessao]), com o mapa da fixture, em vez de
 * reescrever a montagem — um teste que montasse do seu jeito passaria com a `Activity` ainda
 * quebrada.
 */
class MontagemDoAnalisadorTest {

    private val fixtures = File(
        System.getProperty("platos.fixtures")
            ?: error("propriedade `platos.fixtures` nao definida pelo build"),
    )

    @Test
    fun `o analisador e montado com o mapa da prova com discursiva, sem excecao`() {
        val pacote: ExamPackage =
            Json.decodeFromString(File(fixtures, "prova-discursiva.package.json").readText())
        val mapa = pacote.layout.values.single()
        // Guarda de vacuidade: o mapa tem de ter mais de uma regiao, ou o teste nao afirma nada.
        assertEquals(3, mapa.regions.size)

        CameraFrameAnalyzer.daSessao(mapa, deveAnalisar = { false }, entrega = {})
    }
}
