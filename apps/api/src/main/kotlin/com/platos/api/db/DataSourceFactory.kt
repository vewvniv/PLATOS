package com.platos.api.db

import com.platos.api.config.DatabaseConfig
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import javax.sql.DataSource

object DataSourceFactory {

    /**
     * D-0.2: a API conecta como `app_backend`, papel sem SUPERUSER e sem BYPASSRLS.
     *
     * Nao ha verificacao em runtime aqui de proposito — ela existe como teste
     * ([com.platos.api.db.ConnectionRoleTest]), porque o lugar de barrar uma configuracao errada e
     * o build, nao um log de producao que ninguem le.
     */
    fun create(config: DatabaseConfig): DataSource {
        val hikari = HikariConfig().apply {
            jdbcUrl = config.url
            username = config.user
            password = config.password
            maximumPoolSize = config.maxPoolSize
            isAutoCommit = true
            driverClassName = "org.postgresql.Driver"
            poolName = "platos-api"
        }
        return HikariDataSource(hikari)
    }
}
