package com.platos.android.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.platos.android.render.LayoutMapRenderer
import com.platos.domain.exam.ExamPackage
import com.platos.domain.exam.folhaDaAtribuicao
import com.platos.domain.layout.LayoutMap
import kotlinx.serialization.json.Json
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import java.io.File

/**
 * A folha de um aluno da prova com discursiva, desenhada e rasterizada no aparelho
 * (`slice-5b-1-o-aparelho-reconhece-a-discursiva`).
 *
 * **O documento, e nao uma foto.** A pergunta dos testes que usam isto é "o pipeline acha e lê as
 * regiões de uma página que tem mais de uma", e um documento sem perspectiva, sem sombra e sem
 * papel isola essa pergunta. As fotos da folha impressa são outra tarefa (4.2), com outro oráculo.
 *
 * Desenhada pelo **renderizador de produção** (`LayoutMapRenderer`), num PDF, e rasterizada pelo
 * `PdfRenderer` da plataforma — o mesmo caminho que um PDF impresso percorre antes do papel.
 */
class FolhaDiscursivaRenderizada(private val context: Context, private val targetContext: Context) {

    val pacote: ExamPackage by lazy {
        Json { ignoreUnknownKeys = false }.decodeFromString(
            ExamPackage.serializer(),
            context.assets.open("prova-discursiva.package.json").use { it.readBytes().decodeToString() },
        )
    }

    /** A folha de `tok-a`: a geometria da variante com os QRs dele. */
    val folha: LayoutMap by lazy {
        requireNotNull(pacote.folhaDaAtribuicao(TOKEN)) { "o pacote nao tem a atribuicao $TOKEN" }
    }

    /** Uma página da folha em cinza, a [pxPorMm] pixels por milímetro, sobre fundo branco. */
    fun pagina(indice: Int, mapa: LayoutMap = folha, pxPorMm: Int = 10): Mat {
        val pdf = File(targetContext.cacheDir, "folha-discursiva-${System.nanoTime()}.pdf")
        pdf.outputStream().use { LayoutMapRenderer(fonte(), emptyMap()).render(mapa, it) }

        ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY).use { descritor ->
            PdfRenderer(descritor).use { documento ->
                documento.openPage(indice).use { pagina ->
                    // A página do PDF é em pontos; 25,4 mm por polegada, 72 pontos por polegada.
                    val largura = (pagina.width * 25.4 / 72 * pxPorMm).toInt()
                    val altura = (pagina.height * 25.4 / 72 * pxPorMm).toInt()
                    val bitmap = Bitmap.createBitmap(largura, altura, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    pagina.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                    pdf.delete()
                    return cinza(bitmap)
                }
            }
        }
    }

    private fun cinza(bitmap: Bitmap): Mat {
        val colorido = Mat()
        Utils.bitmapToMat(bitmap, colorido)
        val gray = Mat()
        Imgproc.cvtColor(colorido, gray, Imgproc.COLOR_RGBA2GRAY)
        colorido.release()
        return gray
    }

    private fun fonte(): Typeface {
        val cached = File(targetContext.cacheDir, "SourceSerif4-Regular.ttf")
        if (!cached.exists()) {
            context.assets.open("SourceSerif4-Regular.ttf").use { input ->
                cached.outputStream().use { output -> input.copyTo(output) }
            }
        }
        return Typeface.createFromFile(cached)
    }

    companion object {
        const val TOKEN = "tok-a"
    }
}
