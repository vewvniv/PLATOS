package com.platos.domain.layout

import com.platos.domain.exam.ExamDefinition
import com.platos.domain.exam.buildPackage
import com.platos.domain.fixtures.Fixtures
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.test.Test

/**
 * Regrava os artefatos versionados quando pedido explicitamente:
 * `./gradlew :packages:domain:jvmTest -Dplatos.golden.write=true`.
 *
 * Fica atras de uma flag de proposito. Um golden que se regrava sozinho nao detecta nada — ele
 * simplesmente concorda com o que quer que o engine tenha produzido hoje, que e o oposto do que
 * D-1.9 quer.
 */
class GoldenWriterTest {

    private val exam: ExamDefinition = Json { ignoreUnknownKeys = false }
        .decodeFromString(ExamDefinition.serializer(), Fixtures.PROVA_REFERENCIA_JSON)

    @Test
    fun `regrava o golden apenas quando solicitado`() {
        if (System.getProperty("platos.golden.write") != "true") return
        val destination = File(System.getProperty("platos.golden.path"))

        val map = LayoutEngine().layout(exam)

        destination.writeText(map.toCanonicalJson())
        println("golden regravado: ${destination.absolutePath} (${destination.length()} bytes)")
    }

    /**
     * O pacote publicado, versionado ao lado do golden.
     *
     * E de dentro dele que os dois renderizadores passam a extrair a geometria (D-2a.5). Sem
     * consumidor, o pacote seria um artefato que nao tem como estar errado; com ele, a paridade e a
     * fidelidade — que ja existem e ja sabem falhar — passam a julgar o pacote sem uma linha de
     * verificacao nova.
     *
     * Sem atribuicoes: a fixture nao tem roster, e token de aluno nao e coisa de arquivo
     * versionado. E o mesmo pacote cujo hash `ExamPackageTest` afirma nos tres alvos.
     */
    @Test
    fun `regrava o pacote publicado apenas quando solicitado`() {
        if (System.getProperty("platos.golden.write") != "true") return
        val destination = File(System.getProperty("platos.package.path"))

        val pacote = exam.buildPackage()

        destination.writeText(pacote.toCanonicalJson())
        println(
            "pacote regravado: ${destination.absolutePath} (${destination.length()} bytes, " +
                "hash ${pacote.contentHash()})",
        )
    }

    /**
     * A folha de teste de impressao, versionada ao lado do golden.
     *
     * Ela nao vem de prova nenhuma: e um `LayoutMap` proprio, calculado pelas mesmas primitivas e
     * pela mesma `CaptureGeometry`. Versiona-la e o que permite aos dois renderizadores desenharem
     * exatamente a mesma folha, e as ferramentas medirem o que saiu.
     */
    @Test
    fun `regrava a folha de teste de impressao apenas quando solicitado`() {
        if (System.getProperty("platos.golden.write") != "true") return
        val destination = File(System.getProperty("platos.testsheet.path"))

        val folha = PrintTestSheet().layout()

        destination.writeText(folha.toCanonicalJson())
        println("folha de teste regravada: ${destination.absolutePath} (${destination.length()} bytes)")
    }
}
