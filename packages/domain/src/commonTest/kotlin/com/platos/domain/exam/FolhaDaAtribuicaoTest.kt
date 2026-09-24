package com.platos.domain.exam

import com.platos.domain.fixtures.Fixtures
import com.platos.domain.layout.DrawQr
import com.platos.domain.layout.LayoutMap
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A composicao da folha de um aluno: geometria da variante, QR da atribuicao.
 *
 * Roda em `commonTest` pela mesma razao de [ExamPackageTest]: a regra e executada duas vezes — aqui
 * e no renderizador do web, que le o mesmo JSON (D-2a.1). O que este arquivo prende e a **regra**;
 * que as duas implementacoes coincidam e o que a paridade entre plataformas mede.
 *
 * **Os payloads sao os que a publicacao produz, e nao montados a mao.** Publicar passa tokens, o
 * escritor unico resolve o QR de cada atribuicao, e os cenarios comparam contra o que saiu dali. O
 * inverso — payload de teste — deixaria a composicao verde sobre um formato que a publicacao nao
 * produz.
 */
class FolhaDaAtribuicaoTest {

    private val exam: ExamDefinition = Json.decodeFromString(
        ExamDefinition.serializer(),
        Fixtures.PROVA_REFERENCIA_JSON,
    )

    /** A mesma geometria com o QR neutralizado, para comparar tudo **menos** o QR. */
    private fun LayoutMap.semOQr(): LayoutMap = copy(
        pages = pages.map { pagina ->
            pagina.copy(
                primitives = pagina.primitives.map {
                    if (it is DrawQr) it.copy(payload = "", modules = emptyList()) else it
                },
            )
        },
    )

    private fun qrDaFolha(mapa: LayoutMap): DrawQr =
        mapa.pages.flatMap { it.primitives }.filterIsInstance<DrawQr>().single()

    @Test
    fun `a folha da atribuicao e a geometria da variante com o QR dela`() {
        val pacote = exam.buildPackage(tokens = listOf("tok-1"))
        val atribuicao = pacote.assignments.single()
        val folha = requireNotNull(pacote.folhaDaAtribuicao("tok-1"))
        val geometria = requireNotNull(pacote.layout[DEFAULT_VARIANT])

        // Tres afirmacoes separadas: que o QR e o da atribuicao, que a matriz veio com ele, e que
        // **nada mais** mudou. Uma composicao que reconstruisse a geometria passaria nas duas
        // primeiras e falharia na terceira — e e a terceira que sustenta compartilhar geometria.
        assertEquals(atribuicao.qrs.single().payload, qrDaFolha(folha).payload)
        assertEquals(atribuicao.qrs.single().modules, qrDaFolha(folha).modules)
        assertEquals(geometria.semOQr(), folha.semOQr(), "a composicao mexeu em algo alem do QR")

        // E o QR da folha do aluno nao e o da variante: a geometria compartilhada carrega o payload
        // sem identidade, e a folha do aluno tem de substitui-lo.
        assertNotEquals(
            qrDaFolha(geometria).payload,
            qrDaFolha(folha).payload,
            "a folha do aluno saiu com o payload da variante, sem identidade",
        )
    }

    @Test
    fun `duas atribuicoes produzem folhas que diferem so no QR`() {
        val pacote = exam.buildPackage(tokens = listOf("tok-1", "tok-2"))

        val umaFolha = requireNotNull(pacote.folhaDaAtribuicao("tok-1"))
        val outraFolha = requireNotNull(pacote.folhaDaAtribuicao("tok-2"))

        assertNotEquals(
            qrDaFolha(umaFolha).payload,
            qrDaFolha(outraFolha).payload,
            "as duas folhas trouxeram o mesmo payload; a atribuicao nao chegou ao QR",
        )
        assertEquals(
            umaFolha.semOQr(),
            outraFolha.semOQr(),
            "as folhas de dois alunos divergiram fora do QR, e a medicao impressa deixa de valer " +
                "para uma delas",
        )

        // A posicao e o tamanho do QR sao geometria, e nao conteudo: eles NAO podem variar entre
        // alunos, senao a regiao escaneavel muda de lugar de folha para folha.
        val um = qrDaFolha(umaFolha)
        val outro = qrDaFolha(outraFolha)
        assertTrue(
            um.x == outro.x && um.y == outro.y && um.side == outro.side && um.module == outro.module,
            "o QR mudou de lugar entre alunos: (${um.x},${um.y},${um.side}) contra " +
                "(${outro.x},${outro.y},${outro.side})",
        )
    }

    @Test
    fun `atribuicao sem QR e pacote incoerente, e nao folha igual a da variante`() {
        // Montado por `copy` a partir de um pacote valido, e nao por `buildPackage`: a validacao
        // recusa atribuicao sem QR **na publicacao**, e este cenario cobre a segunda linha de
        // defesa — pacote que chegou de outro lugar, como o disco do aparelho, e no qual a
        // composicao nao confia.
        val pacote = exam.buildPackage(tokens = listOf("tok-1"))
            .let { it.copy(assignments = it.assignments.map { a -> a.copy(qrs = emptyList()) }) }

        // O desfecho errado seria silencioso: devolver a folha da variante, com o campo de aluno
        // vazio, para um aluno que existe. A folha sairia impressa sem dono e ninguem notaria.
        val erro = assertFailsWith<IllegalArgumentException> { pacote.folhaDaAtribuicao("tok-1") }
        assertTrue(
            erro.message!!.contains("nao tem QR"),
            "a recusa nao disse que faltava o QR: ${erro.message}",
        )
    }

    @Test
    fun `token sem atribuicao nao tem folha, e isso nao e erro`() {
        val pacote = exam.buildPackage(tokens = listOf("tok-1"))

        // Pergunta legitima de quem le uma folha desconhecida — folha de outra prova, folha avulsa,
        // token que nao pertence a esta turma. Estourar aqui faria o aparelho tratar folha estranha
        // como defeito do pacote.
        assertNull(pacote.folhaDaAtribuicao("tok-desconhecido"))
    }

    @Test
    fun `atribuicao que aponta variante sem layout e pacote incoerente`() {
        val pacote = exam.buildPackage(tokens = listOf("tok-1"))
            .let {
                it.copy(assignments = it.assignments.map { a -> a.copy(variantId = "variante-fantasma") })
            }

        val erro = assertFailsWith<IllegalArgumentException> { pacote.folhaDaAtribuicao("tok-1") }
        assertTrue(
            erro.message!!.contains("variante-fantasma"),
            "a recusa nao nomeou a variante que falta: ${erro.message}",
        )
    }
}
