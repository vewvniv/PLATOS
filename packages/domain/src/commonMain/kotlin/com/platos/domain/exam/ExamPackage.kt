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
)

/** Uma variante: o mapa de posicao fisica na folha para o item que ocupa aquela posicao. */
@Serializable
data class PackageVariant(
    @SerialName("variant_id") val variantId: String,
    /** Posicao na folha -> `item_id`. */
    val positions: Map<String, String>,
)

/**
 * O que distingue a folha de uma atribuicao da folha da variante: o QR dela.
 *
 * **Carrega o conteudo, e nao a geometria.** Posicao, lado e modulo do QR ficam onde sempre
 * estiveram — na primitiva da geometria da variante —, e aqui ficam so o payload e a matriz que dele
 * decorre. E isso que torna "folhas da mesma prova diferem so no QR" verdadeiro **por construcao**:
 * uma folha com o QR em outro lugar nao e representavel.
 *
 * **Um QR, e nao uma lista.** A spec vigente exige exatamente uma regiao escaneavel por folha; QR
 * repetido por regiao e D23, e quando ele chegar muda as duas specs juntas. Lista agora seria a
 * abstracao prematura que a regra 8 proibe.
 *
 * [modules] vem ja codificada, como em `DrawQr`, e pela mesma razao: o payload e resolvido **uma
 * vez**, na publicacao, e ninguem recodifica depois.
 */
@Serializable
data class AssignmentQr(
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
 * **O [qr] e nulavel no tipo, e a validacao e quem recusa a ausencia.** Nao e frouxidao: a spec
 * exige um cenario de recusa para "atribuicao sem folha enderecavel", e campo nao-nulavel tornaria
 * esse estado inconstruivel — a recusa ficaria sem como ser testada. Pacote com atribuicao sem QR
 * existe o tempo suficiente para ser recusado antes de gravar, e nao mais que isso.
 *
 * Prova publicada sem roster nao tem atribuicao nenhuma — e o caso da folha avulsa, em que o campo
 * de aluno do payload fica vazio.
 */
@Serializable
data class PackageAssignment(
    @SerialName("student_token") val studentToken: String,
    @SerialName("variant_id") val variantId: String,
    val qr: AssignmentQr? = null,
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
     * I3: presentes no contrato desde o inicio, vazios enquanto a prova for fixa.
     *
     * Nao sao opcionais por descuido. Prova fixa nao tem artefato de IA, e a fatia 6 preenche estes
     * campos sem mexer no contrato — que e exatamente o que I3 existe para garantir: quando a
     * geracao chegar, nao ha o que retrofitar.
     */
    @SerialName("prompt_version") val promptVersion: String? = null,
    @SerialName("model_id") val modelId: String? = null,
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
    val qr = requireNotNull(atribuicao.qr) {
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
private fun LayoutMap.comQrDe(qr: AssignmentQr): LayoutMap {
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
