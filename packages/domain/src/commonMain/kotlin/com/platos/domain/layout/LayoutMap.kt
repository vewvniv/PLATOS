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
    /**
     * Preenchimento em **permilagem** de preto, 0 a 1000. Nulo e sem preenchimento.
     *
     * Permilagem e nao porcentagem porque §7 pede trama de 4,5%, que porcentagem inteira nao
     * representa (D-2b.1). Arredondar para 4% ou 5% seria congelar num artefato hasheado um valor
     * que ninguem escolheu — e 4% e 5% de preto sao tramas distintas a olho e sob o OMR. Fracao de
     * ponto flutuante resolveria a representacao e traria de volta o arredondamento entre alvos
     * que o resto do mapa evita.
     */
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
    /**
     * Tom em permilagem de preto, 0 a 1000. Nulo e preto pleno.
     *
     * Existe para a letra da alternativa dentro do circulo (§7), que precisa ser legivel sem ser
     * confundida com resposta. Quem escolhe o tom e o mapa: um renderizador que decidisse por
     * conta propria reintroduziria a divergencia que a fatia 1 inteira existiu para eliminar, e
     * dessa vez dentro da bolha que o OMR mede.
     */
    val tone: Int? = null,
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
 * Linha reta entre dois pontos, sem arremate alem das extremidades.
 *
 * Existe para a pauta da area de resposta discursiva (ADR-0016), que e guia para o aluno e fica em
 * cinza, do lado decorativo do ADR-0010. E primitiva propria, e nao um campo de tom no retangulo: o
 * JSON canonico escreve ate os nulos, e um `stroke_tone` em [DrawRect] apareceria em todo retangulo de
 * todo mapa, mudando os bytes de provas que nao tem linha nenhuma (a licao do ADR-0014).
 */
@Serializable
@SerialName("line")
data class DrawLine(
    override val id: String,
    val x1: Int,
    val y1: Int,
    val x2: Int,
    val y2: Int,
    /** Espessura do traco. */
    val stroke: Int,
    /** Tom em permilagem de preto, 0 a 1000. Nulo e preto pleno, como em [DrawText]. */
    val tone: Int? = null,
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
/**
 * Orcamento de tinta decorativa da regiao escaneavel (ADR-0010).
 *
 * Viaja no artefato publicado porque quem o consome e a leitura optica, que roda **offline** contra
 * um pacote imutavel possivelmente produzido por uma versao anterior do engine. Uma constante no
 * aplicativo concordaria com a folha por coincidencia de versao, e discordaria em silencio no dia
 * em que uma turma imprimisse com pacote antigo — que e o modelo de pull de referencia imutavel.
 *
 * Todos os valores em permilagem.
 */
@Serializable
data class InkBudget(
    /** Cobertura maxima de tinta decorativa numa bolha **nao respondida**. */
    @SerialName("decorative_max") val decorativeMax: Int,
    /** Teto de tom de qualquer elemento decorativo dentro de uma bolha. */
    @SerialName("decorative_tone_max") val decorativeToneMax: Int,
    /** Piso do corredor onde o limiar da leitura optica podera cair. */
    @SerialName("threshold_floor") val thresholdFloor: Int,
    /** Teto do corredor onde o limiar da leitura optica podera cair. */
    @SerialName("threshold_ceiling") val thresholdCeiling: Int,
) {
    companion object {
        /** Os numeros de ADR-0010, fixados antes da primeira medicao. */
        val DEFAULT = InkBudget(
            decorativeMax = 120,
            decorativeToneMax = 500,
            thresholdFloor = 200,
            thresholdCeiling = 400,
        )
    }
}

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
    @SerialName("ink_budget") val inkBudget: InkBudget = InkBudget.DEFAULT,
    /**
     * O `id` da primitiva `DrawQr` desta regiao, na pagina dela.
     *
     * **A ligacao entre regiao e QR e declarada, e nao inferida** — nem da ordem das primitivas, nem
     * do texto do `id`. E por ela que a folha de uma atribuicao troca cada QR pelo da regiao certa, e
     * a regra roda em duas implementacoes, Kotlin e TypeScript; uma ligacao inferida seria duas
     * inferencias que concordam por coincidencia de emissao. Obrigatorio em toda regiao, inclusive
     * no gabarito e na folha de teste: uma regra para a discursiva e outra para o gabarito seriam
     * duas regras.
     */
    @SerialName("qr_id") val qrId: String,
    /** A questao a que a regiao discursiva pertence. Nula no gabarito e na folha de teste. */
    @SerialName("question_id") val questionId: String? = null,
    /**
     * A area de resposta da regiao discursiva, normalizada ao quadrilatero. Nula no gabarito e na
     * folha de teste. E o que a captura recorta: a moldura contem so a resposta, e o enunciado fica
     * fora (§8).
     */
    @SerialName("answer_area") val answerArea: NormalizedRect? = null,
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

        /** Versao minima de renderizador de um mapa sem linha: a de antes de `line` (D24). */
        const val BASE_RENDERER_VERSION = 1

        /**
         * A versao de renderizador que desenha `line`, e a mais alta que o motor pode exigir hoje
         * (D24). E o registro do dominio que `tools/parity/renderizador.mjs` compara com os dois
         * renderizadores: quem acrescentar a proxima capacidade sobe os renderizadores, e a guarda
         * reprova ate ler o registro novo (decisao 4 da `slice-5b-0-a-regiao-discursiva-compacta`).
         */
        const val LINE_RENDERER_VERSION = 2

        /**
         * A menor versao de renderizador capaz de desenhar todas as primitivas de [pages] (D24).
         *
         * Por mapa, e nao uma constante global: um mapa nao exige capacidade que nao usa, e a prova so
         * objetiva continua saindo identica byte a byte (decisao 4). Motor e folha de teste chamam
         * esta funcao, e a regra mora so aqui (P28).
         */
        fun minRendererVersionOf(pages: List<Page>): Int =
            if (pages.any { page -> page.primitives.any { it is DrawLine } }) {
                LINE_RENDERER_VERSION
            } else {
                BASE_RENDERER_VERSION
            }

        /** Preto pleno na escala de tom e de trama: a permilagem cheia (D-2b.1). */
        const val TONE_FULL = 1000

        /** Teto de trama chapada da folha — §7: "monocromatico, tramas <= 8%". */
        const val FLAT_TONE_CEILING = 80

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
