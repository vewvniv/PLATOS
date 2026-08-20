package com.platos.domain.exam

import com.platos.domain.fixtures.Fixtures
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * O gabarito da fixture, afirmado como **dado com procedência** e não como letras escritas à mão.
 *
 * A primeira versão deste gabarito eram 40 letras que eu derivei e escrevi direto no JSON. Estava
 * certo, provavelmente — mas ninguém tinha como saber: a derivação vivia fora do repositório, e uma
 * letra trocada por engano seria indistinguível de uma letra correta. Numa base que exige oracle
 * independente para medir 0,04 mm, gabarito sem procedência é o mesmo defeito de outra cor.
 *
 * O que mudou: a fixture declara o **valor** correto e **como se chega nele**; a letra é computada.
 * A divergência entre letra e conteúdo deixou de ser representável.
 */
class GabaritoDaFixtureTest {

    private val exam: ExamDefinition = Json.decodeFromString(
        ExamDefinition.serializer(),
        Fixtures.PROVA_REFERENCIA_JSON,
    )

    @Test
    fun `toda questao declara resposta e justificativa`() {
        for (q in exam.questions) {
            assertTrue(
                !q.answer.isNullOrBlank(),
                "questao `${q.id}` sem resposta declarada; a letra e derivada dela",
            )
            assertTrue(
                (q.why?.length ?: 0) > 5,
                "questao `${q.id}` sem justificativa; gabarito sem procedencia e chute",
            )
        }
    }

    @Test
    fun `a resposta declarada aparece exatamente uma vez entre as alternativas`() {
        for (q in exam.questions) {
            assertEquals(
                1,
                q.options.count { it == q.answer },
                "questao `${q.id}`: a resposta `${q.answer}` precisa estar entre ${q.options} " +
                    "exatamente uma vez — zero significa gabarito errado, duas significa " +
                    "alternativa repetida",
            )
        }
    }

    /**
     * A distribuição existe para a fatia 3, e não por estética.
     *
     * A primeira versão da fixture tinha A=1, B=18, C=21 e **D=0**: nenhuma resposta certa caía na
     * última alternativa. O OMR nunca teria de acertar a leitura da quarta bolha, e a captura
     * poderia estar lendo a coluna errada sem uma única questão acusar.
     *
     * O rebalanceamento é determinístico — a alternativa correta da questão `i` fica na posição
     * `i mod 4` —, então ele não depende de sorteio nem de quem rodou o script.
     */
    @Test
    fun `a alternativa correta usa as quatro posicoes por igual`() {
        val porLetra = exam.questions
            .map { q -> "ABCD"[q.options.indexOf(q.answer)] }
            .groupingBy { it }
            .eachCount()

        assertEquals(
            mapOf('A' to 10, 'B' to 10, 'C' to 10, 'D' to 10),
            porLetra,
            "sem as quatro posicoes ocupadas, o OMR da fatia 3 nao exercita a leitura de todas as bolhas",
        )
    }

    @Test
    fun `a posicao da correta segue a regra deterministica`() {
        // Sem esta afirmacao, a distribuicao igual poderia vir de um sorteio — e um sorteio faria a
        // fixture mudar a cada execucao, com o golden junto.
        for ((i, q) in exam.questions.withIndex()) {
            assertEquals(
                i % 4,
                q.options.indexOf(q.answer),
                "questao `${q.id}` (indice $i) deveria ter a correta na posicao ${i % 4}",
            )
        }
    }

    @Test
    fun `a letra do pacote e a da posicao declarada`() {
        val pacote = exam.buildPackage()
        val porItem = pacote.answerKey.associateBy { it.itemId }
        assertEquals(exam.questions.size, porItem.size)
        for ((i, q) in exam.questions.withIndex()) {
            assertEquals("ABCD"[i % 4].toString(), porItem.getValue(q.id).correct)
        }
    }

    @Test
    fun `resposta fora das alternativas impede a publicacao`() {
        val quebrado = exam.copy(
            questions = exam.questions.mapIndexed { i, q ->
                if (i == 5) q.copy(answer = "resposta que nao esta na lista") else q
            },
        )
        val erro = assertFailsWith<ExamPackageException> { quebrado.buildPackage() }
        assertContains(erro.message!!, exam.questions[5].id)
        assertContains(erro.message!!, "exatamente uma")
    }

    @Test
    fun `alternativa repetida impede a publicacao`() {
        // Duas alternativas iguais tornam o gabarito ambiguo: a bolha certa passa a ser duas.
        val quebrado = exam.copy(
            questions = exam.questions.mapIndexed { i, q ->
                if (i == 5) q.copy(options = List(q.options.size) { q.answer!! }) else q
            },
        )
        assertFailsWith<ExamPackageException> { quebrado.buildPackage() }
    }
}
