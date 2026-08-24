package com.platos.domain.capture

/**
 * Os campos que o QR de uma regiao carrega, ja separados (§8).
 *
 * `studentToken` e `variant` vem vazios enquanto `exam_assignment` e variantes nao existirem —
 * fatia 7. O tipo os declara desde ja porque a **forma impressa** ja e a definitiva: o dia em que
 * forem preenchidos, nada aqui muda.
 */
data class CapturePayload(
    val examShortId: String,
    val studentToken: String,
    val variant: String,
    val regionIndex: Int,
)

/** O que saiu de uma tentativa de ler o QR: ou os campos, ou o motivo de a folha ser recusada. */
sealed interface PayloadReading {
    data class Read(val payload: CapturePayload) : PayloadReading

    data class Rejected(val reason: String) : PayloadReading
}

/**
 * O payload auto-descritivo do QR de cada regiao (§8).
 *
 * Forma definitiva: `{exam_short_id}.{student_token}.{variant}.{region_idx}.{crc}`.
 *
 * **Por que isto e um lugar so.** Ate a fatia 2b existiam dois escritores — o Layout Engine e a
 * folha de teste de impressao — e cada um tinha a sua copia do `crc16`, identica por coincidencia.
 * Nao havia leitor, entao a duplicacao nao tinha como aparecer. A leitura optica e o terceiro
 * consumidor e o primeiro que le: um leitor que divirja dos escritores nao quebra teste nenhum,
 * ele atribui a folha ao aluno errado, em silencio, no aparelho do professor. Escritor e leitor da
 * mesma regra moram juntos.
 *
 * §8 tambem explica por que o payload carrega tudo isso: com a captura auto-descritiva **nao
 * existe estado de sessao para corromper**. O professor pode escanear fora de ordem, embaralhar as
 * folhas ou ser interrompido, que nada disso produz atribuicao errada.
 */
object QrPayload {

    /** Separador dos campos. Nenhum campo pode conte-lo, ou a leitura de volta e ambigua. */
    private const val SEPARATOR = '.'

    /** `{exam_short_id}.{student_token}.{variant}.{region_idx}.{crc}` — cinco campos, sempre. */
    private const val FIELD_COUNT = 5

    /**
     * Payload de uma regiao de prova.
     *
     * `student_token` e `variant` ficam **vazios**, e nao inventados: `exam_assignment` e variantes
     * sao fatia 7. A forma ja e a definitiva, entao o dia em que os dois campos existirem nada
     * muda no leitor nem no formato impresso.
     */
    fun of(examShortId: String, regionIndex: Int): String {
        require(!examShortId.contains(SEPARATOR)) {
            "identificador de prova nao pode conter '$SEPARATOR': $examShortId"
        }
        require(regionIndex >= 0) { "indice de regiao nao pode ser negativo: $regionIndex" }
        val body = "$examShortId$SEPARATOR$SEPARATOR$SEPARATOR$regionIndex"
        return "$body$SEPARATOR${crc16(body)}"
    }

    /**
     * Le de volta o que [of] escreveu.
     *
     * Recusa em vez de lancar, e com motivo legivel: o chamador e a leitura optica, para quem uma
     * folha ilegivel e ocorrencia esperada — camera tremida, dobra no papel, folha de outra prova
     * na pilha. Excecao ali viraria `try`/`catch` em volta do laco de captura, e um `catch` largo
     * engoliria tambem o defeito que ninguem previu.
     *
     * O CRC e conferido **sobre o corpo exato lido**, e nao sobre o corpo reconstruido a partir
     * dos campos: reconstruir normalizaria justamente a corrupcao que o CRC existe para pegar.
     */
    fun read(text: String): PayloadReading {
        val fields = text.split(SEPARATOR)
        if (fields.size != FIELD_COUNT) {
            return PayloadReading.Rejected(
                "payload tem ${fields.size} campos, e §8 define $FIELD_COUNT: '$text'",
            )
        }

        val body = fields.subList(0, FIELD_COUNT - 1).joinToString(SEPARATOR.toString())
        val expected = crc16(body)
        val found = fields[FIELD_COUNT - 1]
        if (found != expected) {
            return PayloadReading.Rejected("CRC do payload e '$found', mas o conteudo da '$expected'")
        }

        val examShortId = fields[0]
        if (examShortId.isEmpty()) {
            return PayloadReading.Rejected("payload sem identificador de prova: '$text'")
        }

        val regionIndex = fields[3].toIntOrNull()
        if (regionIndex == null || regionIndex < 0) {
            return PayloadReading.Rejected("indice de regiao invalido no payload: '${fields[3]}'")
        }

        return PayloadReading.Read(
            CapturePayload(
                examShortId = examShortId,
                studentToken = fields[1],
                variant = fields[2],
                regionIndex = regionIndex,
            ),
        )
    }

    /**
     * CRC-16/CCITT-FALSE, para que o QR detecte leitura corrompida (D6).
     *
     * Quatro digitos hexadecimais maiusculos, com zero a esquerda: comprimento fixo mantem o
     * payload previsivel e a leitura de volta trivial.
     */
    internal fun crc16(text: String): String {
        var crc = 0xFFFF
        for (byte in text.encodeToByteArray()) {
            crc = crc xor ((byte.toInt() and 0xFF) shl 8)
            repeat(8) {
                crc = if (crc and 0x8000 != 0) {
                    ((crc shl 1) xor 0x1021) and 0xFFFF
                } else {
                    (crc shl 1) and 0xFFFF
                }
            }
        }
        return crc.toString(16).uppercase().padStart(4, '0')
    }
}
