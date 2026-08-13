package com.platos.api.billing

import com.platos.api.support.TestDependencies
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * D-0.8: os planos sao carregados e validados na inicializacao. Arquivo ausente ou invalido
 * derruba o processo, em vez de degradar para direitos vazios que so apareceriam como bug de
 * cobranca semanas depois.
 */
class PlanCatalogTest {

    @Test
    fun `os planos versionados carregam e refletem a §3_4`() {
        val catalogo = PlanCatalog.load(TestDependencies.plansDir)

        assertEquals(setOf("basic", "pro"), catalogo.names)

        // Correcao objetiva e ilimitada nos dois planos: roda no dispositivo, custo marginal zero.
        assertTrue(catalogo["basic"].entitlements.getValue("omr_grading").unlimited)
        assertTrue(catalogo["pro"].entitlements.getValue("omr_grading").unlimited)

        // Correcao discursiva por IA e o que o Basic nao inclui.
        assertEquals(false, catalogo["basic"].entitlements.getValue("ai_essay_grading").enabled)
        assertEquals(true, catalogo["pro"].entitlements.getValue("ai_essay_grading").enabled)

        // Geracao por IA existe nos dois, com quota maior no Pro.
        val quotaBasic = catalogo["basic"].entitlements.getValue("ai_exam_generation").periodQuota!!
        val quotaPro = catalogo["pro"].entitlements.getValue("ai_exam_generation").periodQuota!!
        assertTrue(quotaPro > quotaBasic, "Pro precisa ter quota maior de geracao")
    }

    @Test
    fun `plano desconhecido falha explicitamente em vez de devolver direitos vazios`() {
        val catalogo = PlanCatalog.load(TestDependencies.plansDir)

        val erro = assertFailsWith<UnknownPlanException> { catalogo["enterprise"] }

        assertEquals("enterprise", erro.plan)
    }

    @Test
    fun `diretorio inexistente derruba o carregamento`() {
        assertFailsWith<IllegalStateException> {
            PlanCatalog.load(Path.of("diretorio", "que", "nao", "existe"))
        }
    }

    @Test
    fun `diretorio sem nenhum plano derruba o carregamento`() {
        val vazio = Files.createTempDirectory("planos-vazios")

        assertFailsWith<IllegalStateException> { PlanCatalog.load(vazio) }
    }

    @Test
    fun `arquivo corrompido derruba o carregamento com mensagem identificavel`() {
        val diretorio = Files.createTempDirectory("planos-corrompidos")
        diretorio.resolve("basic.yaml").writeText("isto: [nao e: um plano")

        val erro = assertFailsWith<InvalidPlanFileException> { PlanCatalog.load(diretorio) }

        assertTrue(erro.message!!.contains("basic.yaml"), "a mensagem precisa apontar o arquivo: ${erro.message}")
    }

    @Test
    fun `campo plan divergente do nome do arquivo e recusado`() {
        val diretorio = Files.createTempDirectory("planos-divergentes")
        diretorio.resolve("basic.yaml").writeText(
            """
            plan: pro
            display_name: Basic
            entitlements:
              omr_grading:
                enabled: true
                unlimited: true
            """.trimIndent(),
        )

        val erro = assertFailsWith<InvalidPlanFileException> { PlanCatalog.load(diretorio) }

        assertTrue(erro.message!!.contains("'pro'"))
    }

    @Test
    fun `entitlement habilitado e limitado sem quota e recusado`() {
        val diretorio = Files.createTempDirectory("planos-sem-quota")
        diretorio.resolve("basic.yaml").writeText(
            """
            plan: basic
            display_name: Basic
            entitlements:
              ai_exam_generation:
                enabled: true
                unlimited: false
                credit_type: ai_exam_generation
            """.trimIndent(),
        )

        val erro = assertFailsWith<InvalidPlanFileException> { PlanCatalog.load(diretorio) }

        assertTrue(erro.message!!.contains("period_quota"))
    }
}
