package no.nav.helse.modell.automatisering

import kotliquery.TransactionalSession
import net.logstash.logback.argument.StructuredArguments.keyValue
import net.logstash.logback.argument.StructuredArguments.kv
import no.nav.helse.db.AutomatiseringRepository
import no.nav.helse.db.EgenAnsattRepository
import no.nav.helse.db.GenerasjonDao
import no.nav.helse.db.MeldingRepository
import no.nav.helse.db.OverstyringRepository
import no.nav.helse.db.PersonRepository
import no.nav.helse.db.PgVedtakDao
import no.nav.helse.db.RisikovurderingRepository
import no.nav.helse.db.TransactionalÅpneGosysOppgaverDao
import no.nav.helse.db.VedtakDao
import no.nav.helse.db.VergemålRepository
import no.nav.helse.db.ÅpneGosysOppgaverRepository
import no.nav.helse.mediator.Subsumsjonsmelder
import no.nav.helse.modell.MeldingDao
import no.nav.helse.modell.MeldingDao.OverstyringIgangsattKorrigertSøknad
import no.nav.helse.modell.Toggle
import no.nav.helse.modell.egenansatt.EgenAnsattDao
import no.nav.helse.modell.overstyring.OverstyringDao
import no.nav.helse.modell.person.HentEnhetløsning.Companion.erEnhetUtland
import no.nav.helse.modell.person.PersonDao
import no.nav.helse.modell.risiko.RisikovurderingDao
import no.nav.helse.modell.stoppautomatiskbehandling.StansAutomatiskBehandlingMediator
import no.nav.helse.modell.sykefraværstilfelle.Sykefraværstilfelle
import no.nav.helse.modell.utbetaling.Refusjonstype
import no.nav.helse.modell.utbetaling.Utbetaling
import no.nav.helse.modell.vedtaksperiode.Inntektskilde
import no.nav.helse.modell.vedtaksperiode.Periodetype
import no.nav.helse.modell.vedtaksperiode.Periodetype.FORLENGELSE
import no.nav.helse.modell.vedtaksperiode.Periodetype.FØRSTEGANGSBEHANDLING
import no.nav.helse.modell.vedtaksperiode.PgGenerasjonDao
import no.nav.helse.modell.vergemal.VergemålDao
import no.nav.helse.spesialist.api.StansAutomatiskBehandlinghåndterer
import no.nav.helse.spesialist.api.person.Adressebeskyttelse
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.UUID

