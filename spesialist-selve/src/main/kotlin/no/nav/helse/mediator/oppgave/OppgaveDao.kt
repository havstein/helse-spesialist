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
import no.nav.helse.db.PersonnavnFraDatabase
import no.nav.helse.db.SaksbehandlerFraDatabase
import no.nav.helse.db.SorteringsnøkkelForDatabase
import no.nav.helse.db.TransactionalOppgaveDao
import no.nav.helse.modell.gosysoppgaver.OppgaveDataForAutomatisering
import no.nav.helse.modell.oppgave.Egenskap
import no.nav.helse.objectMapper
import no.nav.helse.rapids_rivers.asLocalDate
import no.nav.helse.spesialist.api.graphql.schema.Mottaker
import java.time.LocalDate
import java.util.UUID
import javax.sql.DataSource

class OppgaveDao(private val dataSource: DataSource) : HelseDao(dataSource), OppgaveRepository {
    fun reserverNesteId(): Long {
        return asSQL(
            """
               SELECT nextval(pg_get_serial_sequence('oppgave', 'id')) as neste_id; 
            """,
        ).single { it.long("neste_id") }
            ?: throw IllegalStateException("Klarer ikke hente neste id i sekvens fra oppgave-tabellen")
    }

    override fun finnOppgave(id: Long): OppgaveFraDatabase? {
        return asSQL(
            """ 
            SELECT o.egenskaper, o.status, v.vedtaksperiode_id, o.ferdigstilt_av, o.ferdigstilt_av_oid, o.utbetaling_id, s.navn, s.epost, s.ident, s.oid, o.kan_avvises
            FROM oppgave o
            INNER JOIN vedtak v on o.vedtak_ref = v.id
            LEFT JOIN tildeling t on o.id = t.oppgave_id_ref
            LEFT JOIN saksbehandler s on s.oid = t.saksbehandler_ref
            WHERE o.id = :oppgaveId
            ORDER BY o.id DESC LIMIT 1
        """,
            mapOf("oppgaveId" to id),
        ).single { row ->
            val egenskaper: List<EgenskapForDatabase> = row.array<String>("egenskaper").toList().map { enumValueOf(it) }
            OppgaveFraDatabase(
                id = id,
                egenskaper = egenskaper,
                status = row.string("status"),
                vedtaksperiodeId = row.uuid("vedtaksperiode_id"),
                utbetalingId = row.uuid("utbetaling_id"),
                hendelseId = finnHendelseId(id),
                kanAvvises = row.boolean("kan_avvises"),
                ferdigstiltAvIdent = row.stringOrNull("ferdigstilt_av"),
                ferdigstiltAvOid = row.stringOrNull("ferdigstilt_av_oid")?.let(UUID::fromString),
                tildelt =
                    row.uuidOrNull("oid")?.let {
                        SaksbehandlerFraDatabase(
                            epostadresse = row.string("epost"),
                            oid = it,
                            navn = row.string("navn"),
                            ident = row.string("ident"),
                        )
                    },
            )
        }
    }

