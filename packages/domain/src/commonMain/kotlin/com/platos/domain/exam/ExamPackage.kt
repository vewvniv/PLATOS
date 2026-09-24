package com.platos.domain.exam

import com.platos.domain.hash.Sha256
import com.platos.domain.layout.DrawQr
import com.platos.domain.layout.LayoutMap
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** O pacote publicado nao pode ser montado a partir da entrada dada. Nada e gravado. */
class ExamPackageException(message: String) : IllegalArgumentException(message)

/**
 * Quanto a habilidade declarada realmente descreve o objeto da questao.
 *
 * Existe porque a BNCC **nao cobre tudo o que uma prova de ensino basico pergunta**. Vetores nao
 * aparecem na Matematica da BNCC; matrizes e determinantes nao tem habilidade propria; a notacao de
 * somatorio nao existe no documento. Nesses casos o codigo usado e ancora curricular consensual, e
 * nao descricao do objeto.
 *
 * Guardar so o codigo apagaria essa diferenca, e o boletim de M3 somaria "a turma domina
 * EM13MAT301" a partir de uma questao de matriz que nao e sobre sistemas lineares. A distorcao seria
 * silenciosa e apareceria como diagnostico, que e onde ela custa mais caro.
 */
@Serializable
enum class SkillCoverage {
    /** A habilidade descreve explicitamente o objeto da questao. */
    @SerialName("direct")
    DIRECT,

    /** O objeto e pre-requisito ou caso particular da habilidade, mas ela nao o nomeia. */
    @SerialName("partial")
    PARTIAL,

    /** Nao ha habilidade para o objeto; o codigo e a ancora curricular mais proxima. */
    @SerialName("anchor")
    ANCHOR,
}

/** Uma habilidade atribuida a um item, com a qualidade da atribuicao. */
@Serializable
data class ItemSkill(
    val code: String,
    val coverage: SkillCoverage,
)

/**
 * Um item do pacote: a questao como ela e publicada.
 *
 * [skills] e obrigatoria e nao pode ser vazia — e a forma executavel de I1. A invariante existe
 * porque retro-marcar milhares de itens gerados e caro e impreciso, e uma barreira que so aparece
 * na fatia que gera itens chegaria tarde: este e o primeiro artefato publicado do projeto, e e aqui
 * que a regra passa a ter dente.
 */
@Serializable
data class PackageItem(
    val id: String,
    val statement: String,
    val options: List<String>,
    /** Habilidades do curriculo que o item cobre. Nunca vazia (I1). */
    val skills: List<ItemSkill>,
    /** Referencias de formula em bloco e em linha que o item usa. */
    val assets: List<String> = emptyList(),
    /** Objetivo ou discursivo. E daqui que quem le o pacote sabe se o item tem gabarito ou rubrica. */
    val kind: QuestionKind = QuestionKind.OBJECTIVE,
    /** A rubrica analitica do item discursivo. Nula no objetivo. */
    val rubric: Rubric? = null,
    /** O modo de captura da resposta discursiva, ja resolvido: nunca nulo na discursiva. */
    @SerialName("answer_capture_mode") val answerCaptureMode: AnswerCaptureMode? = null,
)

/** Uma variante: o mapa de posicao fisica na folha para o item que ocupa aquela posicao. */
@Serializable
data class PackageVariant(
    @SerialName("variant_id") val variantId: String,
    /** Posicao na folha -> `item_id`. */
    val positions: Map<String, String>,
)

/**
 * O que distingue uma regiao da folha de uma atribuicao da mesma regiao da folha da variante: o QR
 * dela.
 *
 * **Carrega o conteudo, e nao a geometria.** Posicao, lado e modulo do QR ficam onde sempre
 * estiveram — na primitiva da geometria da variante —, e aqui ficam so o payload e a matriz que dele
 * decorre. E isso que torna "folhas da mesma prova diferem so nos QRs" verdadeiro **por construcao**:
 * uma folha com o QR em outro lugar nao e representavel.
 *
 * **Um por regiao, e por isso lista** (D23). Esta classe se chamava `AssignmentQr` e era um so, e a
 * KDoc dela dizia: "QR repetido por regiao e D23, e quando ele chegar muda as duas specs juntas. Lista
 * agora seria a abstracao prematura que a regra 8 proibe." Chegou com a regiao discursiva
 * (`slice-5a-regiao-discursiva`), e as duas specs mudaram juntas.
 *
 * [regionIndex] e o indice da regiao do layout a que este QR pertence. O payload tambem o carrega, e
 * a coerencia do pacote confere que os dois concordam.
 *
 * [modules] vem ja codificada, como em `DrawQr`, e pela mesma razao: o payload e resolvido **uma
 * vez**, na publicacao, e ninguem recodifica depois.
 */