internal class Automatisering(
    private val risikovurderingRepository: RisikovurderingRepository,
    private val stansAutomatiskBehandlinghåndterer: StansAutomatiskBehandlinghåndterer,
    private val automatiseringRepository: AutomatiseringRepository,
    private val åpneGosysOppgaverRepository: ÅpneGosysOppgaverRepository,
    private val vergemålRepository: VergemålRepository,
    private val personRepository: PersonRepository,
    private val vedtakDao: VedtakDao,
    private val overstyringRepository: OverstyringRepository,
    private val stikkprøver: Stikkprøver,
    private val meldingRepository: MeldingRepository,
    private val generasjonDao: GenerasjonDao,
    private val egenAnsattRepository: EgenAnsattRepository,
) {
    object Factory {
        fun automatisering(
            transactionalSession: TransactionalSession,
            subsumsjonsmelderProvider: () -> Subsumsjonsmelder,
            stikkprøver: Stikkprøver,
        ): Automatisering {
            return Automatisering(
                risikovurderingRepository = RisikovurderingDao(transactionalSession),
                stansAutomatiskBehandlinghåndterer =
                    StansAutomatiskBehandlingMediator.Factory.stansAutomatiskBehandlingMediator(
                        transactionalSession,
                        subsumsjonsmelderProvider,
                    ),
                automatiseringRepository = AutomatiseringDao(transactionalSession),
                åpneGosysOppgaverRepository = TransactionalÅpneGosysOppgaverDao(transactionalSession),
                vergemålRepository = VergemålDao(transactionalSession),
                personRepository = PersonDao(transactionalSession),
                vedtakDao = PgVedtakDao(transactionalSession),
                overstyringRepository = OverstyringDao(transactionalSession),
                stikkprøver = stikkprøver,
                meldingRepository = MeldingDao(transactionalSession),
                generasjonDao = PgGenerasjonDao(transactionalSession),
                egenAnsattRepository = EgenAnsattDao(transactionalSession),
            )
        }
    }

    private companion object {
        private val logger = LoggerFactory.getLogger(Automatisering::class.java)
        private val sikkerLogg = LoggerFactory.getLogger("tjenestekall")
    }

    internal fun settInaktiv(
        vedtaksperiodeId: UUID,
        hendelseId: UUID,
    ) {
        automatiseringRepository.settAutomatiseringInaktiv(vedtaksperiodeId, hendelseId)
        automatiseringRepository.settAutomatiseringProblemInaktiv(vedtaksperiodeId, hendelseId)
    }

    internal fun utfør(
        fødselsnummer: String,
        vedtaksperiodeId: UUID,
        hendelseId: UUID,
        utbetaling: Utbetaling,
        periodetype: Periodetype,
        sykefraværstilfelle: Sykefraværstilfelle,
        organisasjonsnummer: String,
        onAutomatiserbar: () -> Unit,
    ) {
        val problemer =
            vurder(fødselsnummer, vedtaksperiodeId, utbetaling, periodetype, sykefraværstilfelle, organisasjonsnummer)
        val erUTS = utbetaling.harEndringIUtbetalingTilSykmeldt()
        val flereArbeidsgivere = vedtakDao.finnInntektskilde(vedtaksperiodeId) == Inntektskilde.FLERE_ARBEIDSGIVERE
        val erFørstegangsbehandling = periodetype == FØRSTEGANGSBEHANDLING

        val utfallslogger = { tekst: String ->
            sikkerLogg.info(
                tekst,
                keyValue("vedtaksperiodeId", vedtaksperiodeId),
                keyValue("utbetalingId", utbetaling.utbetalingId),
                problemer,
            )
        }

        if (Toggle.AutomatiserSpesialsak.enabled && erSpesialsakSomKanAutomatiseres(sykefraværstilfelle, utbetaling, vedtaksperiodeId)) {
            utfallslogger("Automatiserer spesialsak med {} ({})")
            onAutomatiserbar()
            sykefraværstilfelle.automatiskGodkjennSpesialsakvarsler(vedtaksperiodeId)
            automatiseringRepository.automatisert(vedtaksperiodeId, hendelseId, utbetaling.utbetalingId)
            return
        }

        if (problemer.isNotEmpty()) {
            utfallslogger("Automatiserer ikke {} ({}) fordi: {}")
            automatiseringRepository.manuellSaksbehandling(problemer, vedtaksperiodeId, hendelseId, utbetaling.utbetalingId)
            return
        }

        overstyringIgangsattKorrigertSøknad(fødselsnummer, vedtaksperiodeId)?.let {
            val kanKorrigertSøknadAutomatiseres = kanKorrigertSøknadAutomatiseres(vedtaksperiodeId, it)
            if (!kanKorrigertSøknadAutomatiseres.first) {
                utfallslogger("Automatiserer ikke {} ({}) fordi: ${kanKorrigertSøknadAutomatiseres.second}")
                automatiseringRepository.manuellSaksbehandling(
                    listOf("${kanKorrigertSøknadAutomatiseres.second}"),
                    vedtaksperiodeId,
                    hendelseId,
                    utbetaling.utbetalingId,
                )
                return
            }
        }

        if (egenAnsattRepository.erEgenAnsatt(
                fødselsnummer,
            ) != true && personRepository.finnAdressebeskyttelse(fødselsnummer) == Adressebeskyttelse.Ugradert
        ) {
            avgjørStikkprøve(erUTS, flereArbeidsgivere, erFørstegangsbehandling)?.let {
                tilStikkprøve(it, utfallslogger, vedtaksperiodeId, hendelseId, utbetaling.utbetalingId)
                return@utfør
            }
        } else {
            logger.info("Vurderer ikke om det skal tas stikkprøve.")
        }
        utfallslogger("Automatiserer {} ({})")
        onAutomatiserbar()
        automatiseringRepository.automatisert(vedtaksperiodeId, hendelseId, utbetaling.utbetalingId)
    }

    private fun overstyringIgangsattKorrigertSøknad(
        fødselsnummer: String,
        vedtaksperiodeId: UUID,
    ): OverstyringIgangsattKorrigertSøknad? =
        generasjonDao.førsteGenerasjonVedtakFattetTidspunkt(vedtaksperiodeId)?.let {
            meldingRepository.sisteOverstyringIgangsattOmKorrigertSøknad(fødselsnummer, vedtaksperiodeId)
        }

    private fun kanKorrigertSøknadAutomatiseres(
        vedtaksperiodeId: UUID,
        overstyringIgangsattKorrigertSøknad: OverstyringIgangsattKorrigertSøknad,
    ): Pair<Boolean, String?> {
        val hendelseId = UUID.fromString(overstyringIgangsattKorrigertSøknad.meldingId)
        if (meldingRepository.erAutomatisertKorrigertSøknadHåndtert(hendelseId)) return Pair(true, null)

        val orgnummer = vedtakDao.finnOrgnummer(vedtaksperiodeId)
        val vedtaksperiodeIdKorrigertSøknad =
            overstyringIgangsattKorrigertSøknad.let { overstyring ->
                overstyring.berørtePerioder.find {
                    it.orgnummer == orgnummer &&
                        overstyringIgangsattKorrigertSøknad.periodeForEndringFom.isEqual(
                            it.periodeFom,
                        )
                }?.vedtaksperiodeId
            }

        vedtaksperiodeIdKorrigertSøknad?.let {
            val merEnn6MånederSidenVedtakPåFørsteMottattSøknad =
                generasjonDao.førsteGenerasjonVedtakFattetTidspunkt(it)
                    ?.isBefore(LocalDateTime.now().minusMonths(6))
                    ?: true
            val antallKorrigeringer = meldingRepository.finnAntallAutomatisertKorrigertSøknad(it)
            meldingRepository.opprettAutomatiseringKorrigertSøknad(it, hendelseId)

            if (merEnn6MånederSidenVedtakPåFørsteMottattSøknad) {
                return Pair(
                    false,
                    "Mer enn 6 måneder siden vedtak på første mottatt søknad",
                )
            }
            if (antallKorrigeringer >= 2) return Pair(false, "Antall automatisk godkjente korrigerte søknader er større eller lik 2")

            return Pair(true, null)
        }

        // Hvis vi ikke finner vedtaksperiodeIdKorrigertSøknad, så er det fordi vi vedtaksperioden som er korrigert er AUU som vi ikke trenger å telle
        return Pair(true, null)
    }

    private fun avgjørStikkprøve(
        UTS: Boolean,
        flereArbeidsgivere: Boolean,
        førstegangsbehandling: Boolean,
    ): String? {
        when {
            UTS ->
                when {
                    flereArbeidsgivere ->
                        when {
                            førstegangsbehandling && stikkprøver.utsFlereArbeidsgivereFørstegangsbehandling() -> return "UTS, flere arbeidsgivere, førstegangsbehandling"
                            !førstegangsbehandling && stikkprøver.utsFlereArbeidsgivereForlengelse() -> return "UTS, flere arbeidsgivere, forlengelse"
                        }
                    !flereArbeidsgivere ->
                        when {
                            førstegangsbehandling && stikkprøver.utsEnArbeidsgiverFørstegangsbehandling() -> return "UTS, en arbeidsgiver, førstegangsbehandling"
                            !førstegangsbehandling && stikkprøver.utsEnArbeidsgiverForlengelse() -> return "UTS, en arbeidsgiver, forlengelse"
                        }
                }
            flereArbeidsgivere ->
                when {
                    førstegangsbehandling && stikkprøver.fullRefusjonFlereArbeidsgivereFørstegangsbehandling() -> return "Refusjon, flere arbeidsgivere, førstegangsbehandling"
                    !førstegangsbehandling && stikkprøver.fullRefusjonFlereArbeidsgivereForlengelse() -> return "Refusjon, flere arbeidsgivere, forlengelse"
                }
            stikkprøver.fullRefusjonEnArbeidsgiver() -> return "Refusjon, en arbeidsgiver"
        }
        return null
    }

    private fun tilStikkprøve(
        årsak: String,
        utfallslogger: (String) -> Unit,
        vedtaksperiodeId: UUID,
        hendelseId: UUID,
        utbetalingId: UUID,
    ) {
        utfallslogger("Automatiserer ikke {} ({}), plukket ut til stikkprøve for $årsak")
        automatiseringRepository.stikkprøve(vedtaksperiodeId, hendelseId, utbetalingId)
        logger.info(
            "Automatisk godkjenning av {} avbrutt, sendes til manuell behandling",
            keyValue("vedtaksperiodeId", vedtaksperiodeId),
        )
    }

    private fun vurder(
        fødselsnummer: String,
        vedtaksperiodeId: UUID,
        utbetaling: Utbetaling,
        periodetype: Periodetype,
        sykefraværstilfelle: Sykefraværstilfelle,
        organisasjonsnummer: String,
    ): List<String> {
        val risikovurdering =
            risikovurderingRepository.hentRisikovurdering(vedtaksperiodeId)
                ?: validering("Mangler vilkårsvurdering for arbeidsuførhet, aktivitetsplikt eller medvirkning") { false }
        val unntattFraAutomatisering =
            stansAutomatiskBehandlinghåndterer.sjekkOmAutomatiseringErStanset(
                fødselsnummer,
                vedtaksperiodeId,
                organisasjonsnummer,
            )
        val forhindrerAutomatisering = sykefraværstilfelle.forhindrerAutomatisering(vedtaksperiodeId)
        val harVergemål = vergemålRepository.harVergemål(fødselsnummer) ?: false
        val tilhørerUtlandsenhet = erEnhetUtland(personRepository.finnEnhetId(fødselsnummer))
        val antallÅpneGosysoppgaver = åpneGosysOppgaverRepository.antallÅpneOppgaver(fødselsnummer)
        val harPågåendeOverstyring = overstyringRepository.harVedtaksperiodePågåendeOverstyring(vedtaksperiodeId)
        val harUtbetalingTilSykmeldt = utbetaling.harEndringIUtbetalingTilSykmeldt()

        val skalStoppesPgaUTS = harUtbetalingTilSykmeldt && periodetype !in listOf(FORLENGELSE, FØRSTEGANGSBEHANDLING)

        return valider(
            risikovurdering,
            validering("Unntatt fra automatisk godkjenning") { !unntattFraAutomatisering },
            validering("Har varsler") { !forhindrerAutomatisering },
            validering("Det finnes åpne oppgaver på sykepenger i Gosys") {
                antallÅpneGosysoppgaver?.let { it == 0 } ?: false
            },
            validering("Bruker er under verge") { !harVergemål },
            validering("Bruker tilhører utlandsenhet") { !tilhørerUtlandsenhet },
            validering("Utbetaling til sykmeldt") { !skalStoppesPgaUTS },
            AutomatiserRevurderinger(utbetaling, fødselsnummer, vedtaksperiodeId),
            validering("Vedtaksperioden har en pågående overstyring") { !harPågåendeOverstyring },
        )
    }

    private fun valider(vararg valideringer: AutomatiseringValidering) =
        valideringer.toList()
            .filterNot(AutomatiseringValidering::erAautomatiserbar)
            .map(AutomatiseringValidering::error)

    private fun validering(
        error: String,
        automatiserbar: () -> Boolean,
    ) = object : AutomatiseringValidering {
        override fun erAautomatiserbar() = automatiserbar()

        override fun error() = error
    }

    private fun erSpesialsakSomKanAutomatiseres(
        sykefraværstilfelle: Sykefraværstilfelle,
        utbetaling: Utbetaling,
        vedtaksperiodeId: UUID,
    ): Boolean {
        val erSpesialsak = vedtakDao.erSpesialsak(vedtaksperiodeId)
        val kanAutomatiseres = sykefraværstilfelle.spesialsakSomKanAutomatiseres(vedtaksperiodeId)
        val ingenUtbetaling = utbetaling.ingenUtbetaling()

        if (erSpesialsak) {
            sikkerLogg.info(
                "vedtaksperiode med {} er spesialsak, {}, {}, {}",
                kv("vedtaksperiodeId", vedtaksperiodeId),
                kv("kanAutomatiseres", kanAutomatiseres),
                kv("ingenUtbetaling", ingenUtbetaling),
                kv("blirAutomatiskGodkjent", kanAutomatiseres && ingenUtbetaling),
            )
        }

        return erSpesialsak && kanAutomatiseres && ingenUtbetaling
    }

    private class AutomatiserRevurderinger(
        private val utbetaling: Utbetaling,
        private val fødselsnummer: String,
        private val vedtaksperiodeId: UUID,
    ) : AutomatiseringValidering {
        override fun erAautomatiserbar() =
            !utbetaling.erRevurdering() ||
                (utbetaling.refusjonstype() != Refusjonstype.NEGATIVT_BELØP).also {
                    if (it) {
                        sikkerLogg.info(
                            "Revurdering av $vedtaksperiodeId (person $fødselsnummer) har ikke et negativt beløp, og er godkjent for automatisering",
                        )
                    }
                }

        override fun error() = "Utbetalingen er revurdering med negativt beløp"
    }

    fun erStikkprøve(
        vedtaksperiodeId: UUID,
        hendelseId: UUID,
    ) = automatiseringRepository.plukketUtTilStikkprøve(vedtaksperiodeId, hendelseId)
}

internal typealias PlukkTilManuell<String> = (String?) -> Boolean

internal interface Stikkprøver {
    fun utsFlereArbeidsgivereFørstegangsbehandling(): Boolean

    fun utsFlereArbeidsgivereForlengelse(): Boolean

    fun utsEnArbeidsgiverFørstegangsbehandling(): Boolean

    fun utsEnArbeidsgiverForlengelse(): Boolean

    fun fullRefusjonFlereArbeidsgivereFørstegangsbehandling(): Boolean

    fun fullRefusjonFlereArbeidsgivereForlengelse(): Boolean

    fun fullRefusjonEnArbeidsgiver(): Boolean
}

internal interface AutomatiseringValidering {
    fun erAautomatiserbar(): Boolean

    fun error(): String
}
