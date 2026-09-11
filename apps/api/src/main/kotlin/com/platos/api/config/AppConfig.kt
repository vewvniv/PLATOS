package com.platos.api.config

import java.nio.file.Path
import java.nio.file.Paths

data class DatabaseConfig(
    val url: String,
    val user: String,
    val password: String,
    val maxPoolSize: Int = 10,
)

data class JwtConfig(
    val issuer: String,
    val audience: String,
    val jwksUrl: String,
)

data class AppConfig(
    val database: DatabaseConfig,
    val jwt: JwtConfig,
    val plansDir: Path,
    val port: Int,
    /**
     * Qual build esta servindo, para a resposta de saude declarar.
     *
     * **Assado na imagem em tempo de construcao**, e nao configuracao de operacao: identificador que
     * o operador pode editar sem reconstruir diria o que ele digitou, e nao o que esta rodando — que
     * e exatamente a deriva que declarar o build existe para detectar.
     *
     * Nunca vazio: ausencia e [BUILD_DESCONHECIDO], decidido num lugar so.
     */
    val build: String,
) {
    companion object {

        /**
         * O que se declara quando o artefato foi construido sem identificador.
         *
         * **Um lugar so, e valor nomeado em vez de nulo ou cabecalho omitido.** Cabecalho ausente e
         * indistinguivel de intermediario que o removeu, e a resposta precisa separar "nao sei" de
         * "ninguem me perguntou". Valor inventado seria pior: indistinguivel de identificador
         * verdadeiro para quem le, e quem le esta justamente tentando descobrir o que esta no ar.
         */
        const val BUILD_DESCONHECIDO = "desconhecido"

        fun fromEnvironment(env: (String) -> String? = System::getenv): AppConfig {
            fun required(name: String): String =
                env(name) ?: error("Variavel de ambiente obrigatoria ausente: $name")

            return AppConfig(
                database = DatabaseConfig(
                    // D-0.2: app_backend, nunca postgres. Conectar como superusuario desligaria RLS
                    // em silencio e deixaria toda a suite de isolamento verde sem provar nada.
                    url = required("DATABASE_URL"),
                    user = env("DATABASE_USER") ?: "app_backend",
                    password = required("DATABASE_PASSWORD"),
                ),
                jwt = JwtConfig(
                    issuer = required("JWT_ISSUER"),
                    audience = env("JWT_AUDIENCE") ?: "authenticated",
                    jwksUrl = required("JWKS_URL"),
                ),
                plansDir = Paths.get(env("PLANS_DIR") ?: "plans"),
                port = env("PORT")?.toInt() ?: 8080,
                // **Opcional, e nunca `required`.** Exigir o identificador faria a API recusar subir
                // sem ele, o que transformaria uma melhoria de observabilidade em modo novo de falha
                // de arranque — e quebraria todo `installDist` local.
                //
                // `isNotBlank` porque vazio e o caso real: um `--build-arg` mal formado assa string
                // vazia, que passaria por "presente" e declararia nada.
                build = env("PLATOS_BUILD")?.takeIf { it.isNotBlank() } ?: BUILD_DESCONHECIDO,
            )
        }
    }
}
