package no.nav.helse.mediator.oppgave

import kotliquery.sessionOf
import no.nav.helse.HelseDao
import no.nav.helse.db.AntallOppgaverFraDatabase
import no.nav.helse.db.BehandletOppgaveFraDatabaseForVisning
import no.nav.helse.db.EgenskapForDatabase
import no.nav.helse.db.OppgaveFraDatabase
import no.nav.helse.db.OppgaveFraDatabaseForVisning
import no.nav.helse.db.OppgaveRepository
import no.nav.helse.db.OppgavesorteringForDatabase
import no.nav.helse.db.TransactionalOppgaveDao
import no.nav.helse.modell.gosysoppgaver.OppgaveDataForAutomatisering
import no.nav.helse.modell.oppgave.Egenskap
import no.nav.helse.objectMapper
import no.nav.helse.rapids_rivers.asLocalDate
import java.util.UUID
import javax.sql.DataSource

class OppgaveDao(private val dataSource: DataSource) : HelseDao(dataSource), OppgaveRepository {
    override fun reserverNesteId(): Long {
        return sessionOf(dataSource).use { session ->
            session.transaction { transaction ->
                TransactionalOppgaveDao(transaction).reserverNesteId()
            }
        }
    }

    override fun finnOppgave(id: Long): OppgaveFraDatabase? {
        return sessionOf(dataSource).use { session ->
            session.transaction { transaction ->
                TransactionalOppgaveDao(transaction).finnOppgave(id)
            }
        }
    }

    override fun finnOppgaverForVisning(
        ekskluderEgenskaper: List<String>,
        saksbehandlerOid: UUID,
        offset: Int,
        limit: Int,
        sortering: List<OppgavesorteringForDatabase>,
        egneSakerPåVent: Boolean,
        egneSaker: Boolean,
        tildelt: Boolean?,
        grupperteFiltrerteEgenskaper: Map<Egenskap.Kategori, List<EgenskapForDatabase>>?,
    ): List<OppgaveFraDatabaseForVisning> {
        return sessionOf(dataSource).use { session ->
            session.transaction { transaction ->
                TransactionalOppgaveDao(transaction).finnOppgaverForVisning(
                    ekskluderEgenskaper,
                    saksbehandlerOid,
                    offset,
                    limit,
                    sortering,
                    egneSakerPåVent,
                    egneSaker,
                    tildelt,
                    grupperteFiltrerteEgenskaper,
                )
            }
        }
    }

    override fun finnEgenskaper(
        vedtaksperiodeId: UUID,
        utbetalingId: UUID,
    ): Set<EgenskapForDatabase>? =
        sessionOf(dataSource).use { session ->
            session.transaction { transaction ->
                TransactionalOppgaveDao(transaction).finnEgenskaper(vedtaksperiodeId, utbetalingId)
            }
        }

    override fun finnAntallOppgaver(saksbehandlerOid: UUID): AntallOppgaverFraDatabase {
        return sessionOf(dataSource).use { session ->
            session.transaction { transaction ->
                TransactionalOppgaveDao(transaction).finnAntallOppgaver(saksbehandlerOid)
            }
        }
    }

    override fun finnBehandledeOppgaver(
        behandletAvOid: UUID,
        offset: Int,
        limit: Int,
    ): List<BehandletOppgaveFraDatabaseForVisning> =
        sessionOf(dataSource).use { session ->
            session.transaction { transaction ->
                TransactionalOppgaveDao(transaction).finnBehandledeOppgaver(behandletAvOid, offset, limit)
            }
        }

    fun finnUtbetalingId(oppgaveId: Long) =
        asSQL(
            " SELECT utbetaling_id FROM oppgave WHERE id = :oppgaveId; ",
            mapOf("oppgaveId" to oppgaveId),
        ).single { it.uuid("utbetaling_id") }

    override fun finnSpleisBehandlingId(oppgaveId: Long) =
        sessionOf(dataSource).use { session ->
            session.transaction { transaction ->
                TransactionalOppgaveDao(transaction).finnSpleisBehandlingId(oppgaveId)
            }
        }

    override fun finnIdForAktivOppgave(vedtaksperiodeId: UUID) =
        sessionOf(dataSource).use { session ->
            session.transaction { transaction ->
                TransactionalOppgaveDao(transaction).finnIdForAktivOppgave(vedtaksperiodeId)
            }
        }

    fun finnOppgaveIdUansettStatus(fødselsnummer: String) =
        asSQL(
            """ SELECT o.id as oppgaveId
            FROM oppgave o
                     JOIN vedtak v ON v.id = o.vedtak_ref
                     JOIN person p ON v.person_ref = p.id
            WHERE p.fodselsnummer = :fodselsnummer
            ORDER BY o.id DESC
            LIMIT 1;
        """,
            mapOf("fodselsnummer" to fødselsnummer.toLong()),
        ).single { it.long("oppgaveId") }!!

    override fun finnVedtaksperiodeId(fødselsnummer: String) =
        asSQL(
            """ SELECT v.vedtaksperiode_id as vedtaksperiode_id
            FROM oppgave o
                     JOIN vedtak v ON v.id = o.vedtak_ref
                     JOIN person p ON v.person_ref = p.id
            WHERE p.fodselsnummer = :fodselsnummer
            AND status = 'AvventerSaksbehandler'::oppgavestatus;
        """,
            mapOf("fodselsnummer" to fødselsnummer.toLong()),
        ).single { it.uuid("vedtaksperiode_id") }!!