    internal fun finnOppgaverForVisning(
        ekskluderEgenskaper: List<String>,
        saksbehandlerOid: UUID,
        offset: Int = 0,
        limit: Int = Int.MAX_VALUE,
        sortering: List<OppgavesorteringForDatabase> = emptyList(),
        egneSakerPåVent: Boolean = false,
        egneSaker: Boolean = false,
        tildelt: Boolean? = null,
        grupperteFiltrerteEgenskaper: Map<Egenskap.Kategori, List<EgenskapForDatabase>>? = emptyMap(),
    ): List<OppgaveFraDatabaseForVisning> {
        val orderBy = if (sortering.isNotEmpty()) sortering.joinToString { it.nøkkelTilKolonne() } else "opprettet DESC"
        val egenskaperSomSkalEkskluderes = ekskluderEgenskaper.joinToString { """ '$it' """ }

        val ukategoriserteEgenskaper =
            grupperteFiltrerteEgenskaper?.get(Egenskap.Kategori.Ukategorisert)?.joinToString { """ '${it.name}' """ }
        val oppgavetypeEgenskaper =
            grupperteFiltrerteEgenskaper?.get(Egenskap.Kategori.Oppgavetype)?.joinToString { """ '${it.name}' """ }
        val periodetypeEgenskaper =
            grupperteFiltrerteEgenskaper?.get(Egenskap.Kategori.Periodetype)?.joinToString { """ '${it.name}' """ }
        val mottakerEgenskaper =
            grupperteFiltrerteEgenskaper?.get(Egenskap.Kategori.Mottaker)?.joinToString { """ '${it.name}' """ }
        val antallArbeidsforholdEgenskaper =
            grupperteFiltrerteEgenskaper?.get(Egenskap.Kategori.Inntektskilde)?.joinToString { """ '${it.name}' """ }
        val statusEgenskaper =
            grupperteFiltrerteEgenskaper?.get(Egenskap.Kategori.Status)?.joinToString { """ '${it.name}' """ }

        return asSQL(
            """
                SELECT
                o.id as oppgave_id, 
                p.aktor_id,
                v.vedtaksperiode_id, 
                pi.fornavn, pi.mellomnavn, pi.etternavn, 
                o.egenskaper,
                s.oid, s.ident, s.epost, s.navn,
                o.opprettet,
                os.soknad_mottatt AS opprinnelig_soknadsdato,
                o.kan_avvises,
                pv.frist,
                count(1) OVER() AS filtered_count
            FROM oppgave o
                INNER JOIN vedtak v ON o.vedtak_ref = v.id
                INNER JOIN person p ON v.person_ref = p.id
                INNER JOIN person_info pi ON p.info_ref = pi.id
                INNER JOIN opprinnelig_soknadsdato os ON os.vedtaksperiode_id = v.vedtaksperiode_id
                LEFT JOIN tildeling t ON o.id = t.oppgave_id_ref
                LEFT JOIN totrinnsvurdering ttv ON (ttv.vedtaksperiode_id = v.vedtaksperiode_id AND ttv.utbetaling_id_ref IS NULL)
                LEFT JOIN saksbehandler s ON t.saksbehandler_ref = s.oid
                LEFT JOIN pa_vent pv ON v.vedtaksperiode_id = pv.vedtaksperiode_id
                WHERE o.status = 'AvventerSaksbehandler'
                AND (:ingen_ukategoriserte_egenskaper OR egenskaper @> ARRAY[$ukategoriserteEgenskaper]::varchar[]) -- egenskaper saksbehandler har filtrert på
                AND (:ingen_oppgavetype_egenskaper OR egenskaper && ARRAY[$oppgavetypeEgenskaper]::varchar[]) -- egenskaper saksbehandler har filtrert på
                AND (:ingen_periodetype_egenskaper OR egenskaper && ARRAY[$periodetypeEgenskaper]::varchar[]) -- egenskaper saksbehandler har filtrert på
                AND (:ingen_mottakertype_egenskaper OR egenskaper && ARRAY[$mottakerEgenskaper]::varchar[]) -- egenskaper saksbehandler har filtrert på
                AND (:ingen_antallarbeidsforholdtype_egenskaper OR egenskaper && ARRAY[$antallArbeidsforholdEgenskaper]::varchar[]) -- egenskaper saksbehandler har filtrert på
                AND (:ingen_statustype_egenskaper OR egenskaper && ARRAY[$statusEgenskaper]::varchar[]) -- egenskaper saksbehandler har filtrert på
                AND NOT (egenskaper && ARRAY[$egenskaperSomSkalEkskluderes]::varchar[]) -- egenskaper saksbehandler ikke har tilgang til
                AND NOT (egenskaper && ARRAY['BESLUTTER']::varchar[] AND ttv.saksbehandler = :oid) -- hvis oppgaven er sendt til beslutter og saksbehandler var den som sendte
                AND 
                    CASE 
                        WHEN :egne_saker_pa_vent THEN t.saksbehandler_ref = :oid AND ('PÅ_VENT' = ANY(o.egenskaper))
                        WHEN :egne_saker THEN t.saksbehandler_ref = :oid AND NOT ('PÅ_VENT' = ANY(o.egenskaper))
                        ELSE true
                    END
                AND
                    CASE
                        WHEN :tildelt THEN t.saksbehandler_ref IS NOT NULL
                        WHEN :tildelt = false THEN t.saksbehandler_ref IS NULL
                        ELSE true
                    END
                ORDER BY $orderBy NULLS LAST
                OFFSET :offset
                LIMIT :limit
            """,
            mapOf(
                "oid" to saksbehandlerOid,
                "offset" to offset,
                "limit" to limit,
                "egne_saker_pa_vent" to egneSakerPåVent,
                "egne_saker" to egneSaker,
                "tildelt" to tildelt,
                "ingen_ukategoriserte_egenskaper" to (ukategoriserteEgenskaper == null),
                "ingen_oppgavetype_egenskaper" to (oppgavetypeEgenskaper == null),
                "ingen_periodetype_egenskaper" to (periodetypeEgenskaper == null),
                "ingen_mottakertype_egenskaper" to (mottakerEgenskaper == null),
                "ingen_antallarbeidsforholdtype_egenskaper" to (antallArbeidsforholdEgenskaper == null),
                "ingen_statustype_egenskaper" to (statusEgenskaper == null),
            ),
        ).list { row ->
            val egenskaper = row.array<String>("egenskaper").map { enumValueOf<EgenskapForDatabase>(it) }.toSet()
            OppgaveFraDatabaseForVisning(
                id = row.long("oppgave_id"),
                aktørId = row.string("aktor_id"),
                vedtaksperiodeId = row.uuid("vedtaksperiode_id"),
                navn =
                    PersonnavnFraDatabase(
                        row.string("fornavn"),
                        row.stringOrNull("mellomnavn"),
                        row.string("etternavn"),
                    ),
                egenskaper = egenskaper,
                tildelt =
                    row.uuidOrNull("oid")?.let {
                        SaksbehandlerFraDatabase(
                            epostadresse = row.string("epost"),
                            it,
                            row.string("navn"),
                            row.string("ident"),
                        )
                    },
                påVent = egenskaper.contains(EgenskapForDatabase.PÅ_VENT),
                opprettet = row.localDateTime("opprettet"),
                opprinneligSøknadsdato = row.localDateTime("opprinnelig_soknadsdato"),
                tidsfrist = row.localDateOrNull("frist"),
                filtrertAntall = row.int("filtered_count"),
            )
        }
    }

