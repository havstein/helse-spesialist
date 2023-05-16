package no.nav.helse.modell

import java.time.LocalDateTime
import java.util.UUID
import javax.sql.DataSource
import kotliquery.TransactionalSession
import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.mediator.Hendelsefabrikk
import no.nav.helse.mediator.meldinger.AdressebeskyttelseEndret
import no.nav.helse.mediator.meldinger.EndretSkjermetinfo
import no.nav.helse.mediator.meldinger.Godkjenningsbehov
import no.nav.helse.mediator.meldinger.GosysOppgaveEndret
import no.nav.helse.mediator.meldinger.Hendelse
import no.nav.helse.mediator.meldinger.NyeVarsler
import no.nav.helse.mediator.meldinger.OppdaterPersonsnapshot
import no.nav.helse.mediator.meldinger.OverstyringArbeidsforhold
import no.nav.helse.mediator.meldinger.OverstyringIgangsatt
import no.nav.helse.mediator.meldinger.OverstyringInntektOgRefusjon
import no.nav.helse.mediator.meldinger.OverstyringTidslinje
import no.nav.helse.mediator.meldinger.PåminnetGodkjenningsbehov
import no.nav.helse.mediator.meldinger.Sykefraværstilfeller
import no.nav.helse.mediator.meldinger.SøknadSendt
import no.nav.helse.mediator.meldinger.UtbetalingAnnullert
import no.nav.helse.mediator.meldinger.UtbetalingEndret
import no.nav.helse.mediator.meldinger.VedtakFattet
import no.nav.helse.mediator.meldinger.VedtaksperiodeEndret
import no.nav.helse.mediator.meldinger.VedtaksperiodeForkastet
import no.nav.helse.mediator.meldinger.VedtaksperiodeNyUtbetaling
import no.nav.helse.mediator.meldinger.VedtaksperiodeOpprettet
import no.nav.helse.mediator.meldinger.VedtaksperiodeReberegnet
import no.nav.helse.mediator.meldinger.løsninger.Saksbehandlerløsning
import no.nav.helse.modell.HendelseDao.Hendelsetype.ADRESSEBESKYTTELSE_ENDRET
import no.nav.helse.modell.HendelseDao.Hendelsetype.ENDRET_SKJERMETINFO
import no.nav.helse.modell.HendelseDao.Hendelsetype.GODKJENNING
import no.nav.helse.modell.HendelseDao.Hendelsetype.GOSYS_OPPGAVE_ENDRET
import no.nav.helse.modell.HendelseDao.Hendelsetype.NYE_VARSLER
import no.nav.helse.modell.HendelseDao.Hendelsetype.OPPDATER_PERSONSNAPSHOT
import no.nav.helse.modell.HendelseDao.Hendelsetype.OVERSTYRING
import no.nav.helse.modell.HendelseDao.Hendelsetype.OVERSTYRING_ARBEIDSFORHOLD
import no.nav.helse.modell.HendelseDao.Hendelsetype.OVERSTYRING_IGANGSATT
import no.nav.helse.modell.HendelseDao.Hendelsetype.OVERSTYRING_INNTEKT_OG_REFUSJON
import no.nav.helse.modell.HendelseDao.Hendelsetype.PÅMINNET_GODKJENNINGSBEHOV
import no.nav.helse.modell.HendelseDao.Hendelsetype.SAKSBEHANDLERLØSNING
import no.nav.helse.modell.HendelseDao.Hendelsetype.SYKEFRAVÆRSTILFELLER
import no.nav.helse.modell.HendelseDao.Hendelsetype.SØKNAD_SENDT
import no.nav.helse.modell.HendelseDao.Hendelsetype.UTBETALING_ANNULLERT
import no.nav.helse.modell.HendelseDao.Hendelsetype.UTBETALING_ENDRET
import no.nav.helse.modell.HendelseDao.Hendelsetype.VEDTAKSPERIODE_ENDRET
import no.nav.helse.modell.HendelseDao.Hendelsetype.VEDTAKSPERIODE_FORKASTET
import no.nav.helse.modell.HendelseDao.Hendelsetype.VEDTAKSPERIODE_NY_UTBETALING
import no.nav.helse.modell.HendelseDao.Hendelsetype.VEDTAKSPERIODE_OPPRETTET
import no.nav.helse.modell.HendelseDao.Hendelsetype.VEDTAKSPERIODE_REBEREGNET
import no.nav.helse.modell.HendelseDao.Hendelsetype.VEDTAK_FATTET
import no.nav.helse.modell.person.toFødselsnummer
import org.intellij.lang.annotations.Language

