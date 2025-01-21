package no.nav.helse

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotliquery.queryOf
import kotliquery.sessionOf
import org.flywaydb.core.Flyway
import org.intellij.lang.annotations.Language
import org.testcontainers.containers.PostgreSQLContainer
import java.util.UUID
import javax.sql.DataSource

abstract class AbstractDatabaseTest {

    companion object {
        private val postgres = PostgreSQLContainer<Nothing>("postgres:14").apply {
            withReuse(true)
            withLabel("app-navn", "spesialist")
            start()
            println("Database: jdbc:postgresql://localhost:$firstMappedPort/test startet opp, credentials: test og test")
        }

        @JvmStatic
        protected val dataSource =
            HikariDataSource(HikariConfig().apply {
                jdbcUrl = postgres.jdbcUrl
                username = postgres.username
                password = postgres.password
                maximumPoolSize = 100
                connectionTimeout = 500
                initializationFailTimeout = 5000
            })

        init {
            Flyway.configure()
                .dataSource(dataSource)
                .placeholders(mapOf("spesialist_oid" to UUID.randomUUID().toString()))
                .load()
                .migrate()

            // Kan sikkert slå sammen disse to hvis vi ikke lenger trenger å truncate mer enn en gang
            createTruncateFunction(dataSource)
            sessionOf(dataSource).use  {
                it.run(queryOf("SELECT truncate_tables()").asExecute)
            }
        }
    }
}

private fun createTruncateFunction(dataSource: DataSource) {
    sessionOf(dataSource).use {
        @Language("PostgreSQL")
        val query = """
            CREATE OR REPLACE FUNCTION truncate_tables() RETURNS void AS $$
            DECLARE
            truncate_statement text;
            BEGIN
                SELECT 'TRUNCATE ' || string_agg(format('%I.%I', schemaname, tablename), ',') || ' RESTART IDENTITY CASCADE'
                    INTO truncate_statement
                FROM pg_tables
                WHERE schemaname='public'
                AND tablename not in ('enhet', 'flyway_schema_history', 'global_snapshot_versjon');
                UPDATE global_snapshot_versjon SET versjon = 0 WHERE id = 1;
                EXECUTE truncate_statement;
            END;
            $$ LANGUAGE plpgsql;
        """.trimIndent()
        it.run(queryOf(query).asExecute)
    }
}