    internal fun finnEgenskaper(
        vedtaksperiodeId: UUID,
        utbetalingId: UUID,
    ): Set<EgenskapForDatabase>? =
        asSQL(
            """
            SELECT o.egenskaper FROM oppgave o 
            INNER JOIN vedtak v ON o.vedtak_ref = v.id
            WHERE v.vedtaksperiode_id = :vedtaksperiodeId
            AND o.utbetaling_id = :utbetalingId
            ORDER BY o.opprettet DESC
            LIMIT 1
            """.trimIndent(),
            mapOf(
                "vedtaksperiodeId" to vedtaksperiodeId,
                "utbetalingId" to utbetalingId,
            ),
        ).single { row -> row.array<String>("egenskaper").map { enumValueOf<EgenskapForDatabase>(it) }.toSet() }

    internal fun finnAntallOppgaver(saksbehandlerOid: UUID): AntallOppgaverFraDatabase {
        return asSQL(
            """ 
            SELECT 
                count(*) FILTER ( WHERE NOT o.egenskaper @> ARRAY['PÅ_VENT']::varchar[] ) AS antall_mine_saker,
                count(*) FILTER ( WHERE o.egenskaper @> ARRAY['PÅ_VENT']::varchar[] ) AS antall_mine_saker_på_vent
            from oppgave o 
                LEFT JOIN tildeling t ON o.id = t.oppgave_id_ref
            WHERE o.status = 'AvventerSaksbehandler'
            AND t.saksbehandler_ref = :oid
        """,
            mapOf("oid" to saksbehandlerOid),
        ).single { row ->
            AntallOppgaverFraDatabase(
                antallMineSaker = row.int("antall_mine_saker"),
                antallMineSakerPåVent = row.int("antall_mine_saker_på_vent"),
            )
        } ?: AntallOppgaverFraDatabase(antallMineSaker = 0, antallMineSakerPåVent = 0)
    }

