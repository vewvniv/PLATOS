package com.platos.domain.exam

import com.platos.domain.fixtures.Fixtures
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * O pacote versionado em `fixtures/` e o que o Layout Engine produz hoje — e o layout dentro dele e
 * o golden, byte a byte.
 *
 * A partir da fatia 2a os renderizadores desenham a partir do **pacote** (D-2a.5), enquanto a
 * paridade e a fidelidade medem contra o **golden**. Se os dois arquivos pudessem divergir, as duas
 * ferramentas passariam a julgar uma geometria que ninguem imprime — o pior tipo de verde, porque
 * elas continuariam funcionando perfeitamente, so que sobre o artefato errado.
 *
 * Roda em `commonTest`: os tres alvos precisam concordar sobre o conteudo do pacote, porque o
 * dispositivo da fatia 4 remonta e confere o que recebeu.
 */
class PacoteVersionadoTest {

    private val exam: ExamDefinition = Json.decodeFromString(
        ExamDefinition.serializer(),
        Fixtures.PROVA_REFERENCIA_JSON,
    )

    @Test
    fun `o pacote versionado e o que a publicacao produz hoje`() {
        // Regravar com `./gradlew :packages:domain:jvmTest -Dplatos.golden.write=true`.
        assertEquals(
            Fixtures.PROVA_REFERENCIA_PACKAGE_JSON.trim(),
            exam.buildPackage().toCanonicalJson(),
            "o pacote versionado envelheceu em relacao a fixture",
        )
    }

    /**
     * O pacote da turma tem a mesma guarda, e por que ele existe separado esta no writer dele.
     *
     * Sem esta comparacao o artefato novo seria um arquivo que nao tem como estar errado — o que o
     * KDoc do `GoldenWriterTest` diz sobre golden que se regrava sozinho, um nivel abaixo.
     */
    @Test
    fun `o pacote da turma e o que a publicacao produz hoje`() {
        assertEquals(
            Fixtures.PROVA_REFERENCIA_TURMA_PACKAGE_JSON.trim(),
            exam.buildPackage(tokens = listOf("tok-a", "tok-b", "tok-c")).toCanonicalJson(),
            "o pacote da turma envelheceu em relacao a fixture",
        )
    }

    /**
     * A geometria dos dois pacotes e **a mesma**, e e isso que torna o instrumento honesto.
     *
     * Se o pacote da turma tivesse geometria propria, medir sobre ele nao diria nada sobre a folha
     * que a paridade mede. Com a geometria identica, o que o caminho com atribuicao acrescenta e
     * exatamente o QR — e nada mais.
     */
    @Test
    fun `o pacote da turma tem a mesma geometria da referencia`() {
        val referencia = Json.decodeFromString(
            ExamPackage.serializer(),
            Fixtures.PROVA_REFERENCIA_PACKAGE_JSON,
        )
        val turma = Json.decodeFromString(
            ExamPackage.serializer(),
            Fixtures.PROVA_REFERENCIA_TURMA_PACKAGE_JSON,
        )

        assertEquals(
            referencia.layout.getValue(DEFAULT_VARIANT).toCanonicalJson(),
            turma.layout.getValue(DEFAULT_VARIANT).toCanonicalJson(),
            "os dois pacotes versionados divergiram na geometria",
        )
        assertEquals(0, referencia.assignments.size, "a referencia ganhou atribuicoes")
        assertEquals(3, turma.assignments.size, "o pacote da turma deixou de ter tres atribuicoes")
    }

    @Test
    fun `o layout dentro do pacote e o golden, byte a byte`() {
        val pacote = Json.decodeFromString(
            ExamPackage.serializer(),
            Fixtures.PROVA_REFERENCIA_PACKAGE_JSON,
        )
        val layout = pacote.layout.getValue(DEFAULT_VARIANT)

        assertEquals(
            Fixtures.PROVA_REFERENCIA_LAYOUT_JSON.trim(),
            layout.toCanonicalJson(),
            "a geometria que se imprime deixou de ser a que a paridade mede",
        )
    }
}
