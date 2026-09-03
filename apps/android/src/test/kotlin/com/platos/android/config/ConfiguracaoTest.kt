package com.platos.android.config

import com.platos.android.BuildConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * A configuracao chegou ao aplicativo, e chegou util.
 *
 * O build ja recusa valor ausente (tarefa 2.2), mas essa recusa vive no `build.gradle.kts` e nao e
 * alcancada por teste nenhum. O que estes cenarios cobrem e o outro lado: que o valor exigido
 * atravessou ate `BuildConfig` em vez de virar string vazia no caminho, e que chegou na forma que
 * quem chama assume.
 *
 * Eles rodam no CI com os valores de marcador, e continuam validos: o que se afirma aqui e forma, e
 * nao qual projeto. Afirmar o projeto exigiria versionar o projeto.
 */
class ConfiguracaoTest {

    @Test
    fun osTresValoresChegaramAoAplicativo() {
        assertFalse(BuildConfig.SUPABASE_URL.isBlank(), "SUPABASE_URL chegou vazia")
        assertFalse(BuildConfig.SUPABASE_ANON_KEY.isBlank(), "SUPABASE_ANON_KEY chegou vazia")
        assertFalse(BuildConfig.API_URL.isBlank(), "API_URL chegou vazia")
    }

    @Test
    fun asUrlsSaoHttps() {
        // Nao e preferencia: com `targetSdk 35` o Android recusa trafego em claro antes de abrir
        // soquete, e a falha sairia como politica de rede disfarcada de falha de transporte -- que
        // o classificador da entrada apresenta como "sem rede".
        for ((nome, url) in urls()) {
            assertTrue(url.startsWith("https://"), "$nome nao e https: $url")
        }
    }

    @Test
    fun asUrlsNaoTerminamEmBarra() {
        // Quem chama concatena "$urlBase/auth/v1/...". Com barra final o pedido vira
        // "https://projeto//auth/v1/...", que alguns servidores aceitam e outros nao: defeito que
        // depende do servidor e nao aparece em teste de unidade nenhum.
        for ((nome, url) in urls()) {
            assertEquals(url.trimEnd('/'), url, "$nome termina em barra")
        }
    }

    private fun urls() = listOf(
        "SUPABASE_URL" to BuildConfig.SUPABASE_URL,
        "API_URL" to BuildConfig.API_URL,
    )
}