    internal fun finnBehandledeOppgaver(
        behandletAvOid: UUID,
        offset: Int = 0,
        limit: Int = Int.MAX_VALUE,
    ): List<BehandletOppgaveFraDatabaseForVisning> =
        asSQL(
            """
        SELECT 
            o.id as oppgave_id,
            p.aktor_id,
            o.egenskaper,
            o.oppdatert as ferdigstilt_tidspunkt,
            o.ferdigstilt_av,
            pi.fornavn, pi.mellomnavn, pi.etternavn,
            count(1) OVER() AS filtered_count
        FROM oppgave o
            INNER JOIN vedtak v ON o.vedtak_ref = v.id
            INNER JOIN person p ON v.person_ref = p.id
            INNER JOIN person_info pi ON p.info_ref = pi.id
            LEFT JOIN (SELECT DISTINCT ON (vedtaksperiode_id) vedtaksperiode_id, saksbehandler, utbetaling_id
                     FROM totrinnsvurdering
                     INNER JOIN utbetaling_id ui on ui.id = utbetaling_id_ref
                     WHERE utbetaling_id_ref IS NOT NULL
                     ORDER BY vedtaksperiode_id, totrinnsvurdering.id DESC
                 ) ttv ON ttv.vedtaksperiode_id = v.vedtaksperiode_id AND ttv.utbetaling_id = o.utbetaling_id
        WHERE (ttv.saksbehandler = :oid OR o.ferdigstilt_av_oid = :oid) AND (o.status in ('Ferdigstilt', 'AvventerSystem'))
            AND o.oppdatert::date = :fom::date
        ORDER BY o.oppdatert
        OFFSET :offset
        LIMIT :limit;
    """,
            mapOf("oid" to behandletAvOid, "fom" to LocalDate.now(), "offset" to offset, "limit" to limit),
        ).list { row ->
            BehandletOppgaveFraDatabaseForVisning(
                id = row.long("oppgave_id"),
                aktørId = row.string("aktor_id"),
                egenskaper = row.array<String>("egenskaper").map { enumValueOf<EgenskapForDatabase>(it) }.toSet(),
                ferdigstiltTidspunkt = row.localDateTime("ferdigstilt_tidspunkt"),
                ferdigstiltAv = row.stringOrNull("ferdigstilt_av"),
                navn =
                    PersonnavnFraDatabase(
                        row.string("fornavn"),
                        row.stringOrNull("mellomnavn"),
                        row.string("etternavn"),
                    ),
                filtrertAntall = row.int("filtered_count"),
            )
        }

    private fun OppgavesorteringForDatabase.nøkkelTilKolonne() =
        when (this.nøkkel) {
            SorteringsnøkkelForDatabase.TILDELT_TIL -> "navn"
            SorteringsnøkkelForDatabase.OPPRETTET -> "opprettet"
            SorteringsnøkkelForDatabase.TIDSFRIST -> "frist"
            SorteringsnøkkelForDatabase.SØKNAD_MOTTATT -> "opprinnelig_soknadsdato"
        } + if (this.stigende) " ASC" else " DESC"

    fun finnUtbetalingId(oppgaveId: Long) =
        asSQL(
            " SELECT utbetaling_id FROM oppgave WHERE id = :oppgaveId; ",
            mapOf("oppgaveId" to oppgaveId),
        ).single { it.uuid("utbetaling_id") }

    fun finnSpleisBehandlingId(oppgaveId: Long) =
        requireNotNull(
            asSQL(
                """ SELECT spleis_behandling_id FROM oppgave o
                 INNER JOIN selve_vedtaksperiode_generasjon svg ON svg.unik_id = o.generasjon_ref
                WHERE o.id = :oppgaveId; """,
                mapOf("oppgaveId" to oppgaveId),
            ).single { it.uuid("spleis_behandling_id") },
        )

    fun finnIdForAktivOppgave(vedtaksperiodeId: UUID) =
        asSQL(
            """
            SELECT id FROM oppgave
            WHERE vedtak_ref =
                (SELECT id FROM vedtak WHERE vedtaksperiode_id = :vedtaksperiodeId)
                    AND status not in ('Ferdigstilt'::oppgavestatus, 'Invalidert'::oppgavestatus)
            ORDER BY opprettet DESC
            LIMIT 1
        """,
            mapOf("vedtaksperiodeId" to vedtaksperiodeId),
        ).single { it.long("id") }

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
        asSQL(
            """ SELECT o.id as oppgaveId
            FROM oppgave o
                     JOIN vedtak v ON v.id = o.vedtak_ref
                     JOIN person p ON v.person_ref = p.id
            WHERE o.status = 'AvventerSaksbehandler'::oppgavestatus
              AND p.fodselsnummer = :fodselsnummer;
        """,
            mapOf("fodselsnummer" to fødselsnummer.toLong()),
        ).single { it.long("oppgaveId") }

    fun finnOppgaveId(utbetalingId: UUID) =
        asSQL(
            """ SELECT o.id as oppgaveId
            FROM oppgave o WHERE o.utbetaling_id = :utbetaling_id
            AND o.status NOT IN ('Invalidert'::oppgavestatus, 'Ferdigstilt'::oppgavestatus)
        """,
            mapOf("utbetaling_id" to utbetalingId),
        ).single { it.long("oppgaveId") }

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

