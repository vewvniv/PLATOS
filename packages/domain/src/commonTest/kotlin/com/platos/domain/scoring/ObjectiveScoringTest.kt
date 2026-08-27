package com.platos.domain.scoring

import com.platos.domain.capture.CapturePayload
import com.platos.domain.capture.QuestionAnswer
import com.platos.domain.exam.ExamPackage
import com.platos.domain.exam.PackageVariant
import com.platos.domain.fixtures.Fixtures
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A nota, contra o pacote de referencia.
 *
 * O oracle e o `answer_key` do proprio pacote, e ele nao e recalculado aqui: `GabaritoDaFixtureTest`
 * ja prova que cada letra do gabarito vem do valor declarado na questao, e nao de alguem que a
 * escreveu a mao. O que estes testes conferem e outra coisa — que a apuracao **usa** esse gabarito,
 * e que ela para quando nao pode confiar no que recebeu.
 */
class ObjectiveScoringTest {

    private val pacote: ExamPackage = Json.decodeFromString(
        ExamPackage.serializer(),
        Fixtures.PROVA_REFERENCIA_PACKAGE_JSON,
    )

    /** O payload como ele sai da folha hoje: sem aluno e sem variante, que sao a fatia 7 (§8). */
    private val payload = CapturePayload(
        examShortId = pacote.meta.examId,
        studentToken = "",
        variant = "",
        regionIndex = 0,
    )

    private val itens get() = pacote.answerKey.map { it.itemId }

    private fun todasCorretas() = pacote.answerKey.map { QuestionAnswer.Marcada(it.itemId, it.correct) }

    private fun errada(correta: String) = ALTERNATIVAS.first { it != correta }

    private fun scored(outcome: ScoringOutcome): ObjectiveScore {
        assertTrue(outcome is ScoringOutcome.Scored, "esperava nota, veio: $outcome")
        return outcome.score
    }

    private fun rejected(outcome: ScoringOutcome): String {
        assertTrue(outcome is ScoringOutcome.Rejected, "esperava recusa, veio: $outcome")
        return outcome.reason
    }

    @Test
    fun `folha toda correta tira o maximo que o pacote declara`() {
        val nota = scored(ObjectiveScoring.score(pacote, payload, todasCorretas()))

        assertEquals(pacote.scoring.maxScore, nota.points)
        assertEquals(40, nota.points, "a prova de referencia vale 40 pontos, um por item")
        assertTrue(nota.closed)
        assertEquals(0, nota.pointsAtStake)
    }

    @Test
    fun `folha toda errada tira zero, e a nota fecha`() {
        val respostas = pacote.answerKey.map { QuestionAnswer.Marcada(it.itemId, errada(it.correct)) }

        val nota = scored(ObjectiveScoring.score(pacote, payload, respostas))

        assertEquals(0, nota.points)
        assertTrue(nota.closed, "errar nao e duvida: a nota fecha em zero")
    }

    @Test
    fun `folha mista soma exatamente os acertos`() {
        // Dez primeiras certas, o resto errado. A conta a mao e 10, porque cada item vale 1.
        val respostas = pacote.answerKey.mapIndexed { i, entrada ->
            val alternativa = if (i < 10) entrada.correct else errada(entrada.correct)
            QuestionAnswer.Marcada(entrada.itemId, alternativa)
        }

        val nota = scored(ObjectiveScoring.score(pacote, payload, respostas))

        assertEquals(10, nota.points)
        assertEquals(40, nota.maxScore)
    }

    @Test
    fun `em branco vale zero, e continua fechando a nota`() {
        val respostas = pacote.answerKey.mapIndexed { i, entrada ->
            if (i == 0) QuestionAnswer.EmBranco(entrada.itemId)
            else QuestionAnswer.Marcada(entrada.itemId, entrada.correct)
        }

        val nota = scored(ObjectiveScoring.score(pacote, payload, respostas))

        assertEquals(39, nota.points)
        assertTrue(nota.closed, "em branco e resultado, e nao duvida")
        assertTrue(nota.pending.isEmpty())
    }

    @Test
    fun `multipla marcacao vira pendencia, e a nota deixa de fechar`() {
        val respostas = pacote.answerKey.mapIndexed { i, entrada ->
            if (i == 0) QuestionAnswer.MultiplaMarcacao(entrada.itemId, listOf("A", "B"))
            else QuestionAnswer.Marcada(entrada.itemId, entrada.correct)
        }

        val nota = scored(ObjectiveScoring.score(pacote, payload, respostas))

        assertEquals(39, nota.points, "a pendencia nao entra na nota, nem como zero")
        assertFalse(nota.closed)
        assertEquals(1, nota.pending.size)
        assertEquals(PendingReason.MULTIPLA_MARCACAO, nota.pending.single().reason)
        assertEquals(1, nota.pointsAtStake, "o maximo em disputa e o que a questao vale")
    }

