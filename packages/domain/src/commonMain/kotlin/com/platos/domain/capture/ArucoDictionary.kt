package com.platos.domain.capture

/**
 * Dicionario ArUco DICT_5X5_100 (D29).
 *
 * Os padroes sao dados, nao algoritmo: o OpenCV os gera por busca gulosa com semente fixa, que nao
 * e reproduzivel a partir da especificacao. Estes 100 marcadores foram extraidos de
 * `modules/objdetect/src/aruco/predefined_dictionaries.hpp` do OpenCV, sob Apache 2.0, e vivem
 * aqui para que o KMP — e nao cada renderizador — decida cada modulo desenhado. E o que mantem os
 * renderizadores burros e a paridade significativa: duas bibliotecas de ArUco diferentes poderiam
 * concordar na posicao do marcador e discordar no padrao.
 *
 * Cada marcador e um Int com 25 bits de dados, linha a linha, do bit 24 ao bit 0. Como no OpenCV,
 * bit ligado e modulo *branco*. O marcador desenhado tem 7 modulos de lado: 5 de dados mais a
 * borda preta de 1 modulo exigida pela deteccao.
 */
internal object ArucoDictionary {

    const val SIZE = 100

    /** Modulos de dados por lado. */
    const val DATA_MODULES = 5

    /** Lado total em modulos, incluindo a borda preta. */
    const val TOTAL_MODULES = DATA_MODULES + 2

    private val MARKERS = intArrayOf(
        21344956, 1836774, 28249820, 17012214, 28226852,
        30672940, 13883372, 14816362, 17654066, 20004772,
        20770306, 27450048, 31861520, 6189414, 33356968,
        5366654, 9938776, 12493422, 16141764, 17178088,
        19782260, 22078528, 23792800, 12194526, 27054114,
        27629938, 29609610, 2245190, 3905138, 2368058,
        2570094, 3573874, 4247758, 4893384, 4604858,
        8024042, 10062508, 8573184, 10136860, 8797298,
        11348004, 10825630, 14174710, 12782040, 14319152,
        15295098, 15317398, 16336902, 16093476, 16168918,
        18569444, 18535032, 18757642, 18256964, 19921738,
        22596492, 22644720, 21114846, 21893670, 22470434,
        24370160, 23382332, 24951686, 25692806, 26298332,
        25488266, 26052012, 28495272, 29015410, 32101426,
        31941928, 16086026, 28383826, 1625700, 2798104,
        10021248, 11011612, 21078360, 25506124, 26661164,
        33215394, 598708, 1686752, 742524, 182126,
        1254024, 1233574, 805698, 424900, 1026572,
        2039636, 3729528, 3291938, 2897478, 2991386,
        3054988, 2606434, 4128244, 5381856, 4493052,    )

    /** Matriz de modulos do marcador [id], borda incluida. `true` e modulo preto. */
    fun modulesOf(id: Int): List<List<Boolean>> {
        require(id in 0 until SIZE) { "marcador ArUco fora de DICT_5X5_100: $id" }
        val marker = MARKERS[id]
        return List(TOTAL_MODULES) { row ->
            List(TOTAL_MODULES) { column ->
                val isBorder = row == 0 || column == 0 ||
                    row == TOTAL_MODULES - 1 || column == TOTAL_MODULES - 1
                if (isBorder) {
                    true
                } else {
                    val index = (row - 1) * DATA_MODULES + (column - 1)
                    (marker shr (24 - index)) and 1 == 0
                }
            }
        }
    }
}
