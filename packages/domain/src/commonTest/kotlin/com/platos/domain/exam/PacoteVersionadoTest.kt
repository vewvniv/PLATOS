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