    @Test
    fun `questao indecisa vira pendencia`() {
        val respostas = pacote.answerKey.mapIndexed { i, entrada ->
            if (i == 0) QuestionAnswer.Indecisa(entrada.itemId, listOf("C"))
            else QuestionAnswer.Marcada(entrada.itemId, entrada.correct)
        }

        val nota = scored(ObjectiveScoring.score(pacote, payload, respostas))

        assertFalse(nota.closed)
        assertEquals(PendingReason.INDECISA, nota.pending.single().reason)
    }

    @Test
    fun `a pendencia nao vira zero nem acerto`() {
        // O caso que separa "pendente" de "errado": a bolha pendente e a **correta**. Se a
        // apuracao resolvesse a duvida sozinha, num sentido ou no outro, a nota seria 40 ou 39 —
        // as duas fechadas, e as duas erradas.
        val respostas = pacote.answerKey.mapIndexed { i, entrada ->
            if (i == 0) QuestionAnswer.MultiplaMarcacao(entrada.itemId, listOf(entrada.correct, "D"))
            else QuestionAnswer.Marcada(entrada.itemId, entrada.correct)
        }

        val nota = scored(ObjectiveScoring.score(pacote, payload, respostas))

        assertEquals(39, nota.points)
        assertFalse(nota.closed)
        assertEquals(1, nota.pointsAtStake)
    }

    @Test
    fun `a nota diz de qual pacote e de qual variante ela e`() {
        val nota = scored(ObjectiveScoring.score(pacote, payload, todasCorretas()))

        assertEquals(pacote.contentHash(), nota.packageHash)
        assertEquals("v1", nota.variantId)
    }

    @Test
    fun `payload sem variante e aceito quando o pacote declara uma so`() {
        assertEquals("", payload.variant)
        assertEquals(1, pacote.variants.size)

        val nota = scored(ObjectiveScoring.score(pacote, payload, todasCorretas()))

        assertEquals("v1", nota.variantId)
    }

    @Test
    fun `payload sem variante e recusado quando o pacote declara mais de uma`() {
        // A fatia 7 traz variantes de verdade. Ate o QR passar a carregar a variante, escolher a
        // primeira atribuiria a folha ao gabarito errado — e toda folha continuaria recebendo uma
        // nota plausivel, que e o modo de falha mais caro que esta base tem.
        val duasVariantes = pacote.copy(
            variants = pacote.variants + PackageVariant("v2", pacote.variants.single().positions),
        )

        val motivo = rejected(ObjectiveScoring.score(duasVariantes, payload, todasCorretas()))

        assertTrue(motivo.contains("v1") && motivo.contains("v2"), "a mensagem precisa nomear as variantes: $motivo")
    }

    @Test
    fun `variante que o pacote nao conhece e recusada`() {
        val outraVariante = payload.copy(variant = "v9")

        val motivo = rejected(ObjectiveScoring.score(pacote, outraVariante, todasCorretas()))

        assertTrue(motivo.contains("v9"), "a mensagem precisa nomear a variante encontrada: $motivo")
    }

    @Test
    fun `conjunto de itens divergente e recusado, e a mensagem diz o que faltou`() {
        val faltandoUma = todasCorretas().dropLast(1)

        val motivo = rejected(ObjectiveScoring.score(pacote, payload, faltandoUma))

        assertTrue(motivo.contains(itens.last()), "a mensagem precisa nomear o item faltando: $motivo")
    }

    @Test
    fun `item que a variante nao declara e recusado`() {
        val comIntruso = todasCorretas().dropLast(1) + QuestionAnswer.EmBranco("q99")

        val motivo = rejected(ObjectiveScoring.score(pacote, payload, comIntruso))

        assertTrue(motivo.contains("q99"), "a mensagem precisa nomear o item nao declarado: $motivo")
        // A recusa precisa vir da conferencia contra a variante, e nao do gabarito. As duas pegam
        // `q99`, entao afirmar so o identificador deixaria este teste passar com a conferencia do
        // conjunto desligada — foi o que aconteceu na primeira vez que ele foi visto falhar.
        assertTrue(
            motivo.contains("nao declarados"),
            "a recusa tem de ser a da divergencia contra a variante: $motivo",
        )
    }

