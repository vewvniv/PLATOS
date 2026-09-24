package com.platos.domain.layout

import com.platos.domain.exam.ExamDefinition
import com.platos.domain.exam.buildPackage
import com.platos.domain.exam.folhaDaAtribuicao
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

    /**
     * A mesma prova de referencia, publicada **com roster**. E o instrumento do caminho com
     * atribuicao.
     *
     * **Existe como artefato proprio, e nao como a referencia republicada**, e a razao esta no
     * teste de cima: token de aluno nao e coisa de arquivo versionado, e e o hash da referencia que
     * os tres alvos afirmam. Republica-la com roster desfaria as duas coisas por conveniencia de
     * teste. Aqui vale o precedente da `prova-2`: quando um caminho precisa de um pacote diferente,
     * o pacote e outro arquivo, com o motivo escrito.
     *
     * **Tres alunos, e os tokens sao codigos** — nao nomes civis, nem apelidos que identifiquem
     * alguem. E o modo `coded` que ADR-0012 fez padrao de toda organizacao, e o que ele admite e
     * "aluno por numero, codigo ou apelido". Um artefato versionado com nome de menor dentro seria
     * o oposto do que ADR-0002 decidiu.
     *
     * Tres, e nao dois: com dois, "cada aluno tem a folha dele" e indistinguivel de "a segunda
     * folha e a primeira invertida". Com tres, uma composicao que embaralhasse atribuicoes teria de
     * acertar tres de tres.
     */
    @Test
    fun `regrava o pacote da turma apenas quando solicitado`() {
        if (System.getProperty("platos.golden.write") != "true") return
        val destination = File(System.getProperty("platos.packageTurma.path"))

        val pacote = exam.buildPackage(tokens = listOf("tok-a", "tok-b", "tok-c"))

        destination.writeText(pacote.toCanonicalJson())
        println(
            "pacote da turma regravado: ${destination.absolutePath} (${destination.length()} bytes, " +
                "hash ${pacote.contentHash()}, ${pacote.assignments.size} atribuicoes)",
        )
    }

    /**
     * A segunda prova publicada, e ela e **adversarial** e nao apenas "outra".
     *
     * Ela declara os mesmos identificadores de item e as mesmas posicoes da `prova-referencia`,
     * mudando so o `short_id`. A coincidencia e deliberada: com conjuntos de itens diferentes,
     * `ObjectiveScoring` recusaria a folha trocada por divergencia de itens, a conferencia de
     * identidade ficaria **sombreada** por uma conferencia posterior, e o cenario que a exercita
     * passaria sem que ela existisse.
     *
     * Com os itens iguais, a conferencia do `exam_short_id` e a unica coisa entre a folha errada e
     * uma nota plausivel — que e a forma de falha que ADR-0013 nomeia: integro e errado.
     */
    @Test
    fun `regrava o pacote da segunda prova apenas quando solicitado`() {
        if (System.getProperty("platos.golden.write") != "true") return
        val destination = File(System.getProperty("platos.package2.path"))

        val segunda: ExamDefinition = Json { ignoreUnknownKeys = false }
            .decodeFromString(ExamDefinition.serializer(), Fixtures.PROVA_2_JSON)
        val pacote = segunda.buildPackage()

        destination.writeText(pacote.toCanonicalJson())
        println(
            "pacote 2 regravado: ${destination.absolutePath} (${destination.length()} bytes, " +
                "hash ${pacote.contentHash()})",
        )
    }

    /**
     * A prova com discursiva (`slice-5a-regiao-discursiva`): o layout, o pacote e a folha de um
     * aluno, gravados juntos a partir da mesma definicao.
     *
     * **O pacote tem duas atribuicoes, com tokens que sao codigos**, pela mesma razao do pacote da
     * turma: sem atribuicao, "um QR por regiao" (D23) nao e conferivel nos renderizadores.
     *
     * **A folha do aluno e o oraculo do espelho TypeScript** (decisao 9 do design). Ela e derivada
     * aqui, pela implementacao Kotlin de `folhaDaAtribuicao`; o teste do Vitest deriva a mesma folha
     * do mesmo pacote pela implementacao TypeScript, e compara. Duas implementacoes da regra, em duas
     * linguagens, julgadas pela saida — e o que a P28 aceita como espelho contido.
     */
    @Test
    fun `regrava a prova com discursiva apenas quando solicitado`() {
        if (System.getProperty("platos.golden.write") != "true") return
        val definicao: ExamDefinition = Json { ignoreUnknownKeys = false }
            .decodeFromString(ExamDefinition.serializer(), Fixtures.PROVA_DISCURSIVA_JSON)

        val layout = File(System.getProperty("platos.discursiva.layout.path"))
        layout.writeText(LayoutEngine().layout(definicao).toCanonicalJson())

        val pacote = definicao.buildPackage(tokens = listOf("tok-a", "tok-b"))
        val destinoPacote = File(System.getProperty("platos.discursiva.package.path"))
        destinoPacote.writeText(pacote.toCanonicalJson())

        val aluno = File(System.getProperty("platos.discursiva.aluno.path"))
        aluno.writeText(requireNotNull(pacote.folhaDaAtribuicao("tok-a")).toCanonicalJson())

        println(
            "prova com discursiva regravada: layout ${layout.length()} bytes, pacote " +
                "${destinoPacote.length()} bytes (hash ${pacote.contentHash()}), folha de `tok-a` " +
                "${aluno.length()} bytes",
        )
    }

    /*
     * `fixtures/pacote-do-contrato-anterior.json` NAO e escrito por esta classe, e nao e por
     * esquecimento.
     *
     * Ele e o `prova-referencia.package.json` **do contrato anterior a ADR-0014** — antes de
     * `params_hash` existir em `PackageMeta` —, congelado byte a byte em 2026-09-18, com
     * `sha256 = 26612ad52b0cb967309f49354e9858c501ad7a1b0c7b46db874c05d348e6909a` e 101.618 bytes.
     *
     * **Ele existe para ser recusado.** ADR-0014 decisao 3 aceita que acrescentar o campo faz todo
     * pacote publicado antes dela deixar de passar na camada (b) de ADR-0013 — `encodeDefaults =
     * true` injeta `"params_hash":null` que nao estava nos bytes, e reserializar deixa de reproduzir
     * o original. Essa consequencia so tem como ser exercitada se sobrar um pacote do contrato
     * antigo, e depois da regravacao nao sobra nenhum nesta arvore. Esta e a unica janela, e o
     * arquivo e ela.
     *
     * **Regera-lo destroi o que ele prova.** Um pacote do contrato antigo regerado pelo codigo atual
     * e um pacote do contrato atual — passa nas duas camadas, e o cenario que o consome vira verde
     * vazio. Se algum dia esta classe ganhar um metodo que o escreva, o cenario de
     * `ConferenciaDePacoteTest` que o usa deixa de afirmar qualquer coisa **sem ficar vermelho**.
     */

    /*
     * `fixtures/pacote-antes-da-discursiva.json` tambem NAO e escrito por esta classe, pela mesma
     * razao.
     *
     * Ele e o `prova-referencia.package.json` **do contrato anterior a `slice-5a-regiao-discursiva`**
     * — antes de `PackageItem.kind`, da rubrica, de `ScannableRegion.qr_id` e de `qrs` por regiao —,
     * congelado byte a byte em 2026-09-24, com
     * `sha256 = 277d2f8cd0a7a87e6e26e5ecf47d2f5610dd6e173e724ab38a65373000ac391a` e 101.637 bytes. O
     * hash e o que `ExamPackageTest` e `ExamPublicationTest` fixavam para a prova de referencia no
     * mesmo dia, e e por ele que se sabe que o arquivo e o pacote daquele contrato, e nao outro.
     *
     * **Ele existe para ser recusado**, e pela camada (b), como o de cima: a mudanca aceita, no
     * `design.md` dela (decisao 11), que pacote publicado antes dela deixe de ser interpretado pelo
     * aplicativo atualizado. Os dois arquivos nao sao redundantes. Cada um prova que **uma**
     * mudanca de contrato quebrou a leitura, e so ele prova que foi esta.
     */
}
