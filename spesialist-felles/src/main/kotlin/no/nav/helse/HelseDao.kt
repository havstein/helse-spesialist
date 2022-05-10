package no.nav.helse

import kotliquery.Query
import kotliquery.Row
import kotliquery.queryOf
import kotliquery.sessionOf
import org.intellij.lang.annotations.Language
import javax.sql.DataSource

abstract class HelseDao(private val dataSource: DataSource) {

    fun String.update(argMap: Map<String, Any?> = emptyMap()) = sessionOf(dataSource).use { session ->
        session.run(queryOf(this, argMap).asUpdate)
    }

    fun String.updateAndReturnGeneratedKey(argMap: Map<String, Any?> = emptyMap()) = sessionOf(dataSource, returnGeneratedKey = true).use { session ->
        session.run(queryOf(this, argMap).asUpdateAndReturnGeneratedKey)
    }

    fun <T> String.list(argMap: Map<String, Any> = emptyMap(), mapping: (Row) -> T?) = sessionOf(dataSource).use { session ->
        session.run(queryOf(this, argMap).map { mapping(it) }.asList)
    }

    fun <T> String.single(argMap: Map<String, Any> = emptyMap(), mapping: (Row) -> T?) = sessionOf(dataSource, strict = true).use { session ->
        session.run(queryOf(this, argMap).map { mapping(it) }.asSingle)
    }

    fun asSQL(@Language("SQL") sql: String, argMap: Map<String, Any> = emptyMap()) = queryOf(sql, argMap)
    fun <T> Query.single(mapping: (Row) -> T?) = sessionOf(dataSource).use { session -> session.run(this.map { mapping(it) }.asSingle) }
    fun <T> Query.list(mapping: (Row) -> T?) = sessionOf(dataSource).use { session -> session.run(this.map { mapping(it) }.asList) }
    fun Query.update() = sessionOf(dataSource).use { session -> session.run(this.asUpdate) }
    fun Query.updateAndReturnGeneratedKey() = sessionOf(dataSource, returnGeneratedKey = true).use { session -> session.run(this.asUpdateAndReturnGeneratedKey) }
}