internal class HendelseDao(private val dataSource: DataSource) {
    internal fun opprett(hendelse: Hendelse) {
        sessionOf(dataSource).use { session ->
            session.transaction { transactionalSession ->
                transactionalSession.run {
                    opprettHendelse(hendelse)
                    hendelse.vedtaksperiodeId()?.let { opprettKobling(it, hendelse.id) }
                }
            }
        }
    }

    internal fun finnFødselsnummer(hendelseId: UUID): String {
        return sessionOf(dataSource).use { session ->
            @Language("PostgreSQL")
            val statement = """SELECT fodselsnummer FROM hendelse WHERE id = ?"""
            requireNotNull(session.run(queryOf(statement, hendelseId).map {
                it.long("fodselsnummer").toFødselsnummer()
            }.asSingle))
        }
    }

    internal fun finnAntallKorrigerteSøknader(vedtaksperiodeId: UUID): Int {
        return sessionOf(dataSource).use { session ->
            @Language("PostgreSQL")
            val statement = """
                SELECT count(1) AS antall
                FROM hendelse h, json_array_elements(h.data -> 'berørtePerioder') AS bp
                WHERE bp ->> 'vedtaksperiodeId' = :vedtaksperiodeId
                AND h.data ->> 'årsak' = 'KORRIGERT_SØKNAD'
                AND h.type='OVERSTYRING_IGANGSATT'
                """
            requireNotNull(session.run(queryOf(statement, mapOf("vedtaksperiodeId" to vedtaksperiodeId.toString())).map {
                it.int("antall")
            }.asSingle))
        }
    }

    internal fun erSisteOverstyringIgangsattKorrigertSøknad(vedtaksperiodeId: UUID): Boolean {
        return sessionOf(dataSource).use { session ->
            @Language("PostgreSQL")
            val statement = """
                SELECT h.data ->> 'årsak' AS årsak, h.data ->> '@opprettet' AS opprettet
                FROM hendelse h, json_array_elements(h.data -> 'berørtePerioder') AS bp
                WHERE bp ->> 'vedtaksperiodeId' = :vedtaksperiodeId
                  AND h.type='OVERSTYRING_IGANGSATT'
                ORDER BY opprettet DESC
                LIMIT 1
                """
            session.run(queryOf(statement, mapOf("vedtaksperiodeId" to vedtaksperiodeId.toString())).map {
                it.stringOrNull("årsak")?.let {årsak -> årsak == "KORRIGERT_SØKNAD"}
            }.asSingle) ?: false
        }
    }

    internal fun finnDatoForFørsteSøknadMottatt(vedtaksperiodeId: UUID): LocalDateTime? {
        return sessionOf(dataSource).use { session ->
            @Language("PostgreSQL")
            val statement = """
                SELECT data -> '@forårsaket_av' ->> 'opprettet' AS opprettet 
                FROM hendelse 
                WHERE data ->> 'vedtaksperiodeId' = :vedtaksperiodeId and type = 'VEDTAKSPERIODE_OPPRETTET'
            """
            session.run(queryOf(statement, mapOf("vedtaksperiodeId" to vedtaksperiodeId.toString())).map {
                it.stringOrNull("opprettet")?.let { dato -> LocalDateTime.parse(dato) }
            }.asSingle)
        }
    }

    internal fun finnUtbetalingsgodkjenningbehovJson(hendelseId: UUID): String {
        return finnJson(hendelseId, GODKJENNING)
    }

