package com.platos.domain.hash

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Confere o SHA-256 contra os vetores publicados da norma.
 *
 * **Oracle independente.** Estes valores não saem de nenhuma linha deste repositório: são os
 * vetores de teste do FIPS 180-4 e do NESSIE, que qualquer implementação correta reproduz. É o que
 * torna esta verificação diferente de conferir a implementação contra ela mesma.
 *
 * Roda em `commonTest`, então JVM, Node e Android calculam o mesmo hash. Se um alvo divergisse —
 * por semântica de `Int`, por estouro, por deslocamento com sinal —, o `content_hash` do
 * `ExamPackage` deixaria de ser verificável no dispositivo que consome o pacote, que é justamente
 * a garantia que ele existe para dar.
 */
class Sha256Test {

    @Test
    fun `vetores da norma`() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            Sha256.hex(""),
            "cadeia vazia",
        )
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            Sha256.hex("abc"),
        )
        assertEquals(
            "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
            Sha256.hex("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq"),
            "vetor de 56 bytes: exercita o bloco de preenchimento que transborda",
        )
        assertEquals(
            "cf5b16a778af8380036ce59e7b0492370b249b11e8f07a51afac45037afee9d1",
            Sha256.hex(
                "abcdefghbcdefghicdefghijdefghijkefghijklfghijklmghijklmnhijklmno" +
                    "ijklmnopjklmnopqklmnopqrlmnopqrsmnopqrstnopqrstu",
            ),
            "vetor de 112 bytes: exercita mais de um bloco",
        )
    }

    @Test
    fun `bordas do preenchimento`() {
        // 55 bytes cabem no mesmo bloco do comprimento; 56 forcam um bloco extra. E onde uma
        // implementacao errada de padding quebra, e onde ela costuma passar despercebida.
        assertEquals(
            "9f4390f8d30c2dd92ec9f095b65e2b9ae9b0a925a5258e241c9f1e910f734318",
            Sha256.hex("a".repeat(55)),
        )
        assertEquals(
            "b35439a4ac6f0948b6d6f9e3c6af0f5f590ce20f1bde7090ef7970686ec6738a",
            Sha256.hex("a".repeat(56)),
        )
        assertEquals(
            "ffe054fe7ae0cb6dc65c3af9b61d5209f439851db43d0ba5997337df154668eb",
            Sha256.hex("a".repeat(64)),
        )
    }

    @Test
    fun `bytes fora do ASCII`() {
        // O pacote traz acentuacao em enunciado. Se o hash dependesse da codificacao escolhida por
        // alvo, ele divergiria em prova de verdade e nao nos vetores da norma.
        assertEquals(
            "0664077f33cc3ebbaa4bbdacac0eb70e740983080f01dce29929e73b7785a7ad",
            Sha256.hex("ação"),
            "o hash precisa vir dos bytes UTF-8, e nao da codificacao que cada alvo preferir",
        )
    }

    @Test
    fun `entrada diferente da hash diferente`() {
        assertEquals(false, Sha256.hex("a") == Sha256.hex("b"))
    }
}
