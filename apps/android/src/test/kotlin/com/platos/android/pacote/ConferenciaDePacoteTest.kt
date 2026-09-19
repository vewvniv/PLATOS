package com.platos.android.pacote

import java.io.File
import java.security.MessageDigest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * As duas camadas de conferencia, sem rede e sem aparelho.
 *
 * **O pacote e o de referencia versionado**, e nao um montado a mao: e o mesmo artefato que o
 * servidor publica e que a folha impressa descreve. Montar um pacote de brinquedo aqui faria os
 * cenarios de (b) afirmarem sobre uma estrutura que nao e a que o aplicativo vai receber.
 *
 * **O hash esperado vem do `MessageDigest` da JVM**, e nao do `Sha256` do dominio. Conferir a
 * implementacao contra ela mesma nao provaria nada; o oraculo aqui e a implementacao da plataforma.
 */
class ConferenciaDePacoteTest {

    private val fixtures = File(
        System.getProperty("platos.fixtures")
            ?: error("propriedade `platos.fixtures` nao definida pelo build"),
    )

    private val bytes: ByteArray = File(fixtures, "prova-referencia.package.json").readBytes()

    /** Oraculo independente: SHA-256 da plataforma, sobre os bytes do arquivo. */
    private val hash: String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }

    private fun conferido(resultado: Conferencia): Conferencia.Conferido {
        if (resultado !is Conferencia.Conferido) throw AssertionError("esperava Conferido, veio $resultado")
        return resultado
    }

    private fun recusado(resultado: Conferencia): Conferencia.Recusado {
        if (resultado !is Conferencia.Recusado) throw AssertionError("esperava Recusado, veio $resultado")
        return resultado
    }

    // ------------------------------------------------------------------ o caminho feliz

    @Test
    fun `o pacote de referencia passa nas duas camadas`() {
        val resultado = conferido(verificarPacote(bytes, hash))

        assertEquals(hash, resultado.contentHash)
        assertEquals("prova-referencia-slice-1", resultado.pacote.meta.examId)
    }

    /**
     * O hash do dominio e o da plataforma concordam sobre estes bytes.
     *
     * Nao e redundante com o cenario acima: aquele afirma que a conferencia aceita, este afirma que
     * o valor com que ela concorda e o valor certo. Se `Sha256` derivasse, o primeiro continuaria
     * passando enquanto os dois lados derivassem juntos.
     */
    @Test
    fun `o hash do dominio concorda com o da plataforma`() {
        assertEquals(hash, com.platos.domain.hash.Sha256.hex(bytes))
    }

    // ------------------------------------------------------------------ camada (a)

    @Test
    fun `um byte alterado e recusado por integridade`() {
        val alterado = bytes.copyOf()
        // O ultimo byte do JSON canonico e `}`; trocar por espaco mantem o tamanho e quebra o hash.
        alterado[alterado.lastIndex] = ' '.code.toByte()

        val recusa = recusado(verificarPacote(alterado, hash))

        assertEquals(MotivoDaRecusa.INTEGRIDADE, recusa.motivo)
    }

    @Test
    fun `conteudo truncado e recusado por integridade`() {
        val recusa = recusado(verificarPacote(bytes.copyOf(bytes.size - 1), hash))

        assertEquals(MotivoDaRecusa.INTEGRIDADE, recusa.motivo)
    }

    @Test
    fun `conteudo vazio e recusado por integridade`() {
        val recusa = recusado(verificarPacote(ByteArray(0), hash))

        assertEquals(MotivoDaRecusa.INTEGRIDADE, recusa.motivo)
    }

    @Test
    fun `um byte a mais e recusado por integridade`() {
        val recusa = recusado(verificarPacote(bytes + ' '.code.toByte(), hash))

        assertEquals(MotivoDaRecusa.INTEGRIDADE, recusa.motivo)
    }

    /**
     * O detalhe traz os dois hashes, e nao so a palavra "divergiu".
     *
     * Quem le o registro de uma recusa em campo precisa poder comparar com o que o servidor
     * declarou; sem os dois valores, a unica acao possivel e reproduzir o caso.
     */
    @Test
    fun `a recusa por integridade diz o esperado e o calculado`() {
        val recusa = recusado(verificarPacote(ByteArray(0), hash))

        assertTrue(recusa.detalhe.contains(hash), "o detalhe nao traz o hash esperado: ${recusa.detalhe}")
        assertTrue(
            recusa.detalhe.contains(com.platos.domain.hash.Sha256.hex(ByteArray(0))),
            "o detalhe nao traz o hash calculado: ${recusa.detalhe}",
        )
    }

    // ------------------------------------------------------------------ camada (b)

    /**
     * **O cenario central da camada (b)**, e o unico que (a) nao poderia pegar.
     *
     * O pacote e integro, o hash e recalculado sobre ele, e (a) passa. O que ele tem e um campo com
     * valor padrao **omitido** do JSON: `assets` de um item. A desserializacao o injeta em silencio,
     * a reserializacao canonica o escreve de volta, e os bytes deixam de ser os conferidos.
     *
     * E a forma de desalinhamento de versao que ADR-0013 descreve: nao ha excecao, nao ha campo
     * desconhecido, e o parse estrito nao ve nada de errado. So a comparacao ve.
     */
    @Test
    fun `campo com valor padrao omitido e recusado por interpretacao`() {
        val semAssets = texto.replace(""","assets":[]""", "")
        assertNotEquals(texto, semAssets, "a fixture mudou: nao ha `assets` vazio para omitir")

        val recusa = recusado(verificarPacote(semAssets.toByteArray(), hashDe(semAssets)))

        assertEquals(MotivoDaRecusa.INTERPRETACAO, recusa.motivo)
    }

    /**
     * Ordem de campo diferente tambem e recusada, e pelo mesmo motivo.
     *
     * A serializacao canonica fixa a ordem (D-2a.4). Um pacote com os mesmos campos noutra ordem
     * desserializa igual e reserializa na ordem canonica, entao os bytes divergem — que e
     * exatamente o que se quer, porque o hash publicado cobre uma ordem so.
     */
    @Test
    fun `ordem de campo trocada e recusada por interpretacao`() {
        val trocado = texto.replaceFirst(
            """"meta":{"exam_id"""",
            """"items":[],"meta":{"exam_id"""",
        )
        assertNotEquals(texto, trocado, "a fixture mudou: o recorte de ordem nao se aplica")

        val recusa = recusado(verificarPacote(trocado.toByteArray(), hashDe(trocado)))

        assertEquals(MotivoDaRecusa.INTERPRETACAO, recusa.motivo)
    }

    @Test
    fun `campo desconhecido e recusado por interpretacao`() {
        val comExtra = texto.replaceFirst("""{"meta":""", """{"campo_que_nao_existe":1,"meta":""")

        val recusa = recusado(verificarPacote(comExtra.toByteArray(), hashDe(comExtra)))

        assertEquals(MotivoDaRecusa.INTERPRETACAO, recusa.motivo)
    }

    @Test
    fun `bytes que nao sao json sao recusados por interpretacao e nao estouram`() {
        val lixo = "isto nao e json".toByteArray()

        val recusa = recusado(verificarPacote(lixo, hashDe("isto nao e json")))

        assertEquals(MotivoDaRecusa.INTERPRETACAO, recusa.motivo)
    }

    @Test
    fun `json valido que nao e um pacote e recusado por interpretacao`() {
        val outroObjeto = """{"qualquer":"coisa"}"""

        val recusa = recusado(verificarPacote(outroObjeto.toByteArray(), hashDe(outroObjeto)))

        assertEquals(MotivoDaRecusa.INTERPRETACAO, recusa.motivo)
    }

    // ------------------------------------------------------------------ o entorno do hash

    @Test
    fun `hash declarado em maiusculas confere`() {
        val resultado = conferido(verificarPacote(bytes, hash.uppercase()))

        assertEquals(hash, resultado.contentHash)
    }

    @Test
    fun `hash ausente e recusado por nao declarado e nao por integridade`() {
        val recusa = recusado(verificarPacote(bytes, null))

        assertEquals(MotivoDaRecusa.HASH_NAO_DECLARADO, recusa.motivo)
    }

    @Test
    fun `hash vazio e malformado e nao ausente`() {
        val recusa = recusado(verificarPacote(bytes, ""))

        assertEquals(MotivoDaRecusa.HASH_MALFORMADO, recusa.motivo)
    }

    @Test
    fun `hash curto e malformado e nao divergente`() {
        val recusa = recusado(verificarPacote(bytes, hash.dropLast(1)))

        assertEquals(MotivoDaRecusa.HASH_MALFORMADO, recusa.motivo)
    }

    @Test
    fun `hash longo e malformado`() {
        val recusa = recusado(verificarPacote(bytes, hash + "0"))

        assertEquals(MotivoDaRecusa.HASH_MALFORMADO, recusa.motivo)
    }

    @Test
    fun `hash com caractere fora do hexadecimal e malformado`() {
        val recusa = recusado(verificarPacote(bytes, "z".repeat(64)))

        assertEquals(MotivoDaRecusa.HASH_MALFORMADO, recusa.motivo)
    }

    // ----------------------------------------- o pacote do contrato anterior a ADR-0014

    /**
     * **O cenario que prova que a consequencia aceita por ADR-0014 e real, e alta.**
     *
     * A decisao 3 daquele ADR aceitou que acrescentar `params_hash` a `PackageMeta` faria todo
     * pacote publicado antes dela deixar de passar na camada (b): `encodeDefaults = true` injeta
     * `"params_hash":null` que nao estava nos bytes, e reserializar deixa de reproduzir o original.
     * Aceitar uma consequencia e barato; este cenario e o que a torna **verificavel**.
     *
     * O artefato e `fixtures/pacote-do-contrato-anterior.json`, congelado byte a byte antes da
     * regravacao e deliberadamente **nao** regerado — `GoldenWriterTest` registra por que. Depois da
     * regravacao nao sobra nenhum pacote do contrato antigo nesta arvore, entao aquele arquivo e a
     * unica janela.
     *
     * **A recusa e conferida pelo MOTIVO, e nao so por haver recusa** (`rigorous.md` §3). Recusar
     * por integridade e recusar por interpretacao pedem coisas opostas de quem segura o aparelho:
     * a primeira pede tentar de novo, a segunda pede atualizar o aplicativo. Um cenario que so
     * afirmasse "foi recusado" passaria com o motivo errado.
     */
    @Test
    fun `o pacote do contrato anterior e recusado por interpretacao`() {
        val anterior = File(fixtures, "pacote-do-contrato-anterior.json").readBytes()
        val hashDele = MessageDigest.getInstance("SHA-256")
            .digest(anterior)
            .joinToString("") { "%02x".format(it) }

        assertNotEquals(
            hash,
            hashDele,
            "o pacote do contrato anterior tem o mesmo hash do atual: ou ele foi regerado, ou a " +
                "fixture atual foi revertida — nos dois casos este cenario deixou de afirmar algo",
        )

        val recusa = recusado(verificarPacote(anterior, hashDele))

        assertEquals(MotivoDaRecusa.INTERPRETACAO, recusa.motivo)
    }

    /**
     * **A guarda de vacuidade que isola a camada: o mesmo artefato produz os DOIS motivos.**
     *
     * O cenario acima afirma que o pacote do contrato anterior e recusado por **interpretacao**.
     * Sozinho, ele nao exclui que a recusa viesse de **integridade** por alguma razao que ninguem
     * notou — e as duas sao indistinguiveis para uma assercao que so olhasse "foi recusado".
     *
     * Este cenario apresenta **os mesmos bytes** com o hash da fixture **atual**, e exige
     * `INTEGRIDADE`. Com isso, o par afirma o que interessa: o motivo da recusa e escolhido pelo
     * hash declarado, e nao pelo artefato. No cenario irmao a camada (a) passa — os bytes sao
     * exatamente os que aquele hash cobre — e o que reprova e a (b).
     *
     * E o sombreamento de fixture que o `rigorous.md` §3 descreve, e que ja aconteceu duas vezes
     * nesta base. A forma dele aqui seria facil: apresentar o pacote antigo com o hash novo e ler a
     * recusa como se fosse a de interpretacao.
     */
    @Test
    fun `o mesmo pacote antigo com o hash atual e recusado por integridade, e nao por interpretacao`() {
        val anterior = File(fixtures, "pacote-do-contrato-anterior.json").readBytes()

        // `hash` e o da fixture ATUAL — outro artefato, outro hash.
        val recusa = recusado(verificarPacote(anterior, hash))

        assertEquals(
            MotivoDaRecusa.INTEGRIDADE,
            recusa.motivo,
            "os mesmos bytes com um hash que nao os cobre tem de cair na camada (a); se caem na " +
                "(b), as duas camadas nao estao sendo distinguidas neste artefato",
        )
    }

    // ------------------------------------------------------------------ ajudantes

    private val texto: String get() = bytes.decodeToString()

    private fun hashDe(conteudo: String): String = MessageDigest.getInstance("SHA-256")
        .digest(conteudo.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
