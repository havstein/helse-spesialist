package no.nav.helse.db

import kotliquery.Session
import kotliquery.queryOf
import org.intellij.lang.annotations.Language
import java.time.LocalDateTime
import java.util.UUID

class TransactionalGenerasjonDao(private val session: Session) : GenerasjonRepository {
    override fun førsteGenerasjonVedtakFattetTidspunkt(vedtaksperiodeId: UUID): LocalDateTime? {
        @Language("PostgreSQL")
        val query =
            """
            SELECT tilstand_endret_tidspunkt 
            FROM selve_vedtaksperiode_generasjon 
            WHERE vedtaksperiode_id = :vedtaksperiodeId AND tilstand = 'VedtakFattet'
            ORDER BY tilstand_endret_tidspunkt
            LIMIT 1
            """.trimIndent()
        return session.run(
            queryOf(
                query,
                mapOf(
                    "vedtaksperiodeId" to vedtaksperiodeId,
                ),
            ).map {
                it.localDateTimeOrNull("tilstand_endret_tidspunkt")
            }.asSingle,
        )
    }
}
