package com.platos.api.db

import com.platos.api.support.PostgresSupport
import org.jooq.exception.DataAccessException
import java.sql.SQLException
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * As duas tabelas do resultado, medidas contra Postgres real.
 *
 * O que elas prometem nao e codigo da aplicacao: e append-only no armazenamento, idempotencia por
 * chave e isolamento por organizacao. Cada uma dessas falha em silencio do mesmo jeito — existindo
 * na migration e nunca sendo exercitada —, entao aqui cada uma e **tentada de verdade**, pelos dois
 * caminhos que existem: o da aplicacao, que esbarra no privilegio, e o do dono da tabela, que
 * esbarra no gatilho.
 *
 * Mesma forma de `ExamPackageImmutabilityTest`, e pela mesma razao registrada la.
 */
class GradingResultTableTest {

    private lateinit var usuario: UUID
    private lateinit var org: UUID
    private lateinit var prova: UUID

    private val conteudo = """{"meta":{"exam_id":"prova-r"},"items":[],"answer_key":[]}"""
    private val hashDoPacote = PostgresSupport.sha256Hex(conteudo)

    @BeforeTest
    fun setUp() {
        PostgresSupport.start()
        PostgresSupport.reset()

        usuario = PostgresSupport.createUser("sub-resultado")
        org = PostgresSupport.createOrganization(name = "Escola")
        PostgresSupport.addMembership(usuario, org, "teacher")
        prova = PostgresSupport.createExam(org, shortId = "prova-r", title = "Prova R")
        PostgresSupport.publishPackage(org, prova, conteudo, hashDoPacote)
    }

    /**
     * Grava um resultado pelo caminho da aplicacao.
     *
     * Pelo caminho da aplicacao de proposito: gravar como dono contornaria RLS e privilegio, e
     * entao os testes de isolamento e de append-only estariam medindo uma porta que o servico nunca
     * usa.
     */
    private fun gravarResultado(
        capture: String,
        token: String? = "aluno-1",
        revisao: Int = 1,
        pontos: Int = 10,
        maximo: Int = 40,
        fechada: Boolean = true,
        organizacao: UUID = org,
        comoUsuario: UUID = usuario,
        prova: UUID = this.prova,
    ): UUID = PostgresSupport.tenancy.asUser(comoUsuario) { ctx ->
        ctx.fetchOne(
            """
            insert into grading_result (
                organization_id, exam_id, student_token, revision, capture_id,
                package_hash, variant_id, points, max_score, closed, captured_at
            ) values (?, ?, ?, ?, ?, ?, 'v1', ?, ?, ?, now())
            returning id
            """.trimIndent(),
            organizacao, prova, token, revisao, capture, hashDoPacote, pontos, maximo, fechada,
        )!!.get(0, UUID::class.java)
    }