    private fun finnJson(hendelseId: UUID, hendelsetype: Hendelsetype): String {
        return requireNotNull(sessionOf(dataSource).use { session ->
            @Language("PostgreSQL")
            val statement = """SELECT data FROM hendelse WHERE id = ? AND type = ?"""
            session.run(queryOf(statement, hendelseId, hendelsetype.name).map { it.string("data") }.asSingle)
        })
    }

    private fun TransactionalSession.opprettHendelse(hendelse: Hendelse) {
        @Language("PostgreSQL")
        val hendelseStatement = """
            INSERT INTO hendelse(id, fodselsnummer, data, type)
                VALUES(?, ?, CAST(? as json), ?)
            ON CONFLICT DO NOTHING
            """
        run(
            queryOf(
                hendelseStatement,
                hendelse.id,
                hendelse.fødselsnummer().toLong(),
                hendelse.toJson(),
                tilHendelsetype(hendelse).name
            ).asUpdate
        )
    }

    private fun TransactionalSession.opprettKobling(vedtaksperiodeId: UUID, hendelseId: UUID) {
        @Language("PostgreSQL")
        val koblingStatement = "INSERT INTO vedtaksperiode_hendelse(vedtaksperiode_id, hendelse_ref) VALUES(?,?)"
        run(
            queryOf(
                koblingStatement,
                vedtaksperiodeId,
                hendelseId
            ).asUpdate
        )
    }

    internal fun harKoblingTil(vedtaksperiodeId: UUID): Boolean {
        return sessionOf(dataSource).use { session ->
            session.run(
                queryOf(
                    "SELECT 1 FROM vedtaksperiode_hendelse WHERE vedtaksperiode_id=?", vedtaksperiodeId
                ).map { it.boolean(1) }.asSingle
            )
        } ?: false
    }

    internal fun finn(id: UUID, hendelsefabrikk: Hendelsefabrikk) = sessionOf(dataSource).use { session ->
        session.run(queryOf("SELECT type,data FROM hendelse WHERE id = ?", id).map { row ->
            fraHendelsetype(enumValueOf(row.string("type")), row.string("data"), hendelsefabrikk)
        }.asSingle)
    }

    private fun fraHendelsetype(
        hendelsetype: Hendelsetype,
        json: String,
        hendelsefabrikk: Hendelsefabrikk,
    ): Hendelse =
        when (hendelsetype) {
            ADRESSEBESKYTTELSE_ENDRET -> hendelsefabrikk.adressebeskyttelseEndret(json)
            VEDTAKSPERIODE_ENDRET -> hendelsefabrikk.vedtaksperiodeEndret(json)
            VEDTAKSPERIODE_FORKASTET -> hendelsefabrikk.vedtaksperiodeForkastet(json)
            GODKJENNING -> hendelsefabrikk.godkjenning(json)
            PÅMINNET_GODKJENNINGSBEHOV -> hendelsefabrikk.påminnetGodkjenningsbehov(json)
            OVERSTYRING -> hendelsefabrikk.overstyringTidslinje(json)
            OVERSTYRING_INNTEKT_OG_REFUSJON -> hendelsefabrikk.overstyringInntektOgRefusjon(json)
            OVERSTYRING_ARBEIDSFORHOLD -> hendelsefabrikk.overstyringArbeidsforhold(json)
            OVERSTYRING_IGANGSATT -> hendelsefabrikk.overstyringIgangsatt(json)
            SAKSBEHANDLERLØSNING -> hendelsefabrikk.saksbehandlerløsning(json)
            UTBETALING_ANNULLERT -> hendelsefabrikk.utbetalingAnnullert(json)
            UTBETALING_ENDRET -> hendelsefabrikk.utbetalingEndret(json)
            OPPDATER_PERSONSNAPSHOT -> hendelsefabrikk.oppdaterPersonsnapshot(json)
            VEDTAKSPERIODE_REBEREGNET -> hendelsefabrikk.vedtaksperiodeReberegnet(json)
            GOSYS_OPPGAVE_ENDRET -> hendelsefabrikk.gosysOppgaveEndret(json)
            ENDRET_SKJERMETINFO -> hendelsefabrikk.endretSkjermetinfo(json)
            VEDTAK_FATTET -> hendelsefabrikk.vedtakFattet(json)
            NYE_VARSLER -> hendelsefabrikk.nyeVarsler(json)
            VEDTAKSPERIODE_OPPRETTET -> hendelsefabrikk.vedtaksperiodeOpprettet(json)
            SØKNAD_SENDT -> hendelsefabrikk.søknadSendt(json)
            VEDTAKSPERIODE_NY_UTBETALING -> hendelsefabrikk.vedtaksperiodeNyUtbetaling(json)
            SYKEFRAVÆRSTILFELLER -> hendelsefabrikk.sykefraværstilfeller(json)
        }

