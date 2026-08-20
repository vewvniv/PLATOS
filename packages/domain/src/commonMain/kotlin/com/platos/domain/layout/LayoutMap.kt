package com.platos.domain.layout

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Primitiva de desenho.
 *
 * O KMP calcula, a plataforma so traduz (§6). Toda coordenada aqui e micrometro inteiro na pagina:
 * o renderizador nao mede, nao quebra linha e nao decide posicao.
 */
@Serializable
sealed interface Primitive {
    val id: String
}

@Serializable
@SerialName("rect")
data class DrawRect(
    override val id: String,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val stroke: Int,
    /** Preenchimento em porcentagem de preto, 0 a 100. Nulo e sem preenchimento. */
    val fill: Int? = null,
) : Primitive

@Serializable
@SerialName("circle")
data class DrawCircle(
    override val id: String,
    @SerialName("center_x") val centerX: Int,
    @SerialName("center_y") val centerY: Int,
    val diameter: Int,
    val stroke: Int,
) : Primitive

@Serializable
@SerialName("text")
data class DrawText(
    override val id: String,
    val x: Int,
    /** Linha de base do texto. */
    val baseline: Int,
    val size: Int,
    val text: String,
) : Primitive

@Serializable
@SerialName("image")
data class DrawImage(
    override val id: String,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val reference: String,
) : Primitive

/**
 * Marcador ArUco com o padrao ja resolvido (D-1.10).
 *
 * [modules] traz uma linha por string, `1` para modulo preto. O renderizador nao consulta
 * dicionario nenhum: se cada plataforma resolvesse o padrao com a sua biblioteca, duas folhas
 * poderiam concordar na posicao do marcador e discordar no desenho dele.
 */
@Serializable
@SerialName("aruco")
data class DrawAruco(
    override val id: String,
    @SerialName("marker_id") val markerId: Int,
    val x: Int,
    val y: Int,
    val side: Int,
    /** Lado de um modulo do marcador; a zona de silencio e medida nele. */
    val module: Int,
    val modules: List<String>,
) : Primitive

/** Codigo QR com a matriz ja resolvida, pela mesma razao de [DrawAruco]. */
@Serializable
@SerialName("qr")
data class DrawQr(
    override val id: String,
    val x: Int,
    val y: Int,
    val side: Int,
    val module: Int,
    val payload: String,
    val modules: List<String>,
) : Primitive

/** Uma bolha do gabarito, em coordenadas normalizadas ao quadrilatero da regiao. */
@Serializable
data class Bubble(
    @SerialName("question_id") val questionId: String,
    val option: String,
    /** Centro em partes por milhao do lado horizontal do quadrilatero. */
    val u: Int,
    /** Centro em partes por milhao do lado vertical do quadrilatero. */
    val v: Int,
)

/** Retangulo em coordenadas normalizadas ao quadrilatero. */
@Serializable
data class NormalizedRect(
    val u: Int,
    val v: Int,
    @SerialName("u_size") val uSize: Int,
    @SerialName("v_size") val vSize: Int,
)

/**
 * Regiao escaneavel: o que a captura procura e o que o OMR le.
 *
 * O quadrilatero e formado pelos *centros* dos quatro marcadores — e o ponto que a deteccao
 * devolve com mais estabilidade. Tudo dentro da regiao e normalizado a ele, o que da imunidade a
 * escala de impressao, tamanho de papel, DPI e distancia da camera (§6).
 */
@Serializable
data class ScannableRegion(
    val index: Int,
    val kind: String,
    val page: Int,
    @SerialName("quad_x") val quadX: Int,
    @SerialName("quad_y") val quadY: Int,
    @SerialName("quad_width") val quadWidth: Int,
    @SerialName("quad_height") val quadHeight: Int,
    @SerialName("marker_ids") val markerIds: List<Int>,
    val qr: NormalizedRect,
    val bubbles: List<Bubble>,
)

@Serializable
data class Page(
    val index: Int,
    val primitives: List<Primitive>,
)

/**
 * A fonte geometrica da folha (§6).
 *
 * Calculado uma vez e persistido; o documento impresso e projecao descartavel e o OMR le daqui,
 * nunca do PDF. Todos os numeros sao inteiros em micrometros ou em partes por milhao, o que torna
 * a serializacao estavel byte a byte e pronta para a fatia 2 tirar hash.
 */
/**
 * O perfil que produziu esta geometria (ADR-0004).
 *
 * O cabecalho ja declarava a fonte por hash, mas nao a tipografia: duas provas com corpos
 * diferentes produziam cabecalhos indistinguiveis. Enquanto havia um perfil so isso era invisivel;
 * a partir do momento em que existe pacote publicado e hasheado, um mapa antigo deixaria de ser
 * reconstituivel — nao daria para saber sob que corpo ele foi calculado.
 *
 * Traz o identificador **e** os valores que o definem. So o identificador obrigaria quem le a ter
 * a tabela de perfis da epoca; so os valores nao diriam qual perfil era, e dois perfis podem
 * coincidir em corpo e diferir no resto.
 */
@Serializable
data class LayoutProfileRef(
    val id: String,
    @SerialName("body_size") val bodySize: Int,
    @SerialName("line_height") val lineHeight: Int,
    val grid: Int,
)

@Serializable
data class LayoutMap(
    @SerialName("layout_engine_version") val layoutEngineVersion: Int,
    @SerialName("min_renderer_version") val minRendererVersion: Int,
    @SerialName("exam_id") val examId: String,
    @SerialName("page_width") val pageWidth: Int,
    @SerialName("page_height") val pageHeight: Int,
    @SerialName("font_sha256") val fontSha256: String,
    val profile: LayoutProfileRef,
    val pages: List<Page>,
    val regions: List<ScannableRegion>,
) {
    companion object {
        /** Versao do Layout Engine que produz este formato (D24). */
        const val ENGINE_VERSION = 1

        /** Versao minima de renderizador capaz de desenhar este formato (D24). */
        const val MIN_RENDERER_VERSION = 1

        /**
         * JSON canonico (D-1.5): sem espacos, ordem de campos fixa pela declaracao, sem valor
         * omitido por padrao. Nenhum numero fracionario existe no modelo, entao nao ha formatacao
         * de ponto flutuante para divergir entre plataformas.
         */
        val json: Json = Json {
            prettyPrint = false
            encodeDefaults = true
            explicitNulls = true
        }
    }

    fun toCanonicalJson(): String = json.encodeToString(serializer(), this)
}
