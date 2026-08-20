package com.platos.api.support

import com.platos.api.db.Tenancy
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.testcontainers.containers.PostgreSQLContainer
import java.io.File
import java.time.OffsetDateTime
import java.util.UUID
import javax.sql.DataSource

/**
 * Postgres real, uma vez por JVM, com as migrations aplicadas na ordem dos arquivos.
 *
 * Duas fontes de dados de proposito distinto:
 *
 * - [adminDataSource] e superusuario e serve APENAS para montar cenario. Fixture nao e assercao.
 * - [appDataSource] conecta como `app_backend`, exatamente como a API em producao — sem SUPERUSER
 *   e sem BYPASSRLS (D-0.2). Toda assercao de isolamento passa por ela; se passasse pela outra,
 *   a suite ficaria verde ignorando RLS, que e o falso verde mais perigoso desta fatia.
 */
object PostgresSupport {

    private val container: PostgreSQLContainer<*> by lazy {
        PostgreSQLContainer("postgres:16-alpine").apply {
            start()
            applyMigrations(this)
        }
    }

    val adminDataSource: DataSource by lazy {
        hikari(container.jdbcUrl, container.username, container.password, "test-admin")
    }

    val appDataSource: DataSource by lazy {
        hikari(container.jdbcUrl, APP_ROLE, APP_ROLE_PASSWORD, "test-app-backend")
    }

    val tenancy: Tenancy by lazy { Tenancy(appDataSource) }

    const val APP_ROLE = "app_backend"
    const val APP_ROLE_PASSWORD = "app_backend"

    fun start() {
        container.isRunning
    }

    /**
     * Estado zerado entre testes. Roda como superusuario porque limpeza nao e o que se verifica.
     *
     * `exam_package` recusa DELETE por gatilho (D-2a.3), e TRUNCATE nao passa por gatilho de linha.
     * E por isso que a limpeza continua possivel sem furar a imutabilidade que os testes afirmam:
     * o caminho que a aplicacao usa segue barrado, e o privilegio de truncar nao e dela.
     */
    fun reset() {
        adminDataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    "truncate table exam_roster, exam_package, exam, " +
                        "credit_ledger, subscription, membership, organization, app_user cascade",
                )
            }
        }
    }

    // ---- Fixtures (sempre como admin) --------------------------------------------------------

    fun createUser(authSubject: String, email: String? = null, displayName: String? = null): UUID =
        queryOne(
            "insert into app_user (auth_subject, email, display_name) values (?, ?, ?) returning id",
            authSubject,
            email,
            displayName,
        )

    fun createOrganization(kind: String = "school", name: String = "Org", createdBy: UUID? = null): UUID =
        queryOne(
            "insert into organization (kind, name, created_by_user_id) values (?, ?, ?) returning id",
            kind,
            name,
            createdBy,
        )

    fun addMembership(userId: UUID, organizationId: UUID, role: String = "teacher"): UUID =
        queryOne(
            "insert into membership (user_id, organization_id, role) values (?, ?, ?) returning id",
            userId,
            organizationId,
            role,
        )

    fun createSubscription(
        organizationId: UUID?,
        plan: String,
        status: String = "active",
        billingPeriod: String = "monthly",
        periodStart: OffsetDateTime = OffsetDateTime.now().minusDays(1),
        periodEnd: OffsetDateTime = OffsetDateTime.now().plusDays(29),
    ): UUID = queryOne(
        """
        insert into subscription
            (organization_id, plan, billing_period, status, current_period_start, current_period_end)
        values (?, ?, ?, ?, ?, ?)
        returning id
        """.trimIndent(),
        organizationId,
        plan,
        billingPeriod,
        status,
        periodStart,
        periodEnd,
    )

    fun addLedgerEntry(
        organizationId: UUID,
        creditType: String,
        amount: Long,
        reason: String = "teste",
    ): UUID = queryOne(
        "insert into credit_ledger (organization_id, credit_type, amount, reason) values (?, ?, ?, ?) returning id",
        organizationId,
        creditType,
        amount,
        reason,
    )

    fun createExam(
        organizationId: UUID,
        shortId: String,
        title: String = "Prova",
        createdBy: UUID? = null,
    ): UUID = queryOne(
        "insert into exam (organization_id, short_id, title, created_by_user_id) " +
            "values (?, ?, ?, ?) returning id",
        organizationId,
        shortId,
        title,
        createdBy,
    )

    /**
     * Grava um pacote publicado. [content] entra **exatamente** como veio; e a serializacao
     * canonica de quem publica que o hash cobre (D-2a.4).
     */
    fun publishPackage(
        organizationId: UUID,
        examId: UUID,
        content: String,
        contentHash: String = sha256Hex(content),
    ): UUID = queryOne(
        "insert into exam_package (organization_id, exam_id, content, content_hash) " +
            "values (?, ?, ?, ?) returning id",
        organizationId,
        examId,
        content,
        contentHash,
    )

    fun addRosterEntry(
        organizationId: UUID,
        examId: UUID,
        studentToken: String,
        displayName: String,
        classGroup: String? = null,
        enrollmentId: String? = null,
    ): UUID = queryOne(
        "insert into exam_roster " +
            "(organization_id, exam_id, student_token, display_name, class_group, enrollment_id) " +
            "values (?, ?, ?, ?, ?, ?) returning id",
        organizationId,
        examId,
        studentToken,
        displayName,
        classGroup,
        enrollmentId,
    )

    /**
     * SHA-256 pelo `MessageDigest` da JVM — oracle independente do `Sha256` do dominio KMP.
     *
     * Aqui interessa que o valor gravado seja o hash dos bytes gravados; conferir isso com a
     * mesma implementacao que os produziu nao provaria nada.
     */
    fun sha256Hex(text: String): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }

    fun asAdmin(block: (java.sql.Connection) -> Unit) {
        adminDataSource.connection.use(block)
    }

    private fun queryOne(sql: String, vararg args: Any?): UUID =
        adminDataSource.connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                args.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
                statement.executeQuery().use { rows ->
                    check(rows.next()) { "insert nao retornou id: $sql" }
                    rows.getObject(1, UUID::class.java)
                }
            }
        }

    private fun applyMigrations(postgres: PostgreSQLContainer<*>) {
        val directory = File(
            System.getProperty("platos.migrations.dir")
                ?: error("systemProperty platos.migrations.dir nao definida"),
        )
        val migrations = directory.listFiles { file -> file.isFile && file.name.endsWith(".sql") }
            ?.sortedBy { it.name }
            ?: emptyList()

        check(migrations.isNotEmpty()) { "Nenhuma migration em $directory" }

        java.sql.DriverManager.getConnection(
            postgres.jdbcUrl,
            postgres.username,
            postgres.password,
        ).use { connection ->
            connection.createStatement().use { statement ->
                migrations.forEach { statement.execute(it.readText()) }
            }
        }
    }

    private fun hikari(url: String, user: String, password: String, name: String): DataSource =
        HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = url
                username = user
                this.password = password
                maximumPoolSize = 8
                driverClassName = "org.postgresql.Driver"
                poolName = name
            },
        )
}
