package no.nav.helse.modell.utbetaling

import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.HelseDao
import no.nav.helse.db.TransactionalUtbetalingDao
import no.nav.helse.db.UtbetalingRepository
import org.intellij.lang.annotations.Language
import java.time.LocalDateTime
import java.util.UUID
import javax.sql.DataSource

class UtbetalingDao(private val dataSource: DataSource) : HelseDao(dataSource), UtbetalingRepository {
    fun erUtbetaltFør(aktørId: String): Boolean {
        @Language("PostgreSQL")
        val statement = """
            SELECT 1 FROM utbetaling u 
            JOIN utbetaling_id ui ON u.utbetaling_id_ref = ui.id
            INNER JOIN person p ON ui.person_ref = p.id
            WHERE u.status = 'UTBETALT' AND p.aktor_id = :aktor_id
        """
        return sessionOf(dataSource).use {
            it.run(queryOf(statement, mapOf("aktor_id" to aktørId.toLong())).map { it }.asList).isNotEmpty()
        }
    }

    override fun finnUtbetalingIdRef(utbetalingId: UUID): Long? {
        @Language("PostgreSQL")
        val statement = "SELECT id FROM utbetaling_id WHERE utbetaling_id = ? LIMIT 1;"
        return sessionOf(dataSource).use {
            it.run(
                queryOf(statement, utbetalingId).map { row ->
                    row.long("id")
                }.asSingle,
            )
        }
    }

    override fun nyUtbetalingStatus(
        utbetalingIdRef: Long,
        status: Utbetalingsstatus,
        opprettet: LocalDateTime,
        json: String,
    ) {
        @Language("PostgreSQL")
        val statement = """
            INSERT INTO utbetaling ( utbetaling_id_ref, status, opprettet, data )
            VALUES (:utbetalingIdRef, CAST(:status as utbetaling_status), :opprettet, CAST(:json as json)) ON CONFLICT (status, opprettet, utbetaling_id_ref) DO NOTHING;
        """
        sessionOf(dataSource).use {
            it.run(
                queryOf(
                    statement,
                    mapOf(
                        "utbetalingIdRef" to utbetalingIdRef,
                        "status" to status.toString(),
                        "opprettet" to opprettet,
                        "json" to json,
                    ),
                ).asExecute,
            )
        }
    }

    override fun erUtbetalingForkastet(utbetalingId: UUID): Boolean {
        @Language("PostgreSQL")
        val query =
            """
            SELECT 1
            FROM utbetaling u
            JOIN utbetaling_id ui ON u.utbetaling_id_ref = ui.id
            WHERE ui.utbetaling_id = :utbetaling_id
            AND status = 'FORKASTET'
            """.trimIndent()
        return sessionOf(dataSource).use { session ->
            session.run(
                queryOf(query, mapOf("utbetaling_id" to utbetalingId)).map { true }.asSingle,
            )
        } ?: false
    }

    override fun opprettUtbetalingId(
        utbetalingId: UUID,
        fødselsnummer: String,
        orgnummer: String,
        type: Utbetalingtype,
        opprettet: LocalDateTime,
        arbeidsgiverFagsystemIdRef: Long,
        personFagsystemIdRef: Long,
        arbeidsgiverbeløp: Int,
        personbeløp: Int,
    ): Long {
        @Language("PostgreSQL")
        val statement = """
            INSERT INTO utbetaling_id (
                utbetaling_id, person_ref, arbeidsgiver_ref, type, opprettet, arbeidsgiver_fagsystem_id_ref, person_fagsystem_id_ref, arbeidsgiverbeløp, personbeløp
            ) VALUES (
                :utbetalingId,
                (SELECT id FROM person WHERE fodselsnummer = :fodselsnummer),
                (SELECT id FROM arbeidsgiver WHERE orgnummer = :orgnummer),
                CAST(:type as utbetaling_type),
                :opprettet,
                :arbeidsgiverFagsystemIdRef,
                :personFagsystemIdRef,
                :arbeidsgiverbelop,
                :personbelop
            )
            ON CONFLICT (utbetaling_id) DO NOTHING RETURNING id
        """
        return sessionOf(dataSource, returnGeneratedKey = true).use {
            requireNotNull(
                it.run(
                    queryOf(
                        statement,
                        mapOf(
                            "utbetalingId" to utbetalingId,
                            "fodselsnummer" to fødselsnummer.toLong(),
                            "orgnummer" to orgnummer.toLong(),
                            "type" to type.toString(),
                            "opprettet" to opprettet,
                            "arbeidsgiverFagsystemIdRef" to arbeidsgiverFagsystemIdRef,
                            "personFagsystemIdRef" to personFagsystemIdRef,
                            "arbeidsgiverbelop" to arbeidsgiverbeløp,
                            "personbelop" to personbeløp,
                        ),
                    ).asUpdateAndReturnGeneratedKey,
                ),
            ) { "Kunne ikke opprette utbetaling" }
        }
    }