    private fun tilHendelsetype(hendelse: Hendelse) = when (hendelse) {
        is AdressebeskyttelseEndret -> ADRESSEBESKYTTELSE_ENDRET
        is VedtaksperiodeEndret -> VEDTAKSPERIODE_ENDRET
        is VedtaksperiodeForkastet -> VEDTAKSPERIODE_FORKASTET
        is Godkjenningsbehov -> GODKJENNING
        is PåminnetGodkjenningsbehov -> PÅMINNET_GODKJENNINGSBEHOV
        is OverstyringTidslinje -> OVERSTYRING
        is OverstyringInntektOgRefusjon -> OVERSTYRING_INNTEKT_OG_REFUSJON
        is OverstyringArbeidsforhold -> OVERSTYRING_ARBEIDSFORHOLD
        is OverstyringIgangsatt -> OVERSTYRING_IGANGSATT
        is Saksbehandlerløsning -> SAKSBEHANDLERLØSNING
        is UtbetalingAnnullert -> UTBETALING_ANNULLERT
        is OppdaterPersonsnapshot -> OPPDATER_PERSONSNAPSHOT
        is UtbetalingEndret -> UTBETALING_ENDRET
        is VedtaksperiodeReberegnet -> VEDTAKSPERIODE_REBEREGNET
        is GosysOppgaveEndret -> GOSYS_OPPGAVE_ENDRET
        is EndretSkjermetinfo -> ENDRET_SKJERMETINFO
        is VedtakFattet -> VEDTAK_FATTET
        is NyeVarsler -> NYE_VARSLER
        is VedtaksperiodeOpprettet -> VEDTAKSPERIODE_OPPRETTET
        is SøknadSendt -> SØKNAD_SENDT
        is VedtaksperiodeNyUtbetaling -> VEDTAKSPERIODE_NY_UTBETALING
        is Sykefraværstilfeller -> SYKEFRAVÆRSTILFELLER
        else -> throw IllegalArgumentException("ukjent hendelsetype: ${hendelse::class.simpleName}")
    }

    private enum class Hendelsetype {
        ADRESSEBESKYTTELSE_ENDRET, VEDTAKSPERIODE_ENDRET, VEDTAKSPERIODE_FORKASTET, GODKJENNING, OVERSTYRING,
        SAKSBEHANDLERLØSNING, UTBETALING_ANNULLERT, OPPDATER_PERSONSNAPSHOT, UTBETALING_ENDRET,
        VEDTAKSPERIODE_REBEREGNET, OVERSTYRING_INNTEKT_OG_REFUSJON, OVERSTYRING_ARBEIDSFORHOLD,
        OVERSTYRING_IGANGSATT, GOSYS_OPPGAVE_ENDRET, ENDRET_SKJERMETINFO, VEDTAK_FATTET,
        NYE_VARSLER, VEDTAKSPERIODE_OPPRETTET, SØKNAD_SENDT, VEDTAKSPERIODE_NY_UTBETALING, SYKEFRAVÆRSTILFELLER,
        PÅMINNET_GODKJENNINGSBEHOV
    }
}
