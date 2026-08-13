package com.platos.api.billing

import com.charleskorn.kaml.Yaml
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText

@Serializable
data class EntitlementDefinition(
    val enabled: Boolean,
    val unlimited: Boolean = false,
    @SerialName("period_quota") val periodQuota: Long? = null,
    @SerialName("credit_type") val creditType: String? = null,
)

@Serializable
data class PlanDefinition(
    val plan: String,
    @SerialName("display_name") val displayName: String,
    val entitlements: Map<String, EntitlementDefinition>,
)

class InvalidPlanFileException(path: Path, reason: String, cause: Throwable? = null) :
    IllegalStateException("Arquivo de plano invalido em $path: $reason", cause)

class UnknownPlanException(val plan: String) :
    IllegalStateException(
        "Assinatura referencia o plano '$plan', que nao possui arquivo em plans/. " +
            "Direitos NAO sao assumidos vazios: falhar explicitamente e o comportamento correto.",
    )

/**
 * D40 / D-0.8: entitlements sao arquivo versionado no Git, nao linhas no banco.
 *
 * Carregado e validado na inicializacao: arquivo ausente ou invalido derruba o processo, em vez de
 * degradar silenciosamente para um conjunto de direitos vazio que so apareceria como bug de
 * cobranca semanas depois.
 */
class PlanCatalog private constructor(private val plans: Map<String, PlanDefinition>) {

    operator fun get(plan: String): PlanDefinition = plans[plan] ?: throw UnknownPlanException(plan)

    fun contains(plan: String): Boolean = plans.containsKey(plan)

    val names: Set<String> get() = plans.keys

    companion object {
        fun load(directory: Path): PlanCatalog {
            check(Files.isDirectory(directory)) {
                "Diretorio de planos nao encontrado: ${directory.toAbsolutePath()}"
            }

            val files = Files.list(directory).use { stream ->
                stream.filter { it.extension == "yaml" || it.extension == "yml" }
                    .sorted()
                    .toList()
            }

            check(files.isNotEmpty()) {
                "Nenhum arquivo de plano em ${directory.toAbsolutePath()}"
            }

            val plans = files.associate { path ->
                val definition = try {
                    Yaml.default.decodeFromString(PlanDefinition.serializer(), path.readText())
                } catch (cause: Exception) {
                    throw InvalidPlanFileException(path, "nao pode ser lido como plano", cause)
                }

                validate(path, definition)
                definition.plan to definition
            }

            return PlanCatalog(plans)
        }

        private fun validate(path: Path, definition: PlanDefinition) {
            if (definition.plan != path.nameWithoutExtension) {
                throw InvalidPlanFileException(
                    path,
                    "campo 'plan' e '${definition.plan}' mas o arquivo se chama '${path.nameWithoutExtension}'",
                )
            }
            if (definition.entitlements.isEmpty()) {
                throw InvalidPlanFileException(path, "nenhum entitlement declarado")
            }
            definition.entitlements.forEach { (name, entitlement) ->
                if (entitlement.enabled && !entitlement.unlimited && entitlement.periodQuota == null) {
                    throw InvalidPlanFileException(
                        path,
                        "entitlement '$name' esta habilitado e limitado, mas nao declara period_quota",
                    )
                }
                if (entitlement.periodQuota != null && entitlement.periodQuota < 0) {
                    throw InvalidPlanFileException(path, "entitlement '$name' tem period_quota negativa")
                }
                if (!entitlement.unlimited && entitlement.creditType.isNullOrBlank()) {
                    throw InvalidPlanFileException(
                        path,
                        "entitlement '$name' e limitado e precisa declarar credit_type para casar com o ledger",
                    )
                }
            }
        }
    }
}
