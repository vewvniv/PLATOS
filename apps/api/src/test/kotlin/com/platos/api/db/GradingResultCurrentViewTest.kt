package com.platos.api.db

import com.platos.api.support.PostgresSupport
import org.jooq.exception.DataAccessException
import java.math.BigDecimal
import java.sql.SQLException
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * A nota do professor no banco (`slice-5c-2-a-nota-do-professor`, ADR-0021), medida contra Postgres
 * real e pelo caminho da aplicacao (`app_backend` + RLS), nunca como dono.
 *
 * Tres coisas: as guardas novas (`check`) recusam o que dizem recusar **pela constraint que a migration
 * nomeia**; `numeric(8,2)` **arredonda em silencio** (e por isso a recusa de 3 casas e do dominio); e a
 * view `grading_result_current` escolhe a revisao certa nas ordens de chegada que o modelo offline produz.
 */
class GradingResultCurrentViewTest {

    private lateinit var usuario: UUID
    private lateinit var org: UUID
    private lateinit var prova: UUID

    private val conteudo = """{"meta":{"exam_id":"prova-n"},"items":[],"answer_key":[]}"""
    private val hashDoPacote = PostgresSupport.sha256Hex(conteudo)

    @BeforeTest
    fun setUp() {
        PostgresSupport.start()
        PostgresSupport.reset()

        usuario = PostgresSupport.createUser("sub-nota")
        org = PostgresSupport.createOrganization(name = "Escola")
        PostgresSupport.addMembership(usuario, org, "teacher")
        prova = PostgresSupport.createExam(org, shortId = "prova-n", title = "Prova N")
        PostgresSupport.publishPackage(org, prova, conteudo, hashDoPacote)
    }

    /** Grava pelo caminho da aplicacao. `caminho` e `completa` so existem na linha `teacher`. */
    private fun gravar(
        capture: String,
        revisao: Int,
        token: String? = "aluno-1",
        origem: String = "omr",
        completa: String? = null,
        caminho: String? = if (origem == "teacher") "image" else null,
        pontos: String = "1.00",
        maximo: Int = 4,
        organizacao: UUID = org,
        comoUsuario: UUID = usuario,
    ): UUID = PostgresSupport.tenancy.asUser(comoUsuario) { ctx ->
        ctx.fetchOne(
            """
            insert into grading_result (
                organization_id, exam_id, student_token, revision, capture_id, origin, path,
                completes_capture_id, package_hash, variant_id, points, max_score, closed, captured_at
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, 'v1', ?, ?, false, now())
            returning id
            """.trimIndent(),
            organizacao, prova, token, revisao, capture, origem, caminho, completa,
            hashDoPacote, BigDecimal(pontos), maximo,
        )!!.get(0, UUID::class.java)
    }