    @Test
    fun `item sem entrada no gabarito e recusado`() {
        // O conjunto lido continua batendo com a variante; o que falta e a entrada de gabarito.
        // Sem esta guarda, a questao sumiria da conta e a nota sairia menor, sem dizer por que.
        val semUmaEntrada = pacote.copy(answerKey = pacote.answerKey.dropLast(1))

        val motivo = rejected(ObjectiveScoring.score(semUmaEntrada, payload, todasCorretas()))

        assertTrue(motivo.contains(itens.last()), "a mensagem precisa nomear o item: $motivo")
    }

    @Test
    fun `a nota reage a uma troca no gabarito`() {
        // O teste que prova que a nota **usa** o gabarito, e nao so o carrega. Uma apuracao que
        // ignorasse `correct` e somasse tudo passaria em `folha toda correta` — o caso positivo
        // sozinho nao distingue somar certo de somar sempre.
        val respostas = todasCorretas()
        val primeira = pacote.answerKey.first()
        val gabaritoAlterado = pacote.copy(
            answerKey = listOf(primeira.copy(correct = errada(primeira.correct))) +
                pacote.answerKey.drop(1),
        )

        val original = scored(ObjectiveScoring.score(pacote, payload, respostas)).points
        val alterado = scored(ObjectiveScoring.score(gabaritoAlterado, payload, respostas)).points

        assertEquals(original - 1, alterado, "trocar uma letra do gabarito tem de valer um ponto")
    }

    @Test
    fun `resposta repetida para o mesmo item e recusada`() {
        // O mesmo furo do conjunto, do lado da nota, e aqui ele custa ponto: 41 respostas com
        // `q01` duas vezes tem o mesmo conjunto de 40 itens, e a soma passaria de `max_score`.
        val repetida = todasCorretas() + todasCorretas().first()

        val motivo = rejected(ObjectiveScoring.score(pacote, payload, repetida))

        assertTrue(motivo.contains("repetid"), "a mensagem precisa dizer que ha repeticao: $motivo")
        assertTrue(motivo.contains(itens.first()), "a mensagem precisa nomear o item: $motivo")
    }

    @Test
    fun `nota fora da escala nao e representavel`() {
        // A segunda guarda do furo que a auditoria encontrou. A conferencia por conjunto deixava
        // passar resposta repetida e a nota saia 41 de 40 — bem-formada, plausivel e errada. A
        // recusa por repeticao fecha a porta; este `require` fecha a janela, para o proximo
        // caminho que produza soma fora da escala.
        assertFailsWith<IllegalArgumentException> {
            ObjectiveScore("hash", "v1", points = 41, maxScore = 40, pending = emptyList())
        }
        assertFailsWith<IllegalArgumentException> {
            ObjectiveScore("hash", "v1", points = -1, maxScore = 40, pending = emptyList())
        }
        assertFailsWith<IllegalArgumentException> {
            ObjectiveScore(
                "hash",
                "v1",
                points = 40,
                maxScore = 40,
                pending = listOf(PendingQuestion("q01", PendingReason.INDECISA, 1)),
            )
        }
    }

    @Test
    fun `nota no limite da escala e valida`() {
        // O par positivo: zero, o maximo, e a soma que fecha exatamente no maximo com pendencia.
        ObjectiveScore("hash", "v1", points = 0, maxScore = 40, pending = emptyList())
        ObjectiveScore("hash", "v1", points = 40, maxScore = 40, pending = emptyList())
        ObjectiveScore(
            "hash",
            "v1",
            points = 39,
            maxScore = 40,
            pending = listOf(PendingQuestion("q01", PendingReason.INDECISA, 1)),
        )
    }

    @Test
    fun `a mesma entrada apurada duas vezes da o mesmo resultado`() {
        val respostas = todasCorretas()

        assertEquals(
            ObjectiveScoring.score(pacote, payload, respostas),
            ObjectiveScoring.score(pacote, payload, respostas),
        )
    }

    @Test
    fun `a apuracao nao altera a leitura nem o pacote`() {
        val respostas = todasCorretas()
        val antesDoPacote = pacote.toCanonicalJson()
        val antesDasRespostas = respostas.toString()

        ObjectiveScoring.score(pacote, payload, respostas)

        assertEquals(antesDoPacote, pacote.toCanonicalJson())
        assertEquals(antesDasRespostas, respostas.toString())
    }

    private companion object {
        val ALTERNATIVAS = listOf("A", "B", "C", "D")
    }
}
