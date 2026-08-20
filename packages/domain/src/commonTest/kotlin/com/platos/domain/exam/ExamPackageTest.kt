package com.platos.domain.exam

import com.platos.domain.fixtures.Fixtures
import com.platos.domain.layout.LayoutProfile
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Cobre os cenarios de `exam-package` que nao dependem de banco.
 *
 * Roda em `commonTest`, e isso e exigencia e nao zelo: o servidor produz o hash e o dispositivo que
 * recebe o pacote precisa poder conferi-lo. Um hash que divergisse entre JVM, Node e Android
 * tornaria o pacote inverificavel exatamente onde ele mais importa.
 */
class ExamPackageTest {

    private val HASH_DA_FIXTURE = "61c96f4c146341c3d8bcdc116f2f4f4949efc55112205ab52bcc8e3df950613c"

    private val exam: ExamDefinition = Json.decodeFromString(
        ExamDefinition.serializer(),
        Fixtures.PROVA_REFERENCIA_JSON,
    )

    // --- hash ---

    @Test
    fun `republicar a mesma prova da o mesmo hash`() {
        assertEquals(exam.buildPackage().contentHash(), exam.buildPackage().contentHash())
    }

    @Test
    fun `o hash da fixture e o mesmo nos tres alvos`() {
        // Valor fixado, como o golden — e nao redundante com ele. O golden cobre a geometria;
        // este cobre o ARTEFATO PUBLICADO inteiro: itens, habilidades, gabarito e layout. Se um
        // alvo calculasse diferente, o pacote deixaria de ser verificavel no dispositivo que o
        // consome, e e por isso que a afirmacao vive em `commonTest`.
        //
        // Regravar quando a fixture mudar de proposito, junto com o golden.
        assertEquals(HASH_DA_FIXTURE, exam.buildPackage().contentHash())
    }

    @Test
    fun `o hash tem a forma de um sha256`() {
        val hash = exam.buildPackage().contentHash()
        assertEquals(64, hash.length)
        assertTrue(hash.all { it in "0123456789abcdef" }, "hash fora do alfabeto hexadecimal: $hash")
    }

    @Test
    fun `mudanca no conteudo muda o hash`() {
        val original = exam.buildPackage().contentHash()

        val comEnunciadoDiferente = exam.copy(
            questions = exam.questions.mapIndexed { i, q ->
                if (i == 0) q.copy(statement = q.statement + " ") else q
            },
        )
        assertTrue(comEnunciadoDiferente.buildPackage().contentHash() != original)

        // Trocar a resposta por outra alternativa da mesma questao: o conteudo e o mesmo, so o
        // gabarito muda. E o caso que o hash precisa pegar.
        val comGabaritoDiferente = exam.copy(
            questions = exam.questions.mapIndexed { i, q ->
                if (i == 0) q.copy(answer = q.options.first { it != q.answer }) else q
            },
        )
        assertTrue(
            comGabaritoDiferente.buildPackage().contentHash() != original,
            "trocar o gabarito precisa mudar o hash; senao a correcao pode ser adulterada sem deixar rastro",
        )
    }

    @Test
    fun `atribuir alunos muda o hash, mas so pelo token`() {
        val semAlunos = exam.buildPackage().contentHash()
        val comAlunos = exam.buildPackage(
            assignments = listOf(PackageAssignment("tok-1", DEFAULT_VARIANT)),
        ).contentHash()
        assertTrue(semAlunos != comAlunos)
    }

    // --- I5: o pacote não carrega dado pessoal ---

    @Test
    fun `o pacote nao carrega nome de aluno`() {
        val pacote = exam.buildPackage(
            assignments = listOf(
                PackageAssignment("tok-1", DEFAULT_VARIANT),
                PackageAssignment("tok-2", DEFAULT_VARIANT),
            ),
        )
        val json = pacote.toCanonicalJson()

        // A varredura e sobre o JSON, e nao sobre o tipo: um campo acrescentado por engano aparece
        // aqui antes de aparecer em qualquer revisao de codigo. E a barreira executavel de I5.
        for (proibido in listOf("nome", "name", "turma", "matricula", "student_name", "class_group")) {
            assertTrue(
                !json.contains("\"$proibido\""),
                "o pacote imutavel nao pode declarar `$proibido`: dado pessoal em artefato copiado " +
                    "para dispositivo offline nao e alcancado pelo direito de eliminacao (I5)",
            )
        }
        assertTrue(json.contains("student_token"), "a atribuicao precisa existir, so que por token")
    }

    // --- I1: a barreira da habilidade ---

