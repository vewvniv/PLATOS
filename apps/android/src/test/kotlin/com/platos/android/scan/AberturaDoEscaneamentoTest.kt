package com.platos.android.scan

import com.platos.android.session.MotivoDaBarragem
import com.platos.domain.exam.ExamPackage
import com.platos.domain.exam.PackageMeta
import com.platos.domain.exam.PackageVariant
import com.platos.domain.exam.Scoring
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * A decisao de abrir o escaneamento, exercitada (achado 3.3).
 *
 * **Esta decisao nao tinha um unico cenario.** Ela morava dentro de `ScanActivity.onCreate`, e foi a
 * vinte linhas dela que a auditoria achou o caminho que descartava uma correcao apurada em silencio:
 * folha medida, nota desenhada na tela, nada gravado, nada agendado, sem mensagem.
 *
 * **O que se afirma aqui e o motivo, e nao so que nao abriu.** `rigorous.md` §3: a assercao confere o
 * motivo da recusa, e nao so que houve recusa. Duas recusas indistinguiveis fariam o aplicativo
 * sugerir a acao errada — e a acao errada, aqui, e mandar o professor baixar de novo uma prova que ja
 * esta no aparelho.
 */
class AberturaDoEscaneamentoTest {

    private val organizacao = "01a06ba4-cb43-7d97-842d-165352d010b5"
    private val contentHash = "a".repeat(64)
    private val shortId = "prova-referencia-slice-1"

    private val pacote = ExamPackage(
        meta = PackageMeta(
            examId = shortId,
            layoutEngineVersion = 1,
            minRendererVersion = 1,
            fullyOfflineGradable = true,
        ),
        items = emptyList(),
        variants = listOf(PackageVariant(variantId = "v1", positions = emptyMap())),
        assignments = emptyList(),
        layout = emptyMap(),
        answerKey = emptyList(),
        scoring = Scoring(maxScore = 1),
    )

    private fun lendo(pacote: ExamPackage?): (String, String) -> ExamPackage? = { _, _ -> pacote }

    @Test
    fun `sem o identificador da prova o escaneamento nao abre, e o motivo e proprio`() {
        val decisao = decidirAbertura(
            organizacao = organizacao,
            contentHash = contentHash,
            shortId = null,
            lerPacote = lendo(pacote),
        )

        val recusa = assertInstanceOf(AberturaDoEscaneamento.NaoAbre::class.java, decisao)
        assertEquals(
            MotivoDeNaoAbrir.PROVA_NAO_IDENTIFICADA,
            recusa.motivo,
            "a recusa precisa dizer que a prova nao foi identificada, e nao que falta pacote: " +
                "o pacote esta conferido e no lugar, e mandar baixar de novo nao conserta nada",
        )
    }

    /**
     * A guarda de vacuidade: **so** o `short_id` separava esta entrada da abertura.
     *
     * Sem ela, a recusa acima poderia vir de qualquer outra coisa do mesmo caminho — do pacote, da
     * organizacao — e o cenario estaria medindo a camada vizinha.
     */
    @Test
    fun `com o identificador da prova, a mesma entrada abre`() {
        val decisao = decidirAbertura(
            organizacao = organizacao,
            contentHash = contentHash,
            shortId = shortId,
            lerPacote = lendo(pacote),
        )

        val aberta = assertInstanceOf(AberturaDoEscaneamento.Abre::class.java, decisao)
        assertEquals(organizacao, aberta.organizacao)
        assertEquals(shortId, aberta.prova)
    }

    @Test
    fun `sem pacote conferido o escaneamento nao abre, e o motivo e o outro`() {
        val decisao = decidirAbertura(
            organizacao = organizacao,
            contentHash = contentHash,
            shortId = shortId,
            lerPacote = lendo(null),
        )

        val recusa = assertInstanceOf(AberturaDoEscaneamento.NaoAbre::class.java, decisao)
        assertEquals(MotivoDeNaoAbrir.PACOTE_NAO_CONFERIDO, recusa.motivo)
    }

    /**
     * O motivo novo e **distinguivel dos cinco do gate**, e a distincao e verificada, nao afirmada.
     *
     * Os dois conjuntos vivem em camadas diferentes de proposito — `MotivoDaBarragem` decide antes do
     * `Intent`, em `SessaoActivity`, e nao tem como saber que um extra vai faltar. O que a spec exige
     * e que a recusa seja **propria**, e e isto que se confere: nenhum nome coincide, e as frases sao
     * diferentes.
     */
    @Test
    fun `o motivo e distinguivel dos cinco motivos do gate de pre-voo`() {
        val doGate = MotivoDaBarragem.entries.map { it.name }.toSet()
        val daAbertura = MotivoDeNaoAbrir.entries.map { it.name }.toSet()

        assertEquals(5, doGate.size, "o gate tem cinco motivos; se mudou, esta assercao precisa saber")
        assertEquals(
            emptySet<String>(),
            doGate intersect daAbertura,
            "os dois conjuntos nao podem coincidir",
        )
    }

    /**
     * As duas frases dizem coisas diferentes, e a da prova nao identificada **nao** manda baixar.
     *
     * E o que a spec pede com "nao pede ao professor que baixe a prova de novo": a frase e o que
     * chega a quem segura o aparelho, e sugerir a acao errada e o defeito que separar motivos existe
     * para impedir.
     */
    @Test
    fun `a frase de cada motivo diz o que fazer, e sao diferentes`() {
        val semPacote = frasePara(MotivoDeNaoAbrir.PACOTE_NAO_CONFERIDO)
        val semProva = frasePara(MotivoDeNaoAbrir.PROVA_NAO_IDENTIFICADA)

        assertTrue(semPacote != semProva, "duas recusas com a mesma frase sao uma recusa so")
        assertTrue(
            semPacote.contains("baixa-la"),
            "a recusa por pacote precisa mandar baixar: $semPacote",
        )
        assertTrue(
            !semProva.contains("baixa-la") && semProva.contains("continua baixada"),
            "a recusa por prova nao identificada nao pode mandar baixar de novo: $semProva",
        )
    }
}
