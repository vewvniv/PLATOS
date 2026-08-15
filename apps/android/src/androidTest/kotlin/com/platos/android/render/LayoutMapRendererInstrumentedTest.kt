package com.platos.android.render

import android.graphics.Typeface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.platos.domain.layout.LayoutMap
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Gera o PDF do lado Android a partir do golden e o exporta para o job de paridade.
 *
 * Precisa de emulador ou aparelho: `PdfDocument` e `Canvas` sao implementacoes do sistema, e e
 * exatamente por isso que o teste existe — o que se quer medir e o desenho real da plataforma,
 * nao um duble.
 *
 * O PDF sai no diretorio que o AGP indica por `additionalTestOutputDir` e ele proprio recolhe para
 * `build/outputs/connected_android_test_additional_output/`. Nao adianta gravar em `filesDir` e
 * puxar depois com `adb`: o AGP desinstala o APK ao fim da execucao e leva o diretorio junto.
 */
@RunWith(AndroidJUnit4::class)
class LayoutMapRendererInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().context
    private val targetContext = InstrumentationRegistry.getInstrumentation().targetContext

    private fun golden(): LayoutMap {
        val json = context.assets.open("prova-referencia.layout.json")
            .bufferedReader()
            .use { it.readText() }
        return Json.decodeFromString(LayoutMap.serializer(), json)
    }

    /** Diretorio que sobrevive a desinstalacao do APK, porque o AGP o recolhe. */
    private fun outputDir(): File {
        val declared = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
        val directory = if (declared != null) File(declared) else targetContext.filesDir
        directory.mkdirs()
        return directory
    }

    private fun embeddedTypeface(): Typeface {
        // O Typeface precisa de arquivo; copia o TTF versionado dos assets para o cache.
        val cached = File(targetContext.cacheDir, "SourceSerif4-Regular.ttf")
        if (!cached.exists()) {
            context.assets.open("SourceSerif4-Regular.ttf").use { input ->
                cached.outputStream().use { output -> input.copyTo(output) }
            }
        }
        return Typeface.createFromFile(cached)
    }

    @Test
    fun geraPdfDaFixtureParaOJobDeParidade() {
        val map = golden()
        val output = File(outputDir(), "android.pdf")

        output.outputStream().use { stream ->
            LayoutMapRenderer(embeddedTypeface()).render(map, stream)
        }

        assertTrue("o PDF do Android nao foi escrito", output.exists())
        assertTrue("o PDF do Android saiu vazio", output.length() > 1_000)

        val header = output.inputStream().use { input ->
            ByteArray(5).also { input.read(it) }.decodeToString()
        }
        assertEquals("%PDF-", header)
    }

    @Test
    fun recusaImprimirQuandoOMapaExigeRenderizadorMaisNovo() {
        val exigente = golden().copy(
            minRendererVersion = RendererContract.RENDERER_VERSION + 1,
        )
        val output = File(outputDir(), "nao-deve-existir.pdf")
        output.delete()

        var recusou = false
        try {
            output.outputStream().use { stream ->
                LayoutMapRenderer(embeddedTypeface()).render(exigente, stream)
            }
        } catch (expected: RendererVersionException) {
            recusou = true
        }

        assertTrue("o renderizador deveria ter recusado o mapa", recusou)
        assertEquals("nenhum conteudo deveria ter sido escrito", 0L, output.length())
    }
}