    private fun gravarObservacao(
        resultado: UUID,
        item: String,
        tipo: String = "marcada",
        alternativas: Array<String> = arrayOf("A"),
        vale: Int = 1,
        rendeu: Int = 1,
    ) = PostgresSupport.tenancy.asUser(usuario) { ctx ->
        ctx.execute(
            """
            insert into answer_observation (
                organization_id, grading_result_id, item_id, answer_kind, answer_options, worth, earned
            ) values (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            org, resultado, item, tipo, alternativas, vale, rendeu,
        )
    }

    private fun contarResultados(): Int = PostgresSupport.adminDataSource.connection.use { c ->
        c.createStatement().use { s ->
            s.executeQuery("select count(*) from grading_result").use { it.next(); it.getInt(1) }
        }
    }

    private fun pontosGravados(capture: String): Int =
        PostgresSupport.adminDataSource.connection.use { c ->
            c.prepareStatement("select points from grading_result where capture_id = ?").use { s ->
                s.setString(1, capture)
                s.executeQuery().use { it.next(); it.getInt(1) }
            }
        }

    private fun sqlState(erro: DataAccessException): String? =
        generateSequence(erro.cause) { it.cause }.filterIsInstance<SQLException>().firstOrNull()?.sqlState

    // -----------------------------------------------------------------------
    // 3.1 — a forma que a migration prometeu existe mesmo no catalogo
    // -----------------------------------------------------------------------

    /**
     * As constraints pelo nome, lidas do catalogo.
     *
     * **Nao e redundante com os testes de comportamento abaixo.** Eles provam que *alguma* coisa
     * recusou; este prova que quem recusou foi a constraint que a migration diz ter criado. Uma
     * migration futura que trocasse o unique por um indice parcial passaria nos outros e cairia
     * aqui — que e o momento certo de descobrir.
     */
    @Test
    fun `as constraints declaradas na migration existem no catalogo`() {
        val esperadas = setOf(
            "grading_result_captura_unica",
            "grading_result_revisao_unica",
            "grading_result_nota_na_escala",
            "grading_result_id_org_unico",
            "grading_result_prova_da_mesma_org",
            "answer_observation_item_unico",
            "answer_observation_na_escala",
            "answer_observation_pendente_sem_ponto",
            "answer_observation_em_branco_sem_alternativa",
            "answer_observation_resultado_da_mesma_org",
        )

        val encontradas = PostgresSupport.adminDataSource.connection.use { c ->
            c.prepareStatement(
                """
                select conname from pg_constraint
                where conrelid in ('public.grading_result'::regclass, 'public.answer_observation'::regclass)
                """.trimIndent(),
            ).use { s ->
                s.executeQuery().use { rs ->
                    buildSet { while (rs.next()) add(rs.getString(1)) }
                }
            }
        }

        // O piso (P13): sem ele, um `regclass` que resolvesse para tabela vazia devolveria conjunto
        // vazio e a diferenca abaixo acusaria tudo — mas um `esperadas` vazio passaria calado.
        assertTrue(esperadas.isNotEmpty(), "a lista de constraints esperadas nao pode estar vazia")
        assertEquals(
            emptySet(),
            esperadas - encontradas,
            "constraint que a migration promete e o catalogo nao tem",
        )
    }

    @Test
    fun `nota fora da escala do proprio resultado e recusada`() {
        val erro = assertFailsWith<DataAccessException> {
            gravarResultado(capture = "cap-escala", pontos = 41, maximo = 40)
        }

        assertEquals("23514", sqlState(erro), "esperava recusa por check constraint")
        assertEquals(0, contarResultados())
    }

    @Test
    fun `questao que depende de revisao nao pode render ponto`() {
        val resultado = gravarResultado(capture = "cap-pendente")

        val erro = assertFailsWith<DataAccessException> {
            gravarObservacao(resultado, item = "q01", tipo = "indecisa", vale = 1, rendeu = 1)
        }

        assertEquals("23514", sqlState(erro), "esperava recusa por check constraint")
    }

    // -----------------------------------------------------------------------
    // 3.1 — idempotencia e revisao, na forma de chave
    // -----------------------------------------------------------------------

    @Test
    fun `a mesma captura nao entra duas vezes na mesma prova`() {
        gravarResultado(capture = "cap-unica")

        val erro = assertFailsWith<DataAccessException> {
            gravarResultado(capture = "cap-unica", revisao = 2)
        }

        assertEquals("23505", sqlState(erro), "esperava recusa por unique")
        assertEquals(1, contarResultados(), "a segunda gravacao passou")
    }

    @Test
    fun `revisao repetida para o mesmo aluno e recusada`() {
        gravarResultado(capture = "cap-a", revisao = 1)

        val erro = assertFailsWith<DataAccessException> {
            gravarResultado(capture = "cap-b", revisao = 1)
        }

        assertEquals("23505", sqlState(erro), "esperava recusa por unique")
        assertEquals(1, contarResultados())
    }

    @Test
    fun `recaptura do mesmo aluno convive com a revisao anterior`() {
        gravarResultado(capture = "cap-1", revisao = 1, pontos = 10)
        gravarResultado(capture = "cap-2", revisao = 2, pontos = 12)

        assertEquals(2, contarResultados(), "as duas revisoes precisam coexistir")
        assertEquals(10, pontosGravados("cap-1"), "a revisao anterior foi alterada")
        assertEquals(12, pontosGravados("cap-2"))
    }

    /**
     * A folha avulsa, e por que o token e nulo e nao vazio.
     *
     * Com string vazia, duas avulsas da mesma prova colidiriam no unique de revisao — e a segunda
     * seria recusada como se fosse duplicata da primeira, que e o oposto do que ela e.
     */
    @Test
    fun `duas folhas avulsas da mesma prova coexistem`() {
        gravarResultado(capture = "cap-avulsa-1", token = null)
        gravarResultado(capture = "cap-avulsa-2", token = null)

        assertEquals(2, contarResultados())
    }

    @Test
    fun `token vazio e recusado, porque vazio nao e ausencia`() {
        val erro = assertFailsWith<DataAccessException> {
            gravarResultado(capture = "cap-vazio", token = "   ")
        }

        assertEquals("23514", sqlState(erro), "esperava recusa por check constraint")
    }

    // -----------------------------------------------------------------------
    // 3.2 — isolamento por organizacao
    // -----------------------------------------------------------------------

    @Test
    fun `resultado de outra organizacao nao e legivel`() {
        gravarResultado(capture = "cap-da-escola")

        val forasteiro = PostgresSupport.createUser("sub-forasteiro")
        val outraOrg = PostgresSupport.createOrganization(name = "Outra Escola")
        PostgresSupport.addMembership(forasteiro, outraOrg, "teacher")

        val vistoPeloDono = PostgresSupport.tenancy.asUser(usuario) { ctx ->
            ctx.fetchOne("select count(*) from grading_result")!!.get(0, Int::class.java)
        }
        val vistoPeloForasteiro = PostgresSupport.tenancy.asUser(forasteiro) { ctx ->
            ctx.fetchOne("select count(*) from grading_result")!!.get(0, Int::class.java)
        }

        // O canario (P13): sem ele, "o forasteiro ve zero" passaria tambem num banco vazio, em que
        // ninguem ve nada e o isolamento nao foi exercitado.
        assertEquals(1, vistoPeloDono, "o membro da organizacao precisa ver o proprio resultado")
        assertEquals(0, vistoPeloForasteiro, "quem nao e membro leu resultado de outra organizacao")
    }

    @Test
    fun `gravar resultado em organizacao de que nao se e membro e recusado`() {
        val forasteiro = PostgresSupport.createUser("sub-intruso")
        val outraOrg = PostgresSupport.createOrganization(name = "Terceira Escola")
        PostgresSupport.addMembership(forasteiro, outraOrg, "teacher")

        val erro = assertFailsWith<DataAccessException> {
            gravarResultado(capture = "cap-intruso", comoUsuario = forasteiro)
        }

        assertEquals("42501", sqlState(erro), "esperava recusa pela politica de RLS")
        assertEquals(0, contarResultados())
    }

    // -----------------------------------------------------------------------
    // 3.4 — append-only, pelos dois caminhos
    // -----------------------------------------------------------------------

    @Test
    fun `a aplicacao nao altera nem apaga um resultado gravado`() {
        gravarResultado(capture = "cap-append", pontos = 10)

        val doUpdate = assertFailsWith<DataAccessException> {
            PostgresSupport.tenancy.asUser(usuario) { ctx ->
                ctx.execute("update grading_result set points = 40")
            }
        }
        assertEquals("42501", sqlState(doUpdate), "esperava recusa por privilegio insuficiente")

        val doDelete = assertFailsWith<DataAccessException> {
            PostgresSupport.tenancy.asUser(usuario) { ctx ->
                ctx.execute("delete from grading_result")
            }
        }
        assertEquals("42501", sqlState(doDelete), "esperava recusa por privilegio insuficiente")

        assertEquals(10, pontosGravados("cap-append"), "a nota foi alterada")
        assertEquals(1, contarResultados(), "o resultado foi removido")
    }

    /**
     * O caminho que o privilegio nao cobre.
     *
     * `REVOKE` barra `app_backend` e mais ninguem. Se o append-only fosse so o `REVOKE`, ele valeria
     * ate a primeira migration, job de manutencao ou script de suporte — que e exatamente quando uma
     * nota ja consolidada seria reescrita. Este roda como superusuario para afirmar que ate ele e
     * recusado, e confere o `PT001` do gatilho e nao um erro qualquer.
     */
    @Test
    fun `nem o superusuario altera ou apaga um resultado`() {
        gravarResultado(capture = "cap-super", pontos = 10)

        val doUpdate = assertFailsWith<SQLException> {
            PostgresSupport.adminDataSource.connection.use { c ->
                c.createStatement().use { it.executeUpdate("update grading_result set points = 0") }
            }
        }
        assertEquals("PT001", doUpdate.sqlState, "o gatilho de append-only nao reagiu ao UPDATE")

        val doDelete = assertFailsWith<SQLException> {
            PostgresSupport.adminDataSource.connection.use { c ->
                c.createStatement().use { it.executeUpdate("delete from grading_result") }
            }
        }
        assertEquals("PT001", doDelete.sqlState, "o gatilho de append-only nao reagiu ao DELETE")

        assertEquals(10, pontosGravados("cap-super"), "a nota mudou")
        assertEquals(1, contarResultados())
    }

    @Test
    fun `nem o superusuario altera ou apaga uma observacao por questao`() {
        val resultado = gravarResultado(capture = "cap-obs")
        gravarObservacao(resultado, item = "q01")

        val doUpdate = assertFailsWith<SQLException> {
            PostgresSupport.adminDataSource.connection.use { c ->
                c.createStatement().use { it.executeUpdate("update answer_observation set earned = 0") }
            }
        }
        assertEquals("PT001", doUpdate.sqlState, "o gatilho nao reagiu ao UPDATE na evidencia")

        val doDelete = assertFailsWith<SQLException> {
            PostgresSupport.adminDataSource.connection.use { c ->
                c.createStatement().use { it.executeUpdate("delete from answer_observation") }
            }
        }
        assertEquals("PT001", doDelete.sqlState, "o gatilho nao reagiu ao DELETE na evidencia")
    }

    /**
     * DELETE que chega por cascata continua sendo DELETE.
     *
     * Se `grading_result.exam_id` tivesse `on delete cascade`, apagar a prova apagaria a nota sem
     * que uma linha de codigo mencionasse `grading_result` — append-only contornado pela porta dos
     * fundos, e sem erro nenhum. E a mesma porta que `exam_package` ja fechou.
     */
    @Test
    fun `apagar a prova e recusado enquanto houver resultado gravado`() {
        gravarResultado(capture = "cap-cascata")

        val erro = assertFailsWith<SQLException> {
            PostgresSupport.adminDataSource.connection.use { c ->
                c.prepareStatement("delete from exam where id = ?").use { s ->
                    s.setObject(1, prova)
                    s.executeUpdate()
                }
            }
        }

        assertEquals("23503", erro.sqlState, "esperava recusa por chave estrangeira")
        assertEquals(1, contarResultados(), "o resultado sobreviveu ao DELETE da prova?")
    }
}
