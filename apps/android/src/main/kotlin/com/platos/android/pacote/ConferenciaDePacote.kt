package com.platos.android.pacote

import com.platos.domain.exam.ExamPackage
import com.platos.domain.hash.Sha256

/**
 * Por que um pacote foi recusado. Cada um e um estado distinto, e nao um texto.
 *
 * A distincao existe porque as quatro pedem coisas diferentes de quem segura o aparelho: hash
 * ausente e servidor fora do contrato, malformado e o mesmo um passo adiante, integridade pede
 * tentar de novo, e interpretacao pede atualizar o aplicativo.
 */
enum class MotivoDaRecusa {
    /** O servidor entregou o pacote sem declarar o hash. Nao ha contra o que conferir. */
    HASH_NAO_DECLARADO,

    /** O hash declarado nao tem a forma de um SHA-256 hexadecimal. */
    HASH_MALFORMADO,

    /** Camada (a): os bytes obtidos nao sao os que o hash declarado cobre. */
    INTEGRIDADE,

    /** Camada (b): esta versao do aplicativo nao interpreta o pacote por inteiro. */
    INTERPRETACAO,
}

/** O desfecho de uma conferencia. */
sealed interface Conferencia {

    /** Passou nas duas camadas. [contentHash] e o hash conferido, ja normalizado. */
    data class Conferido(val pacote: ExamPackage, val contentHash: String) : Conferencia

    /** Nao passou. [detalhe] e para o registro e para a tela; [motivo] e para o codigo. */
    data class Recusado(val motivo: MotivoDaRecusa, val detalhe: String) : Conferencia
}

/**
 * As duas primeiras camadas de ADR-0013, decisao 4.
 *
 * **(a) integridade de transporte:** `sha256(bytes) == content_hash`. Responde "recebi o que foi
 * publicado?".
 *
 * **(b) fidelidade da interpretacao:** `toCanonicalJson(parse(bytes)) == bytes`. Responde "eu
 * entendi o que recebi?". As duas perguntas sao rotineiramente confundidas, e sao diferentes: um
 * pacote cujo hash confere e cujo parse perdeu um campo produz nota plausivel e errada, e nao ha
 * sintoma na tela.
 *
 * **Nenhuma das duas e autenticidade.** O hash vem do mesmo servidor que os bytes: quem controlar a
 * resposta controla os dois. Autenticidade e do TLS, e escrever isto aqui impede que a conferencia
 * dupla seja lida como uma garantia que ela nao da.
 *
 * A camada (c) — o pacote afirmar que e desta prova e desta variante — **nao mora aqui**, e nao por
 * esquecimento: ela e conferida contra o payload do QR, que so existe quando ha folha na frente da
 * camera. Ela vive em `ScanSession`.
 *
 * Roda tanto sobre o que chegou da API quanto sobre o que foi lido do disco. E a mesma funcao nos
 * dois casos de proposito: uma conferencia que so acontecesse na primeira gravacao valeria uma vez,
 * e todo uso seguinte seria de um arquivo que ninguem mais olhou.
 */
fun verificarPacote(bytes: ByteArray, hashDeclarado: String?): Conferencia {
    if (hashDeclarado == null) {
        return Conferencia.Recusado(
            MotivoDaRecusa.HASH_NAO_DECLARADO,
            "o pacote veio sem hash declarado, e nao ha contra o que conferi-lo",
        )
    }

    // Hexadecimal nao tem caixa: `A` e `a` sao o mesmo nibble. Normalizar antes de comparar evita
    // uma recusa por diferenca que nao existe, sem afrouxar nada — os 64 digitos ainda precisam
    // bater um a um.
    val esperado = hashDeclarado.lowercase()
    if (!FORMATO_DE_HASH.matches(esperado)) {
        return Conferencia.Recusado(
            MotivoDaRecusa.HASH_MALFORMADO,
            "o hash declarado nao tem a forma de um SHA-256 hexadecimal: `$hashDeclarado`",
        )
    }

    // (a). Sobre os bytes recebidos, e nunca sobre o nome do arquivo de onde eles vieram.
    val calculado = Sha256.hex(bytes)
    if (calculado != esperado) {
        return Conferencia.Recusado(
            MotivoDaRecusa.INTEGRIDADE,
            "o conteudo nao confere com o hash declarado: esperado `$esperado`, calculado `$calculado`",
        )
    }

    // (b). O parse e estrito — `ExamPackage.JSON` nao ignora campo desconhecido —, entao campo a
    // mais estoura aqui. O que a comparacao acrescenta ao parse estrito e a injecao de padrao:
    // campo ausente que a desserializacao preenche sozinha volta na reserializacao e muda os bytes,
    // sem nunca ter estourado.
    val pacote = try {
        ExamPackage.JSON.decodeFromString(ExamPackage.serializer(), bytes.decodeToString())
    } catch (erro: Exception) {
        return Conferencia.Recusado(
            MotivoDaRecusa.INTERPRETACAO,
            "esta versao do aplicativo nao interpreta o pacote: ${erro.message}",
        )
    }

    if (!pacote.toCanonicalJson().encodeToByteArray().contentEquals(bytes)) {
        return Conferencia.Recusado(
            MotivoDaRecusa.INTERPRETACAO,
            "esta versao do aplicativo nao interpreta o pacote por inteiro: reserializa-lo nao " +
                "reproduz os bytes conferidos",
        )
    }

    return Conferencia.Conferido(pacote, esperado)
}

private val FORMATO_DE_HASH = Regex("^[0-9a-f]{64}$")