    @Test
    fun `item sem habilidade impede a publicacao`() {
        val semHabilidade = exam.copy(
            questions = exam.questions.mapIndexed { i, q -> if (i == 3) q.copy(skills = emptyList()) else q },
        )
        val erro = assertFailsWith<ExamPackageException> { semHabilidade.buildPackage() }
        assertContains(erro.message!!, exam.questions[3].id)
        assertContains(erro.message!!, "habilidade")
    }

    @Test
    fun `a fixture declara habilidade em toda questao, com a qualidade da atribuicao`() {
        val itens = exam.buildPackage().items
        assertEquals(40, itens.size)
        assertTrue(itens.all { it.skills.isNotEmpty() })

        // A BNCC nao cobre vetor, matriz nem determinante. Guardar so o codigo apagaria isso, e o
        // boletim somaria dominio de uma habilidade a partir de questao que nao e sobre ela.
        val porCobertura = itens.flatMap { it.skills }.groupingBy { it.coverage }.eachCount()
        assertEquals(3, porCobertura[SkillCoverage.ANCHOR], "vetor, determinante e matriz sao ancoras")
        assertTrue((porCobertura[SkillCoverage.DIRECT] ?: 0) > 30)
    }

    // --- I3: provenance no contrato desde o começo ---

    @Test
    fun `prova fixa publica sem artefato de IA, e os campos existem`() {
        val json = exam.buildPackage().toCanonicalJson()
        assertTrue(json.contains("prompt_version"), "I3 precisa estar no contrato desde o primeiro pacote")
        assertTrue(json.contains("model_id"))
        assertEquals(null, exam.buildPackage().meta.promptVersion)
    }

    // --- coerência interna ---

    @Test
    fun `item sem gabarito impede a publicacao`() {
        val semGabarito = exam.copy(
            questions = exam.questions.mapIndexed { i, q -> if (i == 7) q.copy(answer = null) else q },
        )
        val erro = assertFailsWith<ExamPackageException> { semGabarito.buildPackage() }
        assertContains(erro.message!!, exam.questions[7].id)
        assertContains(erro.message!!, "offline")
    }

    @Test
    fun `variante apontando item inexistente e recusada`() {
        val pacote = exam.buildPackage()
        val quebrado = pacote.copy(
            variants = listOf(pacote.variants[0].copy(positions = mapOf("1" to "q-que-nao-existe"))),
        )
        val erro = assertFailsWith<ExamPackageException> { quebrado.requireCoherent() }
        assertContains(erro.message!!, "q-que-nao-existe")
    }

    @Test
    fun `atribuicao apontando variante inexistente e recusada`() {
        // `buildPackage` ja valida, entao o estado invalido e montado a partir de um pacote valido.
        val pacote = exam.buildPackage(assignments = listOf(PackageAssignment("tok-1", DEFAULT_VARIANT)))
        val quebrado = pacote.copy(assignments = listOf(PackageAssignment("tok-1", "variante-fantasma")))
        val erro = assertFailsWith<ExamPackageException> { quebrado.requireCoherent() }
        assertContains(erro.message!!, "variante-fantasma")
    }

    @Test
    fun `layout divergente dos itens e recusado`() {
        // Tirar o item E a posicao dele: sem isso a recusa da variante dispara antes, e o teste
        // passaria por outro motivo que nao o que ele afirma cobrir.
        val pacote = exam.buildPackage()
        val ultimo = pacote.items.last().id
        val quebrado = pacote.copy(
            items = pacote.items.dropLast(1),
            variants = pacote.variants.map { v -> v.copy(positions = v.positions.filterValues { it != ultimo }) },
            answerKey = pacote.answerKey.filter { it.itemId != ultimo },
        )
        val erro = assertFailsWith<ExamPackageException> { quebrado.requireCoherent() }
        assertContains(erro.message!!, "layout")
        assertContains(erro.message!!, ultimo)
    }

    @Test
    fun `o pacote da fixture e coerente`() {
        // O caso positivo, sem o qual as recusas acima poderiam estar recusando tudo.
        exam.buildPackage().requireCoherent()
    }

    // --- o perfil viaja dentro do pacote ---

    @Test
    fun `perfis diferentes dao pacotes diferentes`() {
        val ampliado = LayoutProfile.DEFAULT.copy(
            id = "a4-2col-ampliado",
            style = LayoutProfile.DEFAULT.style.copy(
                size = LayoutProfile.DEFAULT.style.size * 2,
                lineHeight = LayoutProfile.DEFAULT.style.lineHeight * 2,
            ),
            inlineHeightCeiling = LayoutProfile.DEFAULT.style.lineHeight * 4,
        )
        assertTrue(exam.buildPackage().contentHash() != exam.buildPackage(profile = ampliado).contentHash())
    }
}