    private fun gravarObservacao(
        resultado: UUID,
        item: String,
        tipo: String,
        alternativas: Array<String>,
        vale: Int,
        rendeu: String,
    ) = PostgresSupport.tenancy.asUser(usuario) { ctx ->
        ctx.execute(
            """
            insert into answer_observation (
                organization_id, grading_result_id, item_id, answer_kind, answer_options, worth, earned
            ) values (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            org, resultado, item, tipo, alternativas, vale, BigDecimal(rendeu),
        )
    }

    private fun correntes(): List<String> = PostgresSupport.adminDataSource.connection.use { c ->
        c.prepareStatement(
            "select capture_id from grading_result_current where exam_id = ? order by capture_id",
        ).use { s ->
            s.setObject(1, prova)
            s.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.getString(1)) } }
        }
    }

    private fun umTexto(sql: String): String = PostgresSupport.adminDataSource.connection.use { c ->
        c.createStatement().use { s -> s.executeQuery(sql).use { it.next(); it.getString(1) } }
    }

    private fun contarResultados(): Int = umTexto("select count(*) from grading_result").toInt()

    private fun causaSql(erro: DataAccessException): SQLException =
        generateSequence(erro.cause) { it.cause }.filterIsInstance<SQLException>().first()

    private fun assertRecusadaPor(constraint: String, bloco: () -> Unit) {
        val erro = assertFailsWith<DataAccessException> { bloco() }
        val causa = causaSql(erro)
        assertEquals("23514", causa.sqlState, "esperava check constraint: ${causa.message}")
        assertTrue(constraint in causa.message.orEmpty(), "esperava '$constraint': ${causa.message}")
    }

    // ------------------------------------------------------------------ as guardas novas

    @Test
    fun `as constraints novas existem no catalogo, pelo nome`() {
        val esperadas = setOf(
            "grading_result_origem_do_professor",
            "grading_result_caminho_conhecido",
            "grading_result_completa_captura_nao_vazia",
            "answer_observation_answer_kind_check",
            "answer_observation_pendente_sem_ponto",
            "answer_observation_discursiva_sem_alternativa",
        )
        val encontradas = PostgresSupport.adminDataSource.connection.use { c ->
            c.prepareStatement(
                """
                select conname from pg_constraint
                where conrelid in ('public.grading_result'::regclass, 'public.answer_observation'::regclass)
                """.trimIndent(),
            ).use { s -> s.executeQuery().use { rs -> buildSet { while (rs.next()) add(rs.getString(1)) } } }
        }
        assertTrue(esperadas.isNotEmpty(), "a lista esperada nao pode estar vazia")
        assertEquals(emptySet(), esperadas - encontradas)
    }

    @Test
    fun `linha teacher sem caminho, ou sem a captura que completa, e recusada`() {
        assertRecusadaPor("grading_result_origem_do_professor") {
            gravar("n1", 1, origem = "teacher", completa = "p1", caminho = null)
        }
        assertRecusadaPor("grading_result_origem_do_professor") {
            gravar("n2", 1, origem = "teacher", completa = null, caminho = "image")
        }
        assertEquals(0, contarResultados())
    }

    @Test
    fun `linha automatica com caminho ou com captura que completa e recusada`() {
        assertRecusadaPor("grading_result_origem_do_professor") {
            gravar("a1", 1, origem = "omr", caminho = "image")
        }
        assertRecusadaPor("grading_result_origem_do_professor") {
            gravar("a2", 1, origem = "omr", completa = "p1")
        }
    }

    @Test
    fun `caminho text e de fatia posterior e o banco o recusa`() {
        assertRecusadaPor("grading_result_caminho_conhecido") {
            gravar("n1", 1, origem = "teacher", completa = "p1", caminho = "text")
        }
    }

    @Test
    fun `numeric 8,2 arredonda 3 casas em silencio, e por isso a recusa e do dominio`() {
        gravar("r1", 1, pontos = "1.333")

        // MEDIDO: o banco nao recusa; ele grava 1.33. Se o dominio deixasse passar `1.333`, a nota
        // gravada nao seria a que o professor digitou, e nada acusaria.
        assertEquals("1.33", umTexto("select points::text from grading_result where capture_id = 'r1'"))
    }

    @Test
    fun `a evidencia da discursiva guarda o decimal exato`() {
        val resultado = gravar("n1", 2, origem = "teacher", completa = "p1", pontos = "2.75")

        gravarObservacao(resultado, "d1", "discursiva_corrigida", arrayOf(), vale = 3, rendeu = "1.75")

        assertEquals(
            "1.75",
            umTexto("select earned::text from answer_observation where item_id = 'd1'"),
        )
    }

    @Test
    fun `discursiva corrigida pode render ponto, e a objetiva pendente continua nao podendo`() {
        val resultado = gravar("n1", 2, origem = "teacher", completa = "p1")

        // A excecao do check `pendente_sem_ponto` e so para o tipo novo.
        gravarObservacao(resultado, "d1", "discursiva_corrigida", arrayOf(), vale = 3, rendeu = "3.00")
        assertRecusadaPor("answer_observation_pendente_sem_ponto") {
            gravarObservacao(resultado, "q4", "indecisa", arrayOf("A"), vale = 1, rendeu = "0.50")
        }
    }

    @Test
    fun `discursiva corrigida nao nomeia alternativa nem passa do valor`() {
        val resultado = gravar("n1", 2, origem = "teacher", completa = "p1")

        assertRecusadaPor("answer_observation_discursiva_sem_alternativa") {
            gravarObservacao(resultado, "d1", "discursiva_corrigida", arrayOf("A"), vale = 3, rendeu = "1.00")
        }
        assertRecusadaPor("answer_observation_na_escala") {
            gravarObservacao(resultado, "d2", "discursiva_corrigida", arrayOf(), vale = 3, rendeu = "3.01")
        }
    }

    // ------------------------------------------------------------------ a revisao corrente

    @Test
    fun `a parcial que chega depois da nota do professor nao e a corrente, e continua legivel`() {
        gravar("nota-1", 1, origem = "teacher", completa = "parcial-1")
        gravar("parcial-1", 2)

        assertEquals(listOf("nota-1"), correntes())
        assertEquals(2, contarResultados(), "as duas continuam gravadas")
    }

    @Test
    fun `a parcial que chega antes continua legivel e a nota do professor e a corrente`() {
        gravar("parcial-1", 1)
        gravar("nota-1", 2, origem = "teacher", completa = "parcial-1")

        assertEquals(listOf("nota-1"), correntes())
        assertEquals(2, contarResultados())
    }

    @Test
    fun `captura nova depois da nota do professor e a corrente, e a nota anterior continua legivel`() {
        gravar("nota-1", 1, origem = "teacher", completa = "parcial-1")
        gravar("parcial-1", 2)
        gravar("parcial-2", 3)

        assertEquals(listOf("parcial-2"), correntes())
        assertEquals(3, contarResultados())
    }

    @Test
    fun `nova nota do professor para a mesma captura e a corrente`() {
        gravar("parcial-1", 1)
        gravar("nota-1", 2, origem = "teacher", completa = "parcial-1")
        gravar("nota-2", 3, origem = "teacher", completa = "parcial-1")

        assertEquals(listOf("nota-2"), correntes())
    }

    @Test
    fun `a nota da captura nova vence a captura nova`() {
        gravar("parcial-1", 1)
        gravar("nota-1", 2, origem = "teacher", completa = "parcial-1")
        gravar("parcial-2", 3)
        gravar("nota-2", 4, origem = "teacher", completa = "parcial-2")

        assertEquals(listOf("nota-2"), correntes())
    }

    @Test
    fun `folha avulsa segue a propria parcial e nao se mistura com outra avulsa`() {
        gravar("avulsa-a", 1, token = null)
        gravar("avulsa-b", 2, token = null)
        gravar("nota-a", 3, token = null, origem = "teacher", completa = "avulsa-a")

        // A avulsa B nao foi corrigida: continua corrente, e a A foi substituida pela nota dela.
        assertEquals(listOf("avulsa-b", "nota-a"), correntes())
    }

    @Test
    fun `folha avulsa, a parcial que chega depois da nota nao a substitui`() {
        gravar("nota-a", 1, token = null, origem = "teacher", completa = "avulsa-a")
        gravar("avulsa-a", 2, token = null)

        assertEquals(listOf("nota-a"), correntes())
    }

    @Test
    fun `a view respeita a organizacao, o forasteiro nao le a folha de outra escola`() {
        gravar("parcial-1", 1)
        val forasteiro = PostgresSupport.createUser("sub-forasteiro")
        val outraOrg = PostgresSupport.createOrganization(kind = "school", name = "Outra Escola")
        PostgresSupport.addMembership(forasteiro, outraOrg, "teacher")

        val visto = { quem: UUID ->
            PostgresSupport.tenancy.asUser(quem) { ctx ->
                ctx.fetch("select capture_id from grading_result_current").size
            }
        }

        assertEquals(1, visto(usuario), "o dono precisa ver a propria folha (piso: sem isto o zero abaixo nao prova nada)")
        assertEquals(0, visto(forasteiro), "a view vazaria a folha de outra organizacao")
    }
}