@Serializable
data class RegionQr(
    @SerialName("region_index") val regionIndex: Int,
    val payload: String,
    val modules: List<String>,
)

/**
 * Uma atribuicao: qual variante cabe a qual aluno, e o QR que identifica a folha dele.
 *
 * **Traz apenas o token, e e aqui que I5 e decidida.** Nome, turma e matricula vivem no roster, que
 * e mutavel e nao entra neste artefato. A diferenca entre "fora do hash" e "fora do pacote" e o que
 * importa: campo dentro do pacote e excluido do hash continua sendo dado pessoal dentro de um
 * artefato imutavel copiado para dispositivo offline (ADR-0002).
 *
 * **[qrs] pode vir vazia no tipo, e a validacao e quem recusa.** Nao e frouxidao: a spec exige um
 * cenario de recusa para "atribuicao sem folha enderecavel", e uma lista que nao pudesse ser vazia
 * tornaria esse estado inconstruivel — a recusa ficaria sem como ser testada. Pacote com atribuicao
 * sem QR existe o tempo suficiente para ser recusado antes de gravar, e nao mais que isso. A forma
 * anterior era `qr: AssignmentQr?`, nulavel pela mesma razao.
 *
 * Prova publicada sem roster nao tem atribuicao nenhuma — e o caso da folha avulsa, em que o campo
 * de aluno do payload fica vazio.
 */
@Serializable
data class PackageAssignment(
    @SerialName("student_token") val studentToken: String,
    @SerialName("variant_id") val variantId: String,
    /** Um QR por regiao do layout da variante, em ordem de indice (D23). */
    val qrs: List<RegionQr> = emptyList(),
)

/** Gabarito: item -> alternativa correta e pontuacao. */
@Serializable
data class AnswerKeyEntry(
    @SerialName("item_id") val itemId: String,
    val correct: String,
    val points: Int,
)

/** Metadados do pacote (§5). */
@Serializable
data class PackageMeta(
    @SerialName("exam_id") val examId: String,
    @SerialName("layout_engine_version") val layoutEngineVersion: Int,
    @SerialName("min_renderer_version") val minRendererVersion: Int,
    @SerialName("fully_offline_gradable") val fullyOfflineGradable: Boolean,
    /**
     * A tripla de proveniencia que I3 exige: **os tres**, e nao dois (ADR-0014).
     *
     * **Nulos enquanto a prova for fixa**, e nao opcionais por descuido: prova fixa nao tem artefato
     * de IA, e nao ha proveniencia a declarar. Quando a geracao chegar, a fatia 6 preenche os tres
     * sem mexer no contrato — que e o que I3 existe para garantir.
     *
     * **Nulo nao e vazio.** Um dos tres com valor vazio significa "houve geracao e o valor e vazio",
     * que e um estado impossivel e portanto um defeito detectavel; nulo significa "nao houve
     * geracao". E a mesma distincao que a folha avulsa ja fixou para `student_token`, e pela mesma
     * razao: dois estados colapsados num valor so produzem leitura plausivel e errada, sem sintoma.
     *
     * [paramsHash] cobre os **parametros da chamada** que produziu o artefato — o que foi enviado ao
     * modelo alem do prompt. Ele existe para que dois artefatos produzidos com parametros diferentes
     * sejam distinguiveis sem inferir a partir do conteudo, que e o que [promptVersion] e [modelId]
     * sozinhos nao dao.
     *
     * *A redacao anterior desta KDoc afirmava que a fatia 6 preencheria os campos "sem mexer no
     * contrato — nao ha o que retrofitar". Era **falsa no campo que faltava**: o pacote e hasheado
     * sobre a serializacao canonica com `encodeDefaults = true`, entao acrescentar [paramsHash]
     * mudou o `content_hash` de todo pacote. Fica dito em vez de apagado (P7) — dois de tres nao era
     * "nao ha o que retrofitar", e o custo foi exatamente o que os outros dois existiam para
     * evitar.*
     */
    @SerialName("prompt_version") val promptVersion: String? = null,
    @SerialName("model_id") val modelId: String? = null,
    @SerialName("params_hash") val paramsHash: String? = null,
)

/**
 * O contrato central (§5): produzido na publicacao, imutavel, identificado por hash.
 *
 * Tipo do dominio compartilhado, e nao do servidor, porque **os dois renderizadores precisam
 * le-lo** (D-2a.1). Duas interpretacoes independentes do mesmo artefato sao a divergencia que a
 * fatia 1 inteira existiu para eliminar.
 */