    private fun finnArbeidsgiverbeløpOgPersonbeløp(
        vedtaksperiodeId: UUID,
        utbetalingId: UUID,
    ) = requireNotNull(
        asSQL(
            """ SELECT SUM(ABS(arbeidsgiverbeløp)) as sumArbeidsgiverbeløp, SUM(ABS(personbeløp)) as sumPersonbeløp
            FROM utbetaling_id 
            WHERE person_ref=(SELECT person_ref FROM utbetaling_id WHERE utbetaling_id=:utbetalingId) AND 
                utbetaling_id.utbetaling_id IN (
                    SELECT utbetaling_id 
                    FROM selve_vedtaksperiode_generasjon 
                    WHERE skjæringstidspunkt=(
                        SELECT skjæringstidspunkt 
                            FROM selve_vedtaksperiode_generasjon
                            WHERE vedtaksperiode_id=:vedtaksperiodeId AND tilstand='VidereBehandlingAvklares'
                        ) AND tilstand='VidereBehandlingAvklares'
                    )
        """,
            mapOf(
                "utbetalingId" to utbetalingId,
                "vedtaksperiodeId" to vedtaksperiodeId,
            ),
        ).single { row ->
            Pair(row.intOrNull("sumArbeidsgiverbeløp") ?: 0, row.intOrNull("sumPersonbeløp") ?: 0)
        },
    )

    private fun finnMottaker(
        harArbeidsgiverbeløp: Boolean,
        harPersonbeløp: Boolean,
    ): Mottaker? {
        return when {
            harArbeidsgiverbeløp && harPersonbeløp -> Mottaker.BEGGE
            harPersonbeløp -> Mottaker.SYKMELDT
            harArbeidsgiverbeløp -> Mottaker.ARBEIDSGIVER
            else -> null
        }
    }

    fun opprettOppgave(
        id: Long,
        commandContextId: UUID,
        egenskaper: List<EgenskapForDatabase>,
        vedtaksperiodeId: UUID,
        utbetalingId: UUID,
        kanAvvises: Boolean,
    ) = requireNotNull(
        run {
            val vedtakRef = vedtakRef(vedtaksperiodeId)
            val personRef = personRef(vedtaksperiodeId)

            val (arbeidsgiverBeløp, personBeløp) = finnArbeidsgiverbeløpOgPersonbeløp(vedtaksperiodeId, utbetalingId)
            val mottaker = finnMottaker(arbeidsgiverBeløp > 0, personBeløp > 0)

            asSQL(
                """
                    INSERT INTO oppgave(id, oppdatert, status, ferdigstilt_av, ferdigstilt_av_oid, vedtak_ref, generasjon_ref, command_context_id, utbetaling_id, mottaker, egenskaper, kan_avvises)      
                    SELECT 
                        :id, 
                        now(), 
                        CAST(:oppgavestatus as oppgavestatus), 
                        :ferdigstiltAv, 
                        :ferdigstiltAvOid, 
                        :vedtakRef,
                        (
                            SELECT unik_id FROM selve_vedtaksperiode_generasjon svg WHERE vedtaksperiode_id = (
                                SELECT vedtaksperiode_id FROM vedtak v WHERE v.id = :vedtakRef
                            ) ORDER BY id DESC LIMIT 1
                        ),
                        :commandContextId, 
                        :utbetalingId,
                        CAST(:mottaker as mottakertype), 
                        CAST(:egenskaper as varchar[]), 
                        :kanAvvises
                    WHERE
                        NOT EXISTS(
                            SELECT 1 FROM oppgave o
                                LEFT JOIN vedtak v on v.id = o.vedtak_ref
                                WHERE o.status='AvventerSaksbehandler'::oppgavestatus 
                                    AND v.person_ref=:personRef
                        )
                    ;
                """,
                mapOf(
                    "id" to id,
                    "oppgavestatus" to "AvventerSaksbehandler",
                    "ferdigstiltAv" to null,
                    "ferdigstiltAvOid" to null,
                    "vedtakRef" to vedtakRef,
                    "commandContextId" to commandContextId,
                    "utbetalingId" to utbetalingId,
                    "mottaker" to mottaker?.name,
                    "egenskaper" to egenskaper.joinToString(prefix = "{", postfix = "}"),
                    "kanAvvises" to kanAvvises,
                    "personRef" to personRef,
                ),
            ).updateAndReturnGeneratedKey()
        },
    ) { "Kunne ikke opprette oppgave for vedtak: $vedtaksperiodeId" }

