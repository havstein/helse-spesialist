package no.nav.helse.db

import kotliquery.Query
import kotliquery.Session
import kotliquery.queryOf
import no.nav.helse.modell.totrinnsvurdering.TotrinnsvurderingOld
import org.intellij.lang.annotations.Language
import java.util.UUID

internal class TransactionalTotrinnsvurderingDao(
    private val session: Session,
) : TotrinnsvurderingRepository {
    override fun hentAktivTotrinnsvurdering(oppgaveId: Long): TotrinnsvurderingFraDatabase? {
        @Language("PostgreSQL")
        val query =
            """
            SELECT v.vedtaksperiode_id,
                   er_retur,
                   tv.saksbehandler,
                   tv.beslutter,
                   ui.id as utbetaling_id,
                   tv.opprettet,
                   tv.oppdatert
            FROM totrinnsvurdering tv
                     INNER JOIN vedtak v on tv.vedtaksperiode_id = v.vedtaksperiode_id
                     INNER JOIN oppgave o on v.id = o.vedtak_ref
                     LEFT JOIN utbetaling_id ui on ui.id = tv.utbetaling_id_ref
            WHERE o.id = :oppgaveId
              AND utbetaling_id_ref IS NULL
            """.trimIndent()

        return session.run(
            queryOf(query, mapOf("oppgaveId" to oppgaveId)).map { row ->
                TotrinnsvurderingFraDatabase(
                    vedtaksperiodeId = row.uuid("vedtaksperiode_id"),
                    erRetur = row.boolean("er_retur"),
                    saksbehandler = row.uuidOrNull("saksbehandler"),
                    beslutter = row.uuidOrNull("beslutter"),
                    utbetalingId = row.uuidOrNull("utbetaling_id"),
                    opprettet = row.localDateTime("opprettet"),
                    oppdatert = row.localDateTimeOrNull("oppdatert"),
                )
            }.asSingle,
        )
    }

    override fun oppdater(totrinnsvurderingFraDatabase: TotrinnsvurderingFraDatabase) {
        @Language("PostgreSQL")
        val query =
            """
            UPDATE totrinnsvurdering
            SET saksbehandler     = :saksbehandler,
                beslutter         = :beslutter,
                er_retur          = :er_retur,
                oppdatert         = :oppdatert,
                utbetaling_id_ref = (SELECT id FROM utbetaling_id ui WHERE ui.utbetaling_id = :utbetaling_id)
            WHERE vedtaksperiode_id = :vedtaksperiode_id
              AND utbetaling_id_ref IS NULL
            """.trimIndent()

        session.run(
            queryOf(
                query,
                mapOf(
                    "saksbehandler" to totrinnsvurderingFraDatabase.saksbehandler,
                    "beslutter" to totrinnsvurderingFraDatabase.beslutter,
                    "er_retur" to totrinnsvurderingFraDatabase.erRetur,
                    "oppdatert" to totrinnsvurderingFraDatabase.oppdatert,
                    "utbetaling_id" to totrinnsvurderingFraDatabase.utbetalingId,
                    "vedtaksperiode_id" to totrinnsvurderingFraDatabase.vedtaksperiodeId,
                ),
            ).asUpdate,
        )
    }

    override fun settBeslutter(
        oppgaveId: Long,
        saksbehandlerOid: UUID,
    ) {
        @Language("PostgreSQL")
        val query =
            """
            UPDATE totrinnsvurdering SET beslutter = :saksbehandlerOid, oppdatert = now()
            WHERE vedtaksperiode_id = (
                SELECT ttv.vedtaksperiode_id 
                FROM totrinnsvurdering ttv 
                INNER JOIN vedtak v on ttv.vedtaksperiode_id = v.vedtaksperiode_id
                INNER JOIN oppgave o on v.id = o.vedtak_ref
                WHERE o.id = :oppgaveId
                LIMIT 1
            )
            AND utbetaling_id_ref IS null
            """.trimIndent()

        session.run(
            queryOf(
                query,
                mapOf("oppgaveId" to oppgaveId, "saksbehandlerOid" to saksbehandlerOid),
            ).asExecute,
        )
    }

    override fun settErRetur(vedtaksperiodeId: UUID) {
        @Language("PostgreSQL")
        val query =
            """
            UPDATE totrinnsvurdering SET er_retur = true, oppdatert = now()
            WHERE vedtaksperiode_id = :vedtaksperiodeId
            AND utbetaling_id_ref IS null
            """.trimIndent()

        session.run(
            queryOf(
                query,
                mapOf("vedtaksperiodeId" to vedtaksperiodeId),
            ).asExecute,
        )
    }

    override fun opprett(vedtaksperiodeId: UUID): TotrinnsvurderingOld {
        @Language("PostgreSQL")
        val query =
            """
            INSERT INTO totrinnsvurdering (vedtaksperiode_id) 
            VALUES (:vedtaksperiodeId)
            RETURNING *
            """.trimIndent()
        return requireNotNull(
            session.run(
                queryOf(
                    query,
                    mapOf("vedtaksperiodeId" to vedtaksperiodeId),
                ).tilTotrinnsvurdering(),
            ),
        )
    }

    override fun hentAktiv(oppgaveId: Long): TotrinnsvurderingOld? {
        @Language("PostgreSQL")
        val query =
            """
            SELECT * FROM totrinnsvurdering
            INNER JOIN vedtak v on totrinnsvurdering.vedtaksperiode_id = v.vedtaksperiode_id
            INNER JOIN oppgave o on v.id = o.vedtak_ref
            WHERE o.id = :oppgaveId
            AND utbetaling_id_ref IS NULL
            """.trimIndent()

        return session.run(queryOf(query, mapOf("oppgaveId" to oppgaveId)).tilTotrinnsvurdering())
    }

    override fun hentAktiv(vedtaksperiodeId: UUID): TotrinnsvurderingOld? {
        @Language("PostgreSQL")
        val query =
            """
            SELECT * FROM totrinnsvurdering
            WHERE vedtaksperiode_id = :vedtaksperiodeId
            AND utbetaling_id_ref IS NULL
            """.trimIndent()

        return session.run(queryOf(query, mapOf("vedtaksperiodeId" to vedtaksperiodeId)).tilTotrinnsvurdering())
    }

    override fun ferdigstill(vedtaksperiodeId: UUID) {
        @Language("PostgreSQL")
        val query =
            """
            UPDATE totrinnsvurdering SET utbetaling_id_ref = (
                SELECT id FROM utbetaling_id ui
                INNER JOIN vedtaksperiode_utbetaling_id vui ON vui.utbetaling_id = ui.utbetaling_id
                WHERE vui.vedtaksperiode_id = :vedtaksperiodeId
                ORDER BY ui.id DESC
                LIMIT 1
            ), oppdatert = now()
            WHERE vedtaksperiode_id = :vedtaksperiodeId
            AND utbetaling_id_ref IS null
            """.trimIndent()

        session.run(
            queryOf(
                query,
                mapOf("vedtaksperiodeId" to vedtaksperiodeId),
            ).asExecute,
        )
    }

    private fun Query.tilTotrinnsvurdering() =
        map { row ->
            TotrinnsvurderingOld(
                vedtaksperiodeId = row.uuid("vedtaksperiode_id"),
                erRetur = row.boolean("er_retur"),
                saksbehandler = row.uuidOrNull("saksbehandler"),
                beslutter = row.uuidOrNull("beslutter"),
                utbetalingIdRef = row.longOrNull("utbetaling_id_ref"),
                opprettet = row.localDateTime("opprettet"),
                oppdatert = row.localDateTimeOrNull("oppdatert"),
            )
        }.asSingle
}
