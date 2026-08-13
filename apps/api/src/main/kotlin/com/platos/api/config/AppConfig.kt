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
) {
    companion object {
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
            )
        }
    }
}