@Serializable
data class ExamPackage(
    val meta: PackageMeta,
    val items: List<PackageItem>,
    val variants: List<PackageVariant>,
    val assignments: List<PackageAssignment>,
    /**
     * Layout por variante. A geometria e a que o Layout Engine produziu; o pacote a embute.
     *
     * **Uma geometria por variante, e nao uma por aluno.** O que distingue a folha de cada
     * atribuicao e o QR dela, que viaja em [PackageAssignment.qr]; a folha de um aluno se obtem por
     * [folhaDaAtribuicao]. Guardar um layout completo por aluno custaria 2,64 MB para trinta alunos
     * contra 129,2 KB desta forma, medido sobre a fixture de referencia — e e este artefato que o
     * aparelho puxa, confere por hash e cacheia.
     */
    val layout: Map<String, LayoutMap>,
    @SerialName("answer_key") val answerKey: List<AnswerKeyEntry>,
    val scoring: Scoring,
) {

    /**
     * Serializacao canonica, a mesma do `LayoutMap` (D-2a.4).
     *
     * Reusar e o que evita a armadilha classica de hashear JSON com ordem de campo nao
     * deterministica: este caminho ja e comparado byte a byte entre tres alvos desde a fatia 1, e
     * se ele variasse o golden teria quebrado muito antes de existir pacote.
     */
    fun toCanonicalJson(): String = JSON.encodeToString(serializer(), this)

    /** `content_hash`: SHA-256 sobre a serializacao canonica. */
    fun contentHash(): String = Sha256.hex(toCanonicalJson())

    companion object {
        val JSON: Json = Json {
            prettyPrint = false
            encodeDefaults = true
            explicitNulls = true
        }
    }
}

/** Pesos e composicao da nota. */
@Serializable
data class Scoring(
    @SerialName("max_score") val maxScore: Int,
)

/**
 * A folha de uma atribuicao: a geometria da variante dela, com o QR dela no lugar do da variante.
 *
 * **A regra e mecanica de proposito**, porque ela e executada duas vezes — uma em Kotlin e uma no
 * renderizador do web, que le o mesmo JSON (D-2a.1). Trocar dois campos de uma primitiva e o
 * bastante pequeno para as duas implementacoes coincidirem, e a paridade entre plataformas e quem
 * pega divergencia se elas nao coincidirem.
 *
 * Devolve `null` quando o token nao tem atribuicao neste pacote, e **estoura** quando a atribuicao
 * existe e a variante dela nao tem layout: a primeira e pergunta legitima de quem le uma folha
 * desconhecida, a segunda e pacote incoerente, que a validacao recusa antes de gravar.
 */
fun ExamPackage.folhaDaAtribuicao(studentToken: String): LayoutMap? {
    val atribuicao = assignments.firstOrNull { it.studentToken == studentToken } ?: return null
    val geometria = requireNotNull(layout[atribuicao.variantId]) {
        "pacote incoerente: a atribuicao `$studentToken` aponta a variante `${atribuicao.variantId}`, " +
            "que nao tem layout"
    }
    // Minimo para compilar sobre o contrato novo: ainda um QR so, o da regiao 0. A troca por
    // `qr_id` de cada regiao e a tarefa 4.3 da `slice-5a-regiao-discursiva`.
    val qr = requireNotNull(atribuicao.qrs.singleOrNull { it.regionIndex == 0 }) {
        "pacote incoerente: a atribuicao `$studentToken` nao tem QR proprio"
    }

    return geometria.comQrDe(qr)
}

/**
 * A mesma geometria, com o payload e a matriz do QR substituidos.
 *
 * Exige **exatamente um** QR no mapa, e essa exigencia e a spec vigente escrita em codigo: toda
 * folha tem uma regiao escaneavel, com um QR dentro dela. Zero ou dois seria mapa que a validacao do
 * layout nao deveria ter deixado passar, e trocar "o primeiro que aparecer" esconderia isso.
 */
private fun LayoutMap.comQrDe(qr: RegionQr): LayoutMap {
    val quantos = pages.sumOf { pagina -> pagina.primitives.count { it is DrawQr } }
    require(quantos == 1) { "esperava exatamente um QR no layout, e o mapa tem $quantos" }

    return copy(
        pages = pages.map { pagina ->
            pagina.copy(
                primitives = pagina.primitives.map { primitiva ->
                    if (primitiva is DrawQr) {
                        primitiva.copy(payload = qr.payload, modules = qr.modules)
                    } else {
                        primitiva
                    }
                },
            )
        },
    )
}