    private fun personRef(vedtaksperiodeId: UUID) =
        requireNotNull(
            asSQL(
                "SELECT person_ref FROM vedtak WHERE vedtaksperiode_id = :vedtaksperiodeId;",
                mapOf("vedtaksperiodeId" to vedtaksperiodeId),
            ).single { it.long("person_ref") },
        ) { "Kunne ikke finne person for vedtaksperiodeId $vedtaksperiodeId" }

    private fun vedtakRef(vedtaksperiodeId: UUID) =
        requireNotNull(
            asSQL(
                "SELECT id FROM vedtak WHERE vedtaksperiode_id = :vedtaksperiodeId;",
                mapOf("vedtaksperiodeId" to vedtaksperiodeId),
            ).single { it.long("id") },
        ) { "Kunne ikke finne vedtak for vedtaksperiodeId $vedtaksperiodeId" }

    fun updateOppgave(
        oppgaveId: Long,
        oppgavestatus: String,
        ferdigstiltAv: String? = null,
        oid: UUID? = null,
        egenskaper: List<EgenskapForDatabase>,
    ): Int {
        return asSQL(
            """
                UPDATE oppgave
                SET ferdigstilt_av = :ferdigstiltAv, ferdigstilt_av_oid = :oid, status = :oppgavestatus::oppgavestatus, egenskaper = :egenskaper::varchar[]
                WHERE id=:oppgaveId; 
            """,
            mapOf(
                "ferdigstiltAv" to ferdigstiltAv,
                "oid" to oid,
                "oppgavestatus" to oppgavestatus,
                "egenskaper" to egenskaper.joinToString(prefix = "{", postfix = "}"),
                "oppgaveId" to oppgaveId,
            ),
        ).update()
    }

    override fun finnHendelseId(id: Long) =
        requireNotNull(
            asSQL(
                """
                SELECT DISTINCT hendelse_id 
                FROM command_context 
                WHERE context_id = (SELECT command_context_id FROM oppgave WHERE id = :oppgaveId);
            """,
                mapOf("oppgaveId" to id),
            )
                .single { row -> row.uuid("hendelse_id") },
        )

    override fun harGyldigOppgave(utbetalingId: UUID) =
        requireNotNull(
            asSQL(
                """ SELECT COUNT(1) AS oppgave_count FROM oppgave
            WHERE utbetaling_id = :utbetalingId AND status IN('AvventerSystem'::oppgavestatus, 'AvventerSaksbehandler'::oppgavestatus, 'Ferdigstilt'::oppgavestatus)
        """,
                mapOf("utbetalingId" to utbetalingId),
            ).single { it.int("oppgave_count") },
        ) > 0

    fun harFerdigstiltOppgave(vedtaksperiodeId: UUID) =
        requireNotNull(
            asSQL(
                """ SELECT COUNT(1) AS oppgave_count FROM oppgave o
                INNER JOIN vedtak v on o.vedtak_ref = v.id
                WHERE v.vedtaksperiode_id = :vedtaksperiodeId AND o.status = 'Ferdigstilt'::oppgavestatus
            """,
                mapOf("vedtaksperiodeId" to vedtaksperiodeId),
            ).single { it.int("oppgave_count") },
        ) > 0

    fun venterPåSaksbehandler(oppgaveId: Long) =
        requireNotNull(
            asSQL(
                """ 
            SELECT EXISTS (
                SELECT 1 FROM oppgave WHERE id=:oppgaveId AND status IN('AvventerSaksbehandler'::oppgavestatus)
            )
        """,
                mapOf("oppgaveId" to oppgaveId),
            ).single { it.boolean(1) },
        )

    fun finnFødselsnummer(oppgaveId: Long) =
        requireNotNull(
            asSQL(
                """ SELECT fodselsnummer from person
            INNER JOIN vedtak v on person.id = v.person_ref
            INNER JOIN oppgave o on v.id = o.vedtak_ref
            WHERE o.id = :oppgaveId
        """,
                mapOf("oppgaveId" to oppgaveId),
            ).single { it.long("fodselsnummer").toFødselsnummer() },
        )

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

    private fun Long.toFødselsnummer() = if (this < 10000000000) "0$this" else this.toString()
}