    override fun nyttOppdrag(
        fagsystemId: String,
        mottaker: String,
    ): Long? {
        @Language("PostgreSQL")
        val statement = """
            INSERT INTO oppdrag (fagsystem_id, mottaker)
            VALUES (:fagsystemId, :mottaker)
            ON CONFLICT DO NOTHING
        """
        return sessionOf(dataSource, returnGeneratedKey = true).use {
            it.run(
                queryOf(
                    statement,
                    mapOf(
                        "fagsystemId" to fagsystemId,
                        "mottaker" to mottaker,
                    ),
                ).asUpdateAndReturnGeneratedKey,
            )
        }
    }

    override fun opprettKobling(
        vedtaksperiodeId: UUID,
        utbetalingId: UUID,
    ) {
        sessionOf(dataSource).use { session ->
            TransactionalUtbetalingDao(session).opprettKobling(vedtaksperiodeId, utbetalingId)
        }
    }

    override fun fjernKobling(
        vedtaksperiodeId: UUID,
        utbetalingId: UUID,
    ) {
        sessionOf(dataSource).use { session ->
            TransactionalUtbetalingDao(session).fjernKobling(vedtaksperiodeId, utbetalingId)
        }
    }

    data class TidligereUtbetalingerForVedtaksperiodeDto(
        val utbetalingId: UUID,
        val id: Int,
        val utbetalingsstatus: Utbetalingsstatus,
    )

    override fun hentUtbetaling(utbetalingId: UUID): Utbetaling {
        return sessionOf(dataSource).use { session ->
            TransactionalUtbetalingDao(session).hentUtbetaling(utbetalingId)
        }
    }

    override fun utbetalingFor(utbetalingId: UUID): Utbetaling? {
        return sessionOf(dataSource).use { session ->
            TransactionalUtbetalingDao(session).utbetalingFor(utbetalingId)
        }
    }

    internal fun utbetalingFor(oppgaveId: Long): Utbetaling? {
        @Language("PostgreSQL")
        val query =
            "SELECT utbetaling_id, arbeidsgiverbeløp, personbeløp, type FROM utbetaling_id u WHERE u.utbetaling_id = (SELECT utbetaling_id FROM oppgave o WHERE o.id = :oppgave_id)"
        return sessionOf(dataSource).use { session ->
            session.run(
                queryOf(query, mapOf("oppgave_id" to oppgaveId)).map {
                    Utbetaling(
                        it.uuid("utbetaling_id"),
                        it.int("arbeidsgiverbeløp"),
                        it.int("personbeløp"),
                        enumValueOf(it.string("type")),
                    )
                }.asSingle,
            )
        }
    }

    override fun utbetalingerForVedtaksperiode(vedtaksperiodeId: UUID): List<TidligereUtbetalingerForVedtaksperiodeDto> {
        @Language("PostgreSQL")
        val statement = """
            SELECT vui.utbetaling_id, u.id, u.status
            FROM vedtaksperiode_utbetaling_id vui
            JOIN utbetaling_id ui ON ui.utbetaling_id = vui.utbetaling_id
            JOIN utbetaling u ON u.utbetaling_id_ref = ui.id
            WHERE vui.vedtaksperiode_id = :vedtaksperiodeId
            ORDER BY u.id DESC
            LIMIT 2;
        """
        return sessionOf(dataSource).use {
            it.run(
                queryOf(statement, mapOf("vedtaksperiodeId" to vedtaksperiodeId)).map { row ->
                    TidligereUtbetalingerForVedtaksperiodeDto(
                        id = row.int("id"),
                        utbetalingId = row.uuid("utbetaling_id"),
                        utbetalingsstatus = Utbetalingsstatus.valueOf(row.string("status")),
                    )
                }.asList,
            )
        }
    }

    override fun sisteUtbetalingIdFor(fødselsnummer: String): UUID? {
        return sessionOf(dataSource).use { session ->
            TransactionalUtbetalingDao(session).sisteUtbetalingIdFor(fødselsnummer)
        }
    }
}
