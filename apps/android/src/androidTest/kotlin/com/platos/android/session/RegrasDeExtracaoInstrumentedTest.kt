package com.platos.android.session

import android.content.res.XmlResourceParser
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser

/**
 * O que o aplicativo **instalado** declara sobre copia automatica.
 *
 * **Le o manifesto binario do APK instalado, e nao o arquivo do repositorio, e a escolha e o ponto
 * deste teste.** A
 * classe de falha que originou esta mudanca e exatamente "a intencao esta escrita no arquivo e nao
 * alcanca o sistema": o manifesto dizia `allowBackup="false"` com a intencao declarada de que o dado
 * nao sai do aparelho, e a transferencia entre aparelhos levava o roster mesmo assim. Um teste que
 * lesse `src/main/res/xml/` afirmaria o que alguem escreveu. Este abre o `AndroidManifest.xml`
 * **de dentro do APK instalado**, encontra o identificador de recurso para onde
 * `android:dataExtractionRules` aponta, e percorre o recurso que o empacotamento produziu — que e
 * outro oraculo, e e o independente dos dois. E a mesma escolha de `ConnectionRoleTest`, que le
 * `pg_class` em vez de uma constante do repositorio.
 *
 * *A primeira versao lia `applicationInfo.dataExtractionRulesRes`, que nao e SDK publico e nao
 * compila. O caminho pelo manifesto empacotado e melhor de qualquer forma: ele afirma tambem que o
 * atributo sobreviveu a mesclagem e ao empacotamento, e nao so que o sistema o carregou.*
 *
 * **O que ele NAO prova, e fica dito (P8):** que o dado de fato nao atravessa. Isso e do
 * `BackupManagerService`, e so o aparelho o responde — sob `bmgr`, com um transporte de
 * transferencia selecionado. A medicao esta em `docs/cobertura-transferencia-entre-aparelhos.md`, e
 * e ela que fecha a mudanca. Este teste e a guarda barata que impede a regra de sumir sem que nada
 * fique vermelho entre uma medicao e a seguinte.
 *
 * **Os tres dominios sao afirmados um a um, e a ausencia de um nao passa.** Negar so `file`
 * deixaria a fila de pendentes atravessando, porque `outbox.db` nao e arquivo comum — e banco, e
 * mora noutra arvore do diretorio de dados. Foi o que a medicao encontrou, e e a forma pela qual
 * esta regra falharia pela metade sem sintoma.
 */
@RunWith(AndroidJUnit4::class)
class RegrasDeExtracaoInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /** O que a mudanca nega. `sharedpref` entrou porque a medicao o encontrou no fluxo. */
    private val dominiosNegados = setOf("file", "database", "sharedpref")

    @Test
    fun o_aplicativo_instalado_declara_regras_de_extracao() {
        assertNotEquals(
            "O APK instalado nao declara `android:dataExtractionRules` no `<application>`. Sem " +
                "ele, a transferencia entre aparelhos volta a levar o diretorio de dados inteiro " +
                "— roster com nome de aluno e fila de pendentes.",
            0,
            recursoDeRegras(),
        )
    }

    @Test
    fun a_transferencia_entre_aparelhos_nega_os_tres_dominios() {
        val negados = dominiosExcluidosEm("device-transfer")

        assertEquals(
            "A transferencia entre aparelhos nao nega os tres dominios medidos. Faltando um, o " +
                "dado que vive nele atravessa, e nada mais nesta suite percebe.",
            dominiosNegados,
            negados,
        )
    }

    @Test
    fun o_backup_em_nuvem_nega_os_mesmos_tres() {
        val negados = dominiosExcluidosEm("cloud-backup")

        assertEquals(
            "As duas secoes devem dizer a mesma coisa. Uma `cloud-backup` sem exclusoes afirma " +
                "que a nuvem pode levar tudo, e contradiz o requisito — mesmo com " +
                "`allowBackup=\"false\"` barrando o caminho hoje.",
            dominiosNegados,
            negados,
        )
    }

    @Test
    fun a_exclusao_cobre_a_raiz_de_cada_dominio() {
        val caminhos = caminhosExcluidosEm("device-transfer")

        assertTrue(
            "Exclusao por nome de arquivo silencia quando alguem acrescenta o proximo. Cada " +
                "dominio deve ser negado por inteiro. Caminhos declarados: $caminhos",
            caminhos.isNotEmpty() && caminhos.all { it == "." },
        )
    }

    /** Os `domain` dos `<exclude>` que vivem dentro de [secao]. */
    private fun dominiosExcluidosEm(secao: String): Set<String> =
        excluoesEm(secao).map { it.first }.toSet()

    /** Os `path` dos `<exclude>` que vivem dentro de [secao]. */
    private fun caminhosExcluidosEm(secao: String): List<String> =
        excluoesEm(secao).map { it.second }

    /**
     * Os pares `domain`/`path` de cada `<exclude>` sob [secao].
     *
     * Percorre o recurso do aplicativo instalado. A profundidade e conferida em vez de suposta: um
     * `<exclude>` fora da secao certa nao vale, e sem este cuidado as duas secoes do arquivo se
     * confundiriam e cada afirmacao passaria pelo conteudo da outra.
     */
    private fun excluoesEm(secao: String): List<Pair<String, String>> {
        val encontrados = mutableListOf<Pair<String, String>>()
        val parser: XmlResourceParser = context.resources.getXml(recursoDeRegras())

        parser.use {
            var dentro = false
            var evento = it.eventType
            while (evento != XmlPullParser.END_DOCUMENT) {
                when (evento) {
                    XmlPullParser.START_TAG -> when {
                        it.name == secao -> dentro = true
                        dentro && it.name == "exclude" -> {
                            val dominio = it.getAttributeValue(ANDROID, "domain")
                                ?: it.getAttributeValue(null, "domain")
                            val caminho = it.getAttributeValue(ANDROID, "path")
                                ?: it.getAttributeValue(null, "path")
                            if (dominio != null) encontrados += dominio to (caminho ?: "")
                        }
                    }

                    XmlPullParser.END_TAG -> if (it.name == secao) dentro = false
                }
                evento = it.next()
            }
        }

        return encontrados
    }

    /**
     * O identificador de recurso para onde `android:dataExtractionRules` aponta **no APK
     * instalado**, ou `0` se o atributo nao estiver la.
     *
     * Abre o `AndroidManifest.xml` de dentro do pacote pelo `AssetManager`, o que le o manifesto
     * **empacotado** — depois da mesclagem das variantes e do `aapt2`. Um atributo que exista no
     * arquivo de origem e nao sobreviva ao empacotamento nao aparece aqui, que e exatamente a
     * distincao que este teste existe para fazer.
     */
    private fun recursoDeRegras(): Int {
        val manifesto = context.assets.openXmlResourceParser("AndroidManifest.xml")

        manifesto.use {
            var evento = it.eventType
            while (evento != XmlPullParser.END_DOCUMENT) {
                if (evento == XmlPullParser.START_TAG && it.name == "application") {
                    for (i in 0 until it.attributeCount) {
                        if (it.getAttributeName(i) == "dataExtractionRules") {
                            return it.getAttributeResourceValue(i, 0)
                        }
                    }
                    return 0
                }
                evento = it.next()
            }
        }

        return 0
    }

    private companion object {
        const val ANDROID = "http://schemas.android.com/apk/res/android"
    }
}