    override fun finnOppgaveId(fødselsnummer: String) =
        sessionOf(dataSource).use { session ->
            session.transaction { transaction ->
                TransactionalOppgaveDao(transaction).finnOppgaveId(fødselsnummer)
            }
        }

    override fun finnOppgaveId(utbetalingId: UUID) =
        sessionOf(dataSource).use { session ->
            session.transaction { transaction ->
                TransactionalOppgaveDao(transaction).finnOppgaveId(utbetalingId)
            }
        }

    fun finnVedtaksperiodeId(oppgaveId: Long) =
        requireNotNull(
            asSQL(
                """ SELECT v.vedtaksperiode_id
            FROM vedtak v
            INNER JOIN oppgave o on v.id = o.vedtak_ref
            WHERE o.id = :oppgaveId
        """,
                mapOf("oppgaveId" to oppgaveId),
            ).single { row -> row.uuid("vedtaksperiode_id") },
        )

    override fun opprettOppgave(
        id: Long,
        commandContextId: UUID,
        egenskaper: List<EgenskapForDatabase>,
        vedtaksperiodeId: UUID,
        utbetalingId: UUID,
        kanAvvises: Boolean,
    ) = sessionOf(dataSource, returnGeneratedKey = true).use { session ->
        session.transaction { transaction ->
            TransactionalOppgaveDao(transaction).opprettOppgave(
                id,
                commandContextId,
                egenskaper,
                vedtaksperiodeId,
                utbetalingId,
                kanAvvises,
            )
        }
    }

    override fun updateOppgave(
        oppgaveId: Long,
        oppgavestatus: String,
        ferdigstiltAv: String?,
        oid: UUID?,
        egenskaper: List<EgenskapForDatabase>,
    ) = sessionOf(dataSource).use { session ->
        session.transaction { transaction ->
            TransactionalOppgaveDao(transaction).updateOppgave(oppgaveId, oppgavestatus, ferdigstiltAv, oid, egenskaper)
        }
    }

    override fun finnHendelseId(id: Long) =
        sessionOf(dataSource).use { session ->
            session.transaction { transaction ->
                TransactionalOppgaveDao(transaction).finnHendelseId(id)
            }
        }

    override fun harGyldigOppgave(utbetalingId: UUID) =
        requireNotNull(
            asSQL(
                """ SELECT COUNT(1) AS oppgave_count FROM oppgave
            WHERE utbetaling_id = :utbetalingId AND status IN('AvventerSystem'::oppgavestatus, 'AvventerSaksbehandler'::oppgavestatus, 'Ferdigstilt'::oppgavestatus)
        """,
                mapOf("utbetalingId" to utbetalingId),
            ).single { it.int("oppgave_count") },
        ) > 0

    override fun harFerdigstiltOppgave(vedtaksperiodeId: UUID) =
        sessionOf(dataSource).use { session ->
            session.transaction { transaction ->
                TransactionalOppgaveDao(transaction).harFerdigstiltOppgave(vedtaksperiodeId)
            }
        }

    override fun venterPåSaksbehandler(oppgaveId: Long) =
        sessionOf(dataSource).use { session ->
            session.transaction { transaction ->
                TransactionalOppgaveDao(transaction).venterPåSaksbehandler(oppgaveId)
            }
        }

    override fun finnFødselsnummer(oppgaveId: Long) =
        sessionOf(dataSource).use { session ->
            session.transaction { transaction ->
                TransactionalOppgaveDao(transaction).finnFødselsnummer(oppgaveId)
            }
        }

    fun oppgaveDataForAutomatisering(oppgaveId: Long): OppgaveDataForAutomatisering? =
        asSQL(
            """ SELECT v.vedtaksperiode_id, v.fom, v.tom, o.utbetaling_id, h.id AS hendelseId, h.data AS godkjenningbehovJson, s.type as periodetype
            FROM vedtak v
            INNER JOIN oppgave o ON o.vedtak_ref = v.id
            INNER JOIN hendelse h ON h.id = (SELECT hendelse_id FROM command_context WHERE context_id = o.command_context_id LIMIT 1)
            INNER JOIN saksbehandleroppgavetype s ON s.vedtak_ref = v.id
            WHERE o.id = :oppgaveId 
        """,
            mapOf("oppgaveId" to oppgaveId),
        ).single {
            val json = objectMapper.readTree(it.string("godkjenningbehovJson"))
            val skjæringstidspunkt = json.path("Godkjenning").path("skjæringstidspunkt").asLocalDate()
            OppgaveDataForAutomatisering(
                oppgaveId = oppgaveId,
                vedtaksperiodeId = it.uuid("vedtaksperiode_id"),
                periodeFom = it.localDate("fom"),
                periodeTom = it.localDate("tom"),
                skjæringstidspunkt = skjæringstidspunkt,
                utbetalingId = it.uuid("utbetaling_id"),
                hendelseId = it.uuid("hendelseId"),
                godkjenningsbehovJson = it.string("godkjenningbehovJson"),
                periodetype = enumValueOf(it.string("periodetype")),
            )
        }

    override fun invaliderOppgaveFor(fødselsnummer: String) {
        sessionOf(dataSource).use { session ->
            session.transaction { transaction ->
                TransactionalOppgaveDao(transaction).invaliderOppgaveFor(fødselsnummer)
            }
        }
    }
}
