import com.fasterxml.jackson.databind.JsonNode
import io.mockk.every
import io.mockk.mockk
import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.AbstractDatabaseTest
import no.nav.helse.AvviksvurderingTestdata
import no.nav.helse.GodkjenningsbehovTestdata
import no.nav.helse.Meldingssender
import no.nav.helse.TestRapidHelpers.behov
import no.nav.helse.TestRapidHelpers.hendelser
import no.nav.helse.TestRapidHelpers.løsning
import no.nav.helse.TestRapidHelpers.løsningOrNull
import no.nav.helse.TestRapidHelpers.siste
import no.nav.helse.TestRapidHelpers.sisteBehov
import no.nav.helse.Testdata.snapshot
import no.nav.helse.januar
import no.nav.helse.mediator.meldinger.Risikofunn
import no.nav.helse.mediator.meldinger.Testmeldingfabrikk
import no.nav.helse.mediator.meldinger.Testmeldingfabrikk.VergemålJson.Fullmakt
import no.nav.helse.mediator.meldinger.Testmeldingfabrikk.VergemålJson.Vergemål
import no.nav.helse.modell.egenansatt.EgenAnsattDao
import no.nav.helse.modell.oppgave.Egenskap
import no.nav.helse.modell.person.PersonDao
import no.nav.helse.modell.saksbehandler.OverstyrtInntektOgRefusjonEvent.OverstyrtArbeidsgiverEvent.OverstyrtRefusjonselementEvent
import no.nav.helse.modell.utbetaling.Utbetalingsstatus
import no.nav.helse.modell.utbetaling.Utbetalingsstatus.FORKASTET
import no.nav.helse.modell.utbetaling.Utbetalingsstatus.IKKE_UTBETALT
import no.nav.helse.modell.utbetaling.Utbetalingsstatus.NY
import no.nav.helse.modell.utbetaling.Utbetalingsstatus.SENDT
import no.nav.helse.modell.utbetaling.Utbetalingsstatus.UTBETALT
import no.nav.helse.modell.varsel.Varselkode
import no.nav.helse.modell.vedtaksperiode.Generasjon
import no.nav.helse.modell.vedtaksperiode.Periode
import no.nav.helse.rapids_rivers.asLocalDateTime
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helse.spesialist.api.graphql.schema.ArbeidsforholdOverstyringHandling
import no.nav.helse.spesialist.api.graphql.schema.InntektOgRefusjonOverstyring
import no.nav.helse.spesialist.api.graphql.schema.Lovhjemmel
import no.nav.helse.spesialist.api.graphql.schema.OverstyringArbeidsforhold
import no.nav.helse.spesialist.api.graphql.schema.OverstyringArbeidsgiver
import no.nav.helse.spesialist.api.graphql.schema.OverstyringArbeidsgiver.OverstyringRefusjonselement
import no.nav.helse.spesialist.api.graphql.schema.OverstyringDag
import no.nav.helse.spesialist.api.graphql.schema.Skjonnsfastsettelse
import no.nav.helse.spesialist.api.graphql.schema.TidslinjeOverstyring
import no.nav.helse.spesialist.api.oppgave.Oppgavestatus
import no.nav.helse.spesialist.api.overstyring.Dagtype
import no.nav.helse.spesialist.api.overstyring.OverstyringType
import no.nav.helse.spesialist.api.person.Adressebeskyttelse
import no.nav.helse.spesialist.api.saksbehandler.SaksbehandlerFraApi
import no.nav.helse.spesialist.api.snapshot.SnapshotClient
import no.nav.helse.spesialist.test.TestPerson
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.fail
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

internal abstract class AbstractE2ETest : AbstractDatabaseTest() {
    protected val testperson = TestPerson().also { println("Bruker testdata: $it") }

    val FØDSELSNUMMER = testperson.fødselsnummer
    val ORGNR =
        with(testperson) {
            1.arbeidsgiver.organisasjonsnummer
        }
    val ORGNR2 =
        with(testperson) {
            2.arbeidsgiver.organisasjonsnummer
        }
    val AKTØR = testperson.aktørId
    val VEDTAKSPERIODE_ID = testperson.vedtaksperiodeId1
    val VEDTAKSPERIODE_ID_2 = testperson.vedtaksperiodeId2
    val UTBETALING_ID = testperson.utbetalingId1
    private val behandlinger = mutableMapOf<UUID, MutableList<UUID>>()
    protected val godkjenningsbehovTestdata get() =
        GodkjenningsbehovTestdata(
            fødselsnummer = FØDSELSNUMMER,
            aktørId = AKTØR,
            organisasjonsnummer = ORGNR,
            vedtaksperiodeId = VEDTAKSPERIODE_ID,
            utbetalingId = UTBETALING_ID,
            spleisBehandlingId = behandlinger.getValue(VEDTAKSPERIODE_ID).last(),
        )
    private val avviksvurderingTestdata = AvviksvurderingTestdata()
    internal lateinit var utbetalingId: UUID
        private set
    internal val snapshotClient = mockk<SnapshotClient>()
    private val testRapid = TestRapid()
    internal val inspektør get() = testRapid.inspektør
    private val meldingssender = Meldingssender(testRapid)
    protected lateinit var sisteMeldingId: UUID
    protected lateinit var sisteGodkjenningsbehovId: UUID
    private val testMediator = TestMediator(testRapid, dataSource)
    protected val SAKSBEHANDLER_OID: UUID = UUID.randomUUID()
    protected val SAKSBEHANDLER_EPOST = "augunn.saksbehandler@nav.no"
    protected val SAKSBEHANDLER_IDENT = "S199999"
    protected val SAKSBEHANDLER_NAVN = "Augunn Saksbehandler"
    private val saksbehandler =
        SaksbehandlerFraApi(
            oid = SAKSBEHANDLER_OID,
            navn = SAKSBEHANDLER_NAVN,
            epost = SAKSBEHANDLER_EPOST,
            ident = SAKSBEHANDLER_IDENT,
            grupper = emptyList(),
        )
    private val enhetsnummerOslo = "0301"

    @BeforeEach
    internal fun resetTestSetup() {
        resetTestRapid()
        lagVarseldefinisjoner()
        opprettSaksbehandler()
    }

    private fun resetTestRapid() = testRapid.reset()

    // Tanken er at denne ikke skal eksponeres ut av AbstractE2ETest, for å unngå at enkelttester implementer egen kode
    // som bør være felles
    protected val __ikke_bruk_denne get() = testRapid

    private fun opprettSaksbehandler() {
        sessionOf(dataSource).use {
            @Language("PostgreSQL")
            val query =
                """
                INSERT INTO saksbehandler
                VALUES (:oid, :navn, :epost, :ident)
                ON CONFLICT (oid) DO NOTHING
                """.trimIndent()
            it.run(
                queryOf(
                    query,
                    mapOf(
                        "oid" to SAKSBEHANDLER_OID,
                        "navn" to SAKSBEHANDLER_NAVN,
                        "epost" to SAKSBEHANDLER_EPOST,
                        "ident" to SAKSBEHANDLER_IDENT,
                    ),
                ).asUpdate,
            )
        }
    }

    protected fun Int.oppgave(vedtaksperiodeId: UUID): Long {
        require(this > 0) { "Forventet oppgaveId for vedtaksperiodeId=$vedtaksperiodeId må være større enn 0" }
        @Language("PostgreSQL")
        val query = "SELECT id FROM oppgave WHERE vedtak_ref = (SELECT id FROM vedtak WHERE vedtaksperiode_id = ?)"
        val oppgaveIder =
            sessionOf(dataSource).use { session ->
                session.run(queryOf(query, vedtaksperiodeId).map { it.long("id") }.asList)
            }
        assertTrue(oppgaveIder.size >= this) {
            "Forventer at det finnes minimum $this antall oppgaver for vedtaksperiodeId=$vedtaksperiodeId. Fant ${oppgaveIder.size} oppgaver."
        }
        return oppgaveIder[this - 1]
    }

    private fun nyUtbetalingId(utbetalingId: UUID) {
        this.utbetalingId = utbetalingId
    }

    protected fun spesialistInnvilgerAutomatisk(
        fom: LocalDate = 1.januar,
        tom: LocalDate = 31.januar,
        skjæringstidspunkt: LocalDate = fom,
        vedtaksperiodeId: UUID = testperson.vedtaksperiodeId1,
        avviksvurderingTestdata: AvviksvurderingTestdata = AvviksvurderingTestdata(skjæringstidspunkt = skjæringstidspunkt),
    ) {
        spesialistBehandlerGodkjenningsbehovFremTilRisikovurdering(
            avviksvurderingTestdata = avviksvurderingTestdata,
            godkjenningsbehovTestdata =
                godkjenningsbehovTestdata.copy(
                    periodeFom = fom,
                    periodeTom = tom,
                    skjæringstidspunkt = skjæringstidspunkt,
                ),
        )
        håndterRisikovurderingløsning(vedtaksperiodeId = vedtaksperiodeId)
        håndterUtbetalingUtbetalt()
        håndterAvsluttetMedVedtak(
            fom = fom,
            tom = tom,
            skjæringstidspunkt = skjæringstidspunkt,
            vedtaksperiodeId = vedtaksperiodeId,
            spleisBehandlingId =
                behandlinger[vedtaksperiodeId]?.last()
                    ?: throw IllegalArgumentException("Det finnes ingen behandlinger for vedtaksperiodeId=$vedtaksperiodeId"),
        )
        håndterVedtakFattet()
    }

    protected fun spesialistInnvilgerManuelt(
        regelverksvarsler: List<String> = emptyList(),
        fullmakter: List<Fullmakt> = emptyList(),
        risikofunn: List<Risikofunn> = emptyList(),
        harOppdatertMetadata: Boolean = false,
        godkjenningsbehovTestdata: GodkjenningsbehovTestdata = this.godkjenningsbehovTestdata,
    ) {
        spesialistBehandlerGodkjenningsbehovFremTilOppgave(
            regelverksvarsler = regelverksvarsler,
            fullmakter = fullmakter,
            risikofunn = risikofunn,
            harOppdatertMetadata = harOppdatertMetadata,
            godkjenningsbehovTestdata = godkjenningsbehovTestdata,
        )
        håndterSaksbehandlerløsning(vedtaksperiodeId = godkjenningsbehovTestdata.vedtaksperiodeId)
        håndterUtbetalingUtbetalt()
        håndterAvsluttetMedVedtak()
        håndterVedtakFattet(vedtaksperiodeId = godkjenningsbehovTestdata.vedtaksperiodeId)
    }

    protected fun spesialistBehandlerGodkjenningsbehovFremTilVergemål(
        regelverksvarsler: List<String> = emptyList(),
        harOppdatertMetadata: Boolean = false,
        enhet: String = enhetsnummerOslo,
        arbeidsgiverbeløp: Int = 20000,
        personbeløp: Int = 0,
        avviksvurderingTestdata: AvviksvurderingTestdata = this.avviksvurderingTestdata,
        godkjenningsbehovTestdata: GodkjenningsbehovTestdata = this.godkjenningsbehovTestdata,
    ) {
        spesialistBehandlerGodkjenningsbehovTilOgMedUtbetalingsfilter(
            regelverksvarsler,
            harOppdatertMetadata = harOppdatertMetadata,
            enhet = enhet,
            arbeidsgiverbeløp = arbeidsgiverbeløp,
            personbeløp = personbeløp,
            avviksvurderingTestdata = avviksvurderingTestdata,
            godkjenningsbehovTestdata = godkjenningsbehovTestdata,
        )
        if (!harOppdatertMetadata) håndterEgenansattløsning()
    }

    protected fun spesialistBehandlerGodkjenningsbehovFremTilÅpneOppgaver(
        regelverksvarsler: List<String> = emptyList(),
        fullmakter: List<Fullmakt> = emptyList(),
        harOppdatertMetadata: Boolean = false,
        enhet: String = enhetsnummerOslo,
        arbeidsgiverbeløp: Int = 20000,
        personbeløp: Int = 0,
        avviksvurderingTestdata: AvviksvurderingTestdata = this.avviksvurderingTestdata,
        godkjenningsbehovTestdata: GodkjenningsbehovTestdata = this.godkjenningsbehovTestdata,
    ) {
        spesialistBehandlerGodkjenningsbehovFremTilVergemål(
            regelverksvarsler,
            harOppdatertMetadata,
            enhet,
            arbeidsgiverbeløp = arbeidsgiverbeløp,
            personbeløp = personbeløp,
            avviksvurderingTestdata = avviksvurderingTestdata,
            godkjenningsbehovTestdata = godkjenningsbehovTestdata,
        )
        håndterVergemålOgFullmaktløsning(fullmakter = fullmakter)
    }

    protected fun spesialistBehandlerGodkjenningsbehovTilOgMedUtbetalingsfilter(
        regelverksvarsler: List<String> = emptyList(),
        harOppdatertMetadata: Boolean = false,
        enhet: String = enhetsnummerOslo,
        arbeidsgiverbeløp: Int = 20000,
        personbeløp: Int = 0,
        avviksvurderingTestdata: AvviksvurderingTestdata = this.avviksvurderingTestdata,
        godkjenningsbehovTestdata: GodkjenningsbehovTestdata = this.godkjenningsbehovTestdata,
    ) {
        if (regelverksvarsler.isNotEmpty()) håndterAktivitetsloggNyAktivitet(varselkoder = regelverksvarsler)
        håndterGodkjenningsbehov(
            harOppdatertMetainfo = harOppdatertMetadata,
            arbeidsgiverbeløp = arbeidsgiverbeløp,
            personbeløp = personbeløp,
            avviksvurderingTestdata = avviksvurderingTestdata,
            godkjenningsbehovTestdata = godkjenningsbehovTestdata,
        )
        if (!harOppdatertMetadata) {
            håndterPersoninfoløsning()
            håndterEnhetløsning(vedtaksperiodeId = godkjenningsbehovTestdata.vedtaksperiodeId, enhet = enhet)
            håndterInfotrygdutbetalingerløsning(vedtaksperiodeId = godkjenningsbehovTestdata.vedtaksperiodeId)
            håndterArbeidsgiverinformasjonløsning(
                vedtaksperiodeId = godkjenningsbehovTestdata.vedtaksperiodeId,
            )
            håndterArbeidsforholdløsning(vedtaksperiodeId = godkjenningsbehovTestdata.vedtaksperiodeId)
        }
    }

    private fun spinnvillAvviksvurderer(
        avviksvurderingTestdata: AvviksvurderingTestdata,
        fødselsnummer: String,
        aktørId: String,
        organisasjonsnummer: String,
    ) {
        sisteMeldingId =
            meldingssender.sendAvvikVurdert(avviksvurderingTestdata, fødselsnummer, aktørId, organisasjonsnummer)
    }

    protected fun spesialistBehandlerGodkjenningsbehovFremTilOppgave(
        enhet: String = enhetsnummerOslo,
        regelverksvarsler: List<String> = emptyList(),
        fullmakter: List<Fullmakt> = emptyList(),
        risikofunn: List<Risikofunn> = emptyList(),
        harRisikovurdering: Boolean = false,
        harOppdatertMetadata: Boolean = false,
        kanGodkjennesAutomatisk: Boolean = false,
        arbeidsgiverbeløp: Int = 20000,
        personbeløp: Int = 0,
        avviksvurderingTestdata: AvviksvurderingTestdata = this.avviksvurderingTestdata,
        godkjenningsbehovTestdata: GodkjenningsbehovTestdata = this.godkjenningsbehovTestdata,
    ) {
        spesialistBehandlerGodkjenningsbehovFremTilRisikovurdering(
            enhet = enhet,
            regelverksvarsler = regelverksvarsler,
            fullmakter = fullmakter,
            harOppdatertMetadata = harOppdatertMetadata,
            arbeidsgiverbeløp = arbeidsgiverbeløp,
            personbeløp = personbeløp,
            avviksvurderingTestdata = avviksvurderingTestdata,
            godkjenningsbehovTestdata = godkjenningsbehovTestdata,
        )
        if (!harRisikovurdering) {
            håndterRisikovurderingløsning(
                kanGodkjennesAutomatisk = kanGodkjennesAutomatisk,
                risikofunn = risikofunn,
                vedtaksperiodeId = godkjenningsbehovTestdata.vedtaksperiodeId,
            )
        }
        if (!erFerdigstilt(sisteGodkjenningsbehovId)) håndterInntektløsning()
    }

    private fun spesialistBehandlerGodkjenningsbehovFremTilRisikovurdering(
        enhet: String = enhetsnummerOslo,
        regelverksvarsler: List<String> = emptyList(),
        fullmakter: List<Fullmakt> = emptyList(),
        harOppdatertMetadata: Boolean = false,
        arbeidsgiverbeløp: Int = 20000,
        personbeløp: Int = 0,
        avviksvurderingTestdata: AvviksvurderingTestdata = this.avviksvurderingTestdata,
        godkjenningsbehovTestdata: GodkjenningsbehovTestdata = this.godkjenningsbehovTestdata,
    ) {
        spesialistBehandlerGodkjenningsbehovFremTilÅpneOppgaver(
            regelverksvarsler,
            fullmakter,
            harOppdatertMetadata = harOppdatertMetadata,
            enhet = enhet,
            arbeidsgiverbeløp = arbeidsgiverbeløp,
            personbeløp = personbeløp,
            avviksvurderingTestdata = avviksvurderingTestdata,
            godkjenningsbehovTestdata = godkjenningsbehovTestdata,
        )
        håndterÅpneOppgaverløsning()
    }

    protected fun vedtaksløsningenMottarNySøknad(
        aktørId: String = AKTØR,
        fødselsnummer: String = FØDSELSNUMMER,
        organisasjonsnummer: String = ORGNR,
    ) {
        sisteMeldingId = meldingssender.sendSøknadSendt(aktørId, fødselsnummer, organisasjonsnummer)
        assertIngenEtterspurteBehov()
        assertPersonEksisterer(fødselsnummer, aktørId)
        assertArbeidsgiverEksisterer(organisasjonsnummer)
    }

    protected fun spleisOppretterNyBehandling(
        aktørId: String = AKTØR,
        fødselsnummer: String = FØDSELSNUMMER,
        organisasjonsnummer: String = ORGNR,
        vedtaksperiodeId: UUID = testperson.vedtaksperiodeId1,
        fom: LocalDate = 1.januar,
        tom: LocalDate = 31.januar,
        spleisBehandlingId: UUID = UUID.randomUUID(),
    ) {
        behandlinger.getOrPut(vedtaksperiodeId) { mutableListOf() }.addLast(spleisBehandlingId)
        sisteMeldingId =
            meldingssender.sendBehandlingOpprettet(
                aktørId = aktørId,
                fødselsnummer = fødselsnummer,
                organisasjonsnummer = organisasjonsnummer,
                vedtaksperiodeId = vedtaksperiodeId,
                fom = fom,
                tom = tom,
                spleisBehandlingId = spleisBehandlingId,
            )
        assertIngenEtterspurteBehov()
        assertVedtaksperiodeEksisterer(vedtaksperiodeId)
    }

    protected fun håndterVedtaksperiodeEndret(
        aktørId: String = AKTØR,
        fødselsnummer: String = FØDSELSNUMMER,
        organisasjonsnummer: String = ORGNR,
        vedtaksperiodeId: UUID = testperson.vedtaksperiodeId1,
        forårsaketAvId: UUID = UUID.randomUUID(),
        forrigeTilstand: String? = null,
        gjeldendeTilstand: String? = null,
    ) {
        val erRevurdering = erRevurdering(vedtaksperiodeId)
        sisteMeldingId =
            meldingssender.sendVedtaksperiodeEndret(
                aktørId = aktørId,
                fødselsnummer = fødselsnummer,
                organisasjonsnummer = organisasjonsnummer,
                vedtaksperiodeId = vedtaksperiodeId,
                forrigeTilstand =
                    forrigeTilstand
                        ?: if (erRevurdering) "AVVENTER_SIMULERING_REVURDERING" else "AVVENTER_SIMULERING",
                gjeldendeTilstand =
                    gjeldendeTilstand
                        ?: if (erRevurdering) "AVVENTER_GODKJENNING_REVURDERING" else "AVVENTER_GODKJENNING",
                forårsaketAvId = forårsaketAvId,
            )
    }

    protected fun håndterVedtaksperiodeReberegnet(
        aktørId: String = AKTØR,
        fødselsnummer: String = FØDSELSNUMMER,
        organisasjonsnummer: String = ORGNR,
        vedtaksperiodeId: UUID = testperson.vedtaksperiodeId1,
        forårsaketAvId: UUID = UUID.randomUUID(),
    ) {
        val erRevurdering = erRevurdering(vedtaksperiodeId)
        sisteMeldingId =
            meldingssender.sendVedtaksperiodeEndret(
                aktørId = aktørId,
                fødselsnummer = fødselsnummer,
                organisasjonsnummer = organisasjonsnummer,
                vedtaksperiodeId = vedtaksperiodeId,
                forrigeTilstand = if (erRevurdering) "AVVENTER_GODKJENNING_REVURDERING" else "AVVENTER_GODKJENNING",
                gjeldendeTilstand = if (erRevurdering) "AVVENTER_HISTORIKK_REVURDERING" else "AVVENTER_HISTORIKK",
                forårsaketAvId = forårsaketAvId,
            )
        assertIngenEtterspurteBehov()
    }

    protected fun håndterVedtaksperiodeForkastet(
        aktørId: String = AKTØR,
        fødselsnummer: String = FØDSELSNUMMER,
        organisasjonsnummer: String = ORGNR,
        vedtaksperiodeId: UUID = testperson.vedtaksperiodeId1,
    ) {
        sisteMeldingId =
            meldingssender.sendVedtaksperiodeForkastet(
                aktørId = aktørId,
                fødselsnummer = fødselsnummer,
                organisasjonsnummer = organisasjonsnummer,
                vedtaksperiodeId = vedtaksperiodeId,
            )
        assertIngenEtterspurteBehov()
    }

    protected fun håndterVedtaksperiodeNyUtbetaling(
        aktørId: String = AKTØR,
        fødselsnummer: String = FØDSELSNUMMER,
        organisasjonsnummer: String = ORGNR,
        vedtaksperiodeId: UUID = testperson.vedtaksperiodeId1,
        utbetalingId: UUID = testperson.utbetalingId1,
    ) {
        if (!this::utbetalingId.isInitialized || utbetalingId != this.utbetalingId) nyUtbetalingId(utbetalingId)
        sisteMeldingId =
            meldingssender.sendVedtaksperiodeNyUtbetaling(
                aktørId = aktørId,
                fødselsnummer = fødselsnummer,
                organisasjonsnummer = organisasjonsnummer,
                vedtaksperiodeId = vedtaksperiodeId,
                utbetalingId = this.utbetalingId,
            )
        assertIngenEtterspurteBehov()
    }

    protected fun håndterAktivitetsloggNyAktivitet(
        aktørId: String = AKTØR,
        fødselsnummer: String = FØDSELSNUMMER,
        organisasjonsnummer: String = ORGNR,
        vedtaksperiodeId: UUID = testperson.vedtaksperiodeId1,
        varselkoder: List<String> = emptyList(),
    ) {
        varselkoder.forEach {
            lagVarseldefinisjon(it)
        }
        sisteMeldingId =
            meldingssender.sendAktivitetsloggNyAktivitet(
                aktørId = aktørId,
                fødselsnummer = fødselsnummer,
                organisasjonsnummer = organisasjonsnummer,
                vedtaksperiodeId = vedtaksperiodeId,
                varselkoder = varselkoder,
            )
        assertIngenEtterspurteBehov()
    }

    protected fun håndterEndretSkjermetinfo(
        fødselsnummer: String = FØDSELSNUMMER,
        skjermet: Boolean,
    ) {
        sisteMeldingId = meldingssender.sendEndretSkjermetinfo(fødselsnummer, skjermet)
        if (!skjermet) {
            assertIngenEtterspurteBehov()
        }
    }

    protected fun håndterGosysOppgaveEndret(fødselsnummer: String = FØDSELSNUMMER) {
        sisteMeldingId = meldingssender.sendGosysOppgaveEndret(fødselsnummer)
    }

    protected fun håndterTilbakedateringBehandlet(
        fødselsnummer: String = FØDSELSNUMMER,
        perioder: List<Periode>,
    ) {
        sisteMeldingId = meldingssender.sendTilbakedateringBehandlet(fødselsnummer, perioder)
    }

    protected fun håndterKommandokjedePåminnelse(
        commandContextId: UUID,
        meldingId: UUID,
    ) {
        sisteMeldingId = meldingssender.sendKommandokjedePåminnelse(commandContextId, meldingId)
    }

    protected fun håndterUtbetalingOpprettet(
        aktørId: String = AKTØR,
        fødselsnummer: String = FØDSELSNUMMER,
        organisasjonsnummer: String = ORGNR,
        utbetalingtype: String = "UTBETALING",
        arbeidsgiverbeløp: Int = 20000,
        personbeløp: Int = 0,
        utbetalingId: UUID = testperson.utbetalingId1,
    ) {
        nyUtbetalingId(utbetalingId)
        håndterUtbetalingEndret(
            aktørId,
            fødselsnummer,
            organisasjonsnummer,
            utbetalingtype,
            arbeidsgiverbeløp,
            personbeløp,
            utbetalingId = this.utbetalingId,
        )
        assertIngenEtterspurteBehov()
    }

    protected fun håndterUtbetalingErstattet(
        aktørId: String = AKTØR,
        fødselsnummer: String = FØDSELSNUMMER,
        organisasjonsnummer: String = ORGNR,
        utbetalingtype: String = "UTBETALING",
        arbeidsgiverbeløp: Int = 20000,
        personbeløp: Int = 0,
        utbetalingId: UUID,
    ) {
        håndterUtbetalingForkastet(aktørId, fødselsnummer, organisasjonsnummer)
        håndterUtbetalingEndret(
            aktørId,
            fødselsnummer,
            organisasjonsnummer,
            utbetalingtype,
            arbeidsgiverbeløp,
            personbeløp,
            utbetalingId = utbetalingId,
        )
        assertIngenEtterspurteBehov()
    }

    protected fun håndterUtbetalingEndret(
        aktørId: String = AKTØR,
        fødselsnummer: String = FØDSELSNUMMER,
        organisasjonsnummer: String = ORGNR,
        utbetalingtype: String = "UTBETALING",
        arbeidsgiverbeløp: Int = 20000,
        personbeløp: Int = 0,
        forrigeStatus: Utbetalingsstatus = NY,
        gjeldendeStatus: Utbetalingsstatus = IKKE_UTBETALT,
        opprettet: LocalDateTime = LocalDateTime.now(),
        utbetalingId: UUID = this.utbetalingId,
    ) {
        nyUtbetalingId(utbetalingId)
        sisteMeldingId =
            meldingssender.sendUtbetalingEndret(
                aktørId = aktørId,
                fødselsnummer = fødselsnummer,
                organisasjonsnummer = organisasjonsnummer,
                utbetalingId = this.utbetalingId,
                type = utbetalingtype,
                arbeidsgiverbeløp = arbeidsgiverbeløp,
                personbeløp = personbeløp,
                forrigeStatus = forrigeStatus,
                gjeldendeStatus = gjeldendeStatus,
                opprettet = opprettet,
            )
    }

    protected fun håndterUtbetalingForkastet(
        aktørId: String = AKTØR,
        fødselsnummer: String = FØDSELSNUMMER,
        organisasjonsnummer: String = ORGNR,
        forrigeStatus: Utbetalingsstatus = NY,
    ) {
        håndterUtbetalingEndret(
            aktørId,
            fødselsnummer,
            organisasjonsnummer,
            forrigeStatus = forrigeStatus,
            gjeldendeStatus = FORKASTET,
            utbetalingId = this.utbetalingId,
        )
        assertIngenEtterspurteBehov()
    }

    protected fun håndterUtbetalingUtbetalt(
        aktørId: String = AKTØR,
        fødselsnummer: String = FØDSELSNUMMER,
        organisasjonsnummer: String = ORGNR,
    ) {
        håndterUtbetalingEndret(
            aktørId,
            fødselsnummer,
            organisasjonsnummer,
            forrigeStatus = SENDT,
            gjeldendeStatus = UTBETALT,
            utbetalingId = this.utbetalingId,
        )
        assertIngenEtterspurteBehov()
    }

    protected fun håndterUtbetalingAnnullert(
        fødselsnummer: String = FØDSELSNUMMER,
        saksbehandler_epost: String,
    ) {
        @Suppress("SqlResolve")
        fun fagsystemidFor(
            utbetalingId: UUID,
            tilArbeidsgiver: Boolean,
        ): String {
            val fagsystemidtype = if (tilArbeidsgiver) "arbeidsgiver" else "person"
            return sessionOf(dataSource).use { session ->
                @Language("PostgreSQL")
                val query =
                    "SELECT fagsystem_id FROM utbetaling_id ui INNER JOIN oppdrag o on o.id = ui.${fagsystemidtype}_fagsystem_id_ref WHERE ui.utbetaling_id = ?"
                requireNotNull(session.run(queryOf(query, utbetalingId).map { it.string("fagsystem_id") }.asSingle)) {
                    "Forventet å finne med ${fagsystemidtype}FagsystemId for utbetalingId=$utbetalingId"
                }
            }
        }

        sisteMeldingId =
            meldingssender.sendUtbetalingAnnullert(
                fødselsnummer = fødselsnummer,
                utbetalingId = utbetalingId,
                epost = saksbehandler_epost,
                arbeidsgiverFagsystemId = fagsystemidFor(utbetalingId, tilArbeidsgiver = true),
                personFagsystemId = fagsystemidFor(utbetalingId, tilArbeidsgiver = false),
            )
        assertIngenEtterspurteBehov()
    }

    protected fun håndterGodkjenningsbehovUtenValidering(
        arbeidsgiverbeløp: Int = 20000,
        personbeløp: Int = 0,
        avviksvurderingTestdata: AvviksvurderingTestdata = this.avviksvurderingTestdata,
        godkjenningsbehovTestdata: GodkjenningsbehovTestdata = this.godkjenningsbehovTestdata,
    ) {
        val erRevurdering = erRevurdering(godkjenningsbehovTestdata.vedtaksperiodeId)
        håndterVedtaksperiodeNyUtbetaling(
            vedtaksperiodeId = godkjenningsbehovTestdata.vedtaksperiodeId,
            utbetalingId = godkjenningsbehovTestdata.utbetalingId,
        )
        håndterUtbetalingOpprettet(
            utbetalingtype = if (erRevurdering) "REVURDERING" else "UTBETALING",
            utbetalingId = godkjenningsbehovTestdata.utbetalingId,
            arbeidsgiverbeløp = arbeidsgiverbeløp,
            personbeløp = personbeløp,
        )
        håndterVedtaksperiodeEndret(vedtaksperiodeId = godkjenningsbehovTestdata.vedtaksperiodeId)
        spinnvillAvviksvurderer(
            avviksvurderingTestdata,
            godkjenningsbehovTestdata.fødselsnummer,
            godkjenningsbehovTestdata.aktørId,
            godkjenningsbehovTestdata.organisasjonsnummer,
        )
        sisteMeldingId =
            sendGodkjenningsbehov(
                godkjenningsbehovTestdata.copy(avviksvurderingId = avviksvurderingTestdata.avviksvurderingId),
            )
        sisteGodkjenningsbehovId = sisteMeldingId
    }

    protected fun håndterGodkjenningsbehov(
        harOppdatertMetainfo: Boolean = false,
        arbeidsgiverbeløp: Int = 20000,
        personbeløp: Int = 0,
        avviksvurderingTestdata: AvviksvurderingTestdata = this.avviksvurderingTestdata,
        godkjenningsbehovTestdata: GodkjenningsbehovTestdata = this.godkjenningsbehovTestdata,
    ) {
        val alleArbeidsforhold =
            sessionOf(dataSource).use { session ->
                @Language("PostgreSQL")
                val query = "SELECT a.orgnummer FROM arbeidsgiver a"
                session.run(queryOf(query).map { it.string("orgnummer") }.asList)
            }
        håndterGodkjenningsbehovUtenValidering(
            arbeidsgiverbeløp = arbeidsgiverbeløp,
            personbeløp = personbeløp,
            avviksvurderingTestdata = avviksvurderingTestdata,
            godkjenningsbehovTestdata = godkjenningsbehovTestdata.copy(avviksvurderingId = avviksvurderingTestdata.avviksvurderingId),
        )

        when {
            !harOppdatertMetainfo -> assertEtterspurteBehov("HentPersoninfoV2")
            !godkjenningsbehovTestdata.orgnummereMedRelevanteArbeidsforhold.all {
                it in alleArbeidsforhold
            } -> assertEtterspurteBehov("Arbeidsgiverinformasjon")
            else -> assertEtterspurteBehov("Vergemål", "Fullmakt")
        }
    }

    private fun sendGodkjenningsbehov(godkjenningsbehovTestdata: GodkjenningsbehovTestdata) =
        meldingssender.sendGodkjenningsbehov(godkjenningsbehovTestdata).also { sisteMeldingId = it }

    protected fun håndterPersoninfoløsning(
        aktørId: String = AKTØR,
        fødselsnummer: String = FØDSELSNUMMER,
        adressebeskyttelse: Adressebeskyttelse = Adressebeskyttelse.Ugradert,
    ) {
        assertEtterspurteBehov("HentPersoninfoV2")
        sisteMeldingId =
            meldingssender.sendPersoninfoløsning(
                aktørId,
                fødselsnummer,
                adressebeskyttelse,
            )
    }

    protected fun håndterPersoninfoløsningUtenValidering(
        aktørId: String = AKTØR,
        fødselsnummer: String = FØDSELSNUMMER,
        adressebeskyttelse: Adressebeskyttelse = Adressebeskyttelse.Ugradert,
    ) {
        sisteMeldingId =
            meldingssender.sendPersoninfoløsning(
                aktørId,
                fødselsnummer,
                adressebeskyttelse,
            )
    }

    protected fun håndterEnhetløsning(
        aktørId: String = AKTØR,
        fødselsnummer: String = FØDSELSNUMMER,
        organisasjonsnummer: String = ORGNR,
        vedtaksperiodeId: UUID = testperson.vedtaksperiodeId1,
        enhet: String = "0301", // Oslo
    ) {
        assertEtterspurteBehov("HentEnhet")
        sisteMeldingId =
            meldingssender.sendEnhetløsning(aktørId, fødselsnummer, organisasjonsnummer, vedtaksperiodeId, enhet)
    }

    protected fun håndterInfotrygdutbetalingerløsning(
        aktørId: String = AKTØR,
        fødselsnummer: String = FØDSELSNUMMER,
        organisasjonsnummer: String = ORGNR,
        vedtaksperiodeId: UUID = testperson.vedtaksperiodeId1,
    ) {
        assertEtterspurteBehov("HentInfotrygdutbetalinger")
        sisteMeldingId =
            meldingssender.sendInfotrygdutbetalingerløsning(
                aktørId,
                fødselsnummer,
                organisasjonsnummer,
                vedtaksperiodeId,
            )
    }

    protected fun håndterArbeidsgiverinformasjonløsning(
        aktørId: String = AKTØR,
        fødselsnummer: String = FØDSELSNUMMER,
        organisasjonsnummer: String = ORGNR,
        vedtaksperiodeId: UUID = testperson.vedtaksperiodeId1,
        arbeidsgiverinformasjonJson: List<Testmeldingfabrikk.ArbeidsgiverinformasjonJson>? = null,
    ) {
        val erKompositt = testRapid.inspektør.sisteBehov("Arbeidsgiverinformasjon", "HentPersoninfoV2") != null
        if (erKompositt) {
            assertEtterspurteBehov("Arbeidsgiverinformasjon", "HentPersoninfoV2")
            sisteMeldingId =
                meldingssender.sendArbeidsgiverinformasjonKompositt(
                    aktørId,
                    fødselsnummer,
                    organisasjonsnummer,
                    vedtaksperiodeId,
                )
            return
        }
        assertEtterspurteBehov("Arbeidsgiverinformasjon")
        sisteMeldingId =
            meldingssender.sendArbeidsgiverinformasjonløsning(
                aktørId,
                fødselsnummer,
                organisasjonsnummer,
                vedtaksperiodeId,
                arbeidsgiverinformasjonJson,
            )
    }

    protected fun håndterArbeidsforholdløsning(
        aktørId: String = AKTØR,
        fødselsnummer: String = FØDSELSNUMMER,
        organisasjonsnummer: String = ORGNR,
        vedtaksperiodeId: UUID = testperson.vedtaksperiodeId1,
    ) {
        assertEtterspurteBehov("Arbeidsforhold")
        sisteMeldingId =
            meldingssender.sendArbeidsforholdløsning(aktørId, fødselsnummer, organisasjonsnummer, vedtaksperiodeId)
    }

    protected fun håndterEgenansattløsning(
        aktørId: String = AKTØR,
        fødselsnummer: String = FØDSELSNUMMER,
        erEgenAnsatt: Boolean = false,
    ) {
        assertEtterspurteBehov("EgenAnsatt")
        sisteMeldingId = meldingssender.sendEgenAnsattløsning(aktørId, fødselsnummer, erEgenAnsatt)
    }

    protected fun håndterVergemålOgFullmaktløsning(
        aktørId: String = AKTØR,
        fødselsnummer: String = FØDSELSNUMMER,
        vergemål: List<Vergemål> = emptyList(),
        fremtidsfullmakter: List<Vergemål> = emptyList(),
        fullmakter: List<Fullmakt> = emptyList(),
    ) {
        assertEtterspurteBehov("Vergemål", "Fullmakt")
        sisteMeldingId =
            meldingssender.sendVergemålOgFullmaktløsning(
                aktørId = aktørId,
                fødselsnummer = fødselsnummer,
                vergemål = vergemål,
                fremtidsfullmakter = fremtidsfullmakter,
                fullmakter = fullmakter,
            )
    }

    protected fun håndterInntektløsning(
        aktørId: String = AKTØR,
        fødselsnummer: String = FØDSELSNUMMER,
    ) {
        assertEtterspurteBehov("InntekterForSykepengegrunnlag")
        sisteMeldingId = meldingssender.sendInntektløsning(aktørId, fødselsnummer, ORGNR)
    }

    protected fun håndterÅpneOppgaverløsning(
        aktørId: String = AKTØR,
        fødselsnummer: String = FØDSELSNUMMER,
        antallÅpneOppgaverIGosys: Int = 0,
        oppslagFeilet: Boolean = false,
    ) {
        assertEtterspurteBehov("ÅpneOppgaver")
        sisteMeldingId = meldingssender.sendÅpneGosysOppgaverløsning(aktørId, fødselsnummer, antallÅpneOppgaverIGosys, oppslagFeilet)
    }

    protected fun håndterRisikovurderingløsning(
        aktørId: String = AKTØR,
        fødselsnummer: String = FØDSELSNUMMER,
        organisasjonsnummer: String = ORGNR,
        vedtaksperiodeId: UUID = testperson.vedtaksperiodeId1,
        kanGodkjennesAutomatisk: Boolean = true,
        risikofunn: List<Risikofunn> = emptyList(),
    ) {
        assertEtterspurteBehov("Risikovurdering")
        sisteMeldingId =
            meldingssender.sendRisikovurderingløsning(
                aktørId,
                fødselsnummer,
                organisasjonsnummer,
                vedtaksperiodeId,
                kanGodkjennesAutomatisk,
                risikofunn,
            )
    }

    protected fun saksbehandlerVurdererVarsel(
        vedtaksperiodeId: UUID,
        varselkode: String,
        saksbehandlerIdent: String = SAKSBEHANDLER_IDENT,
    ) {
        sessionOf(dataSource).use { session ->
            @Language("PostgreSQL")
            val query =
                """
                UPDATE selve_varsel 
                SET status = 'VURDERT', status_endret_ident = :ident, status_endret_tidspunkt = now()
                WHERE vedtaksperiode_id = :vedtaksperiodeId
                    AND kode = :varselkode
                """.trimIndent()
            session.run(
                queryOf(
                    query,
                    mapOf(
                        "vedtaksperiodeId" to vedtaksperiodeId,
                        "varselkode" to varselkode,
                        "ident" to saksbehandlerIdent,
                    ),
                ).asUpdate,
            )
        }
    }

    protected fun håndterSaksbehandlerløsning(
        fødselsnummer: String = FØDSELSNUMMER,
        vedtaksperiodeId: UUID = testperson.vedtaksperiodeId1,
        godkjent: Boolean = true,
        kommentar: String? = null,
        begrunnelser: List<String> = emptyList(),
    ) {
        fun oppgaveIdFor(vedtaksperiodeId: UUID): Long =
            sessionOf(dataSource).use { session ->
                @Language("PostgreSQL")
                val query =
                    "SELECT id FROM oppgave WHERE vedtak_ref = (SELECT id FROM vedtak WHERE vedtaksperiode_id = ?) ORDER BY id DESC LIMIT 1;"
                requireNotNull(session.run(queryOf(query, vedtaksperiodeId).map { it.long(1) }.asSingle))
            }

        fun godkjenningsbehovIdFor(vedtaksperiodeId: UUID): UUID =
            sessionOf(dataSource).use { session ->
                @Language("PostgreSQL")
                val query =
                    "SELECT id FROM hendelse h INNER JOIN vedtaksperiode_hendelse vh on h.id = vh.hendelse_ref WHERE vh.vedtaksperiode_id = ? AND h.type = 'GODKJENNING';"
                requireNotNull(session.run(queryOf(query, vedtaksperiodeId).map { it.uuid("id") }.asSingle))
            }

        fun settOppgaveIAvventerSystem(oppgaveId: Long) {
            @Language("PostgreSQL")
            val query = "UPDATE oppgave SET status = 'AvventerSystem' WHERE id = ?"
            sessionOf(dataSource).use {
                it.run(queryOf(query, oppgaveId).asUpdate)
            }
        }

        val oppgaveId = oppgaveIdFor(vedtaksperiodeId)
        val godkjenningsbehovId = godkjenningsbehovIdFor(vedtaksperiodeId)
        settOppgaveIAvventerSystem(oppgaveId)
        sisteMeldingId =
            meldingssender.sendSaksbehandlerløsning(
                fødselsnummer,
                oppgaveId = oppgaveId,
                godkjenningsbehovId = godkjenningsbehovId,
                godkjent = godkjent,
                begrunnelser = begrunnelser,
                kommentar = kommentar,
            )
        if (godkjent) {
            assertUtgåendeMelding("vedtaksperiode_godkjent")
        } else {
            assertUtgåendeMelding("vedtaksperiode_avvist")
        }
        assertUtgåendeBehovløsning("Godkjenning")
    }

    protected fun håndterAvsluttetMedVedtak(
        aktørId: String = AKTØR,
        fødselsnummer: String = FØDSELSNUMMER,
        organisasjonsnummer: String = ORGNR,
        vedtaksperiodeId: UUID = testperson.vedtaksperiodeId1,
        spleisBehandlingId: UUID = behandlinger.getValue(vedtaksperiodeId).last(),
        fastsattType: String = "EtterHovedregel",
        fom: LocalDate = 1.januar,
        tom: LocalDate = 31.januar,
        skjæringstidspunkt: LocalDate = fom,
        settInnAvviksvurderingFraSpleis: Boolean = true,
    ) {
        val utbetalingId = if (this::utbetalingId.isInitialized) this.utbetalingId else null
        sisteMeldingId =
            meldingssender.sendAvsluttetMedVedtak(
                aktørId = aktørId,
                fødselsnummer = fødselsnummer,
                organisasjonsnummer = organisasjonsnummer,
                vedtaksperiodeId = vedtaksperiodeId,
                spleisBehandlingId = spleisBehandlingId,
                utbetalingId = utbetalingId,
                fom = fom,
                tom = tom,
                skjæringstidspunkt = skjæringstidspunkt,
                fastsattType = fastsattType,
                settInnAvviksvurderingFraSpleis = settInnAvviksvurderingFraSpleis,
            )
    }

    protected fun håndterAvsluttetUtenVedtak(
        aktørId: String = AKTØR,
        fødselsnummer: String = FØDSELSNUMMER,
        organisasjonsnummer: String = ORGNR,
        vedtaksperiodeId: UUID = testperson.vedtaksperiodeId1,
        spleisBehandlingId: UUID = UUID.randomUUID(),
        fom: LocalDate = 1.januar,
        tom: LocalDate = 11.januar,
        skjæringstidspunkt: LocalDate = fom,
    ) {
        sisteMeldingId =
            meldingssender.sendAvsluttetUtenVedtak(
                aktørId = aktørId,
                fødselsnummer = fødselsnummer,
                organisasjonsnummer = organisasjonsnummer,
                vedtaksperiodeId = vedtaksperiodeId,
                spleisBehandlingId = spleisBehandlingId,
                fom = fom,
                tom = tom,
                skjæringstidspunkt = skjæringstidspunkt,
            )
    }

    protected fun håndterVedtakFattet(
        aktørId: String = AKTØR,
        fødselsnummer: String = FØDSELSNUMMER,
        organisasjonsnummer: String = ORGNR,
        vedtaksperiodeId: UUID = testperson.vedtaksperiodeId1,
        spleisBehandlingId: UUID = UUID.randomUUID(),
    ) {
        if (this::utbetalingId.isInitialized) håndterUtbetalingUtbetalt(aktørId, fødselsnummer, organisasjonsnummer)
        sisteMeldingId = meldingssender.sendVedtakFattet(aktørId, fødselsnummer, organisasjonsnummer, vedtaksperiodeId, spleisBehandlingId)
    }

    protected fun håndterAdressebeskyttelseEndret(
        aktørId: String = AKTØR,
        fødselsnummer: String = FØDSELSNUMMER,
        harOppdatertMetadata: Boolean = true,
    ) {
        if (!harOppdatertMetadata) assertEtterspurteBehov("HentPersoninfoV2")
        sisteMeldingId = meldingssender.sendAdressebeskyttelseEndret(aktørId, fødselsnummer)
    }

    protected fun håndterOppdaterPersondata(
        aktørId: String = AKTØR,
        fødselsnummer: String = FØDSELSNUMMER,
    ) {
        sisteMeldingId = meldingssender.sendOppdaterPersondata(aktørId, fødselsnummer)
        assertEtterspurteBehov("HentInfotrygdutbetalinger")
    }

    protected fun håndterSkalKlargjøresForVisning(
        aktørId: String = AKTØR,
        fødselsnummer: String = FØDSELSNUMMER,
    ) {
        sisteMeldingId = meldingssender.sendKlargjørPersonForVisning(aktørId, fødselsnummer)
        assertEtterspurteBehov("HentPersoninfoV2")
    }

    protected fun håndterSkjønnsfastsattSykepengegrunnlag(
        aktørId: String = AKTØR,
        fødselsnummer: String = FØDSELSNUMMER,
        organisasjonsnummer: String = ORGNR,
        vedtaksperiodeId: UUID = testperson.vedtaksperiodeId1,
        skjæringstidspunkt: LocalDate = 1.januar,
        arbeidsgivere: List<Skjonnsfastsettelse.SkjonnsfastsettelseArbeidsgiver> =
            listOf(
                Skjonnsfastsettelse.SkjonnsfastsettelseArbeidsgiver(
                    organisasjonsnummer = organisasjonsnummer,
                    arlig = 1.0,
                    fraArlig = 1.0,
                    arsak = "årsak",
                    type = Skjonnsfastsettelse.SkjonnsfastsettelseArbeidsgiver.SkjonnsfastsettelseType.OMREGNET_ARSINNTEKT,
                    begrunnelseMal = "begrunnelseMal",
                    begrunnelseKonklusjon = "begrunnelseKonklusjon",
                    begrunnelseFritekst = "begrunnelseFritekst",
                    lovhjemmel =
                        Lovhjemmel(
                            paragraf = "paragraf",
                            ledd = "ledd",
                            bokstav = "bokstav",
                            lovverk = "folketrygdloven",
                            lovverksversjon = "",
                        ),
                    initierendeVedtaksperiodeId = vedtaksperiodeId.toString(),
                ),
            ),
    ) {
        håndterOverstyring(aktørId, fødselsnummer, organisasjonsnummer, "skjønnsmessig_fastsettelse") {
            val handling =
                Skjonnsfastsettelse(
                    aktorId = aktørId,
                    fodselsnummer = fødselsnummer,
                    skjaringstidspunkt = skjæringstidspunkt,
                    arbeidsgivere = arbeidsgivere,
                    vedtaksperiodeId = vedtaksperiodeId,
                )
            testMediator.håndter(handling, saksbehandler)
            // Her må det gjøres kall til api for å sende inn skjønnsfastsettelse
        }
    }

    protected fun håndterOverstyrTidslinje(
        aktørId: String = AKTØR,
        fødselsnummer: String = FØDSELSNUMMER,
        organisasjonsnummer: String = ORGNR,
        vedtaksperiodeId: UUID = testperson.vedtaksperiodeId1,
        dager: List<OverstyringDag> =
            listOf(
                OverstyringDag(1.januar(1970), Dagtype.Feriedag.toString(), Dagtype.Sykedag.toString(), null, 100, null),
            ),
    ) {
        håndterOverstyring(aktørId, fødselsnummer, organisasjonsnummer, "overstyr_tidslinje") {
            val handling =
                TidslinjeOverstyring(
                    vedtaksperiodeId,
                    organisasjonsnummer,
                    fødselsnummer,
                    aktørId,
                    "En begrunnelse",
                    dager,
                )
            testMediator.håndter(handling, saksbehandler)
            // Her må det gjøres kall til api for å sende inn overstyring av tidslinje
        }
    }

    protected fun håndterOverstyrInntektOgRefusjon(
        aktørId: String = AKTØR,
        fødselsnummer: String = FØDSELSNUMMER,
        skjæringstidspunkt: LocalDate = 1.januar(1970),
        vedtaksperiodeId: UUID = UUID.randomUUID(),
        arbeidsgivere: List<OverstyringArbeidsgiver> =
            listOf(
                OverstyringArbeidsgiver(
                    organisasjonsnummer = ORGNR,
                    manedligInntekt = 25000.0,
                    fraManedligInntekt = 25001.0,
                    forklaring = "testbortforklaring",
                    lovhjemmel = Lovhjemmel("8-28", "LEDD_1", "BOKSTAV_A", "folketrygdloven", "1970-01-01"),
                    refusjonsopplysninger = null,
                    fraRefusjonsopplysninger = null,
                    begrunnelse = "en begrunnelse",
                    fom = null,
                    tom = null,
                ),
            ),
    ) {
        håndterOverstyring(aktørId, fødselsnummer, ORGNR, "overstyr_inntekt_og_refusjon") {
            val handling =
                InntektOgRefusjonOverstyring(aktørId, fødselsnummer, skjæringstidspunkt, arbeidsgivere, vedtaksperiodeId)
            testMediator.håndter(handling, saksbehandler)
        }
    }

    protected fun håndterOverstyrArbeidsforhold(
        aktørId: String = AKTØR,
        fødselsnummer: String = FØDSELSNUMMER,
        organisasjonsnummer: String = ORGNR,
        skjæringstidspunkt: LocalDate = 1.januar,
        vedtaksperiodeId: UUID = UUID.randomUUID(),
        overstyrteArbeidsforhold: List<OverstyringArbeidsforhold> =
            listOf(
                OverstyringArbeidsforhold(
                    orgnummer = organisasjonsnummer,
                    deaktivert = true,
                    begrunnelse = "begrunnelse",
                    forklaring = "forklaring",
                    lovhjemmel = Lovhjemmel("8-15", null, null, "folketrygdloven", "1998-12-18"),
                ),
            ),
    ) {
        håndterOverstyring(aktørId, fødselsnummer, organisasjonsnummer, "overstyr_arbeidsforhold") {
            val handling =
                ArbeidsforholdOverstyringHandling(
                    fodselsnummer = fødselsnummer,
                    aktorId = aktørId,
                    skjaringstidspunkt = skjæringstidspunkt,
                    overstyrteArbeidsforhold = overstyrteArbeidsforhold,
                    vedtaksperiodeId = vedtaksperiodeId,
                )
            testMediator.håndter(handling, saksbehandler)
        }
    }

    private fun håndterOverstyring(
        aktørId: String,
        fødselsnummer: String,
        organisasjonsnummer: String,
        overstyringHendelse: String,
        overstyringBlock: () -> Unit,
    ) {
        overstyringBlock()
        val sisteOverstyring = testRapid.inspektør.hendelser(overstyringHendelse).last()
        val hendelseId = UUID.fromString(sisteOverstyring["@id"].asText())
        håndterOverstyringIgangsatt(fødselsnummer, hendelseId)
        håndterVedtaksperiodeReberegnet(aktørId, fødselsnummer, organisasjonsnummer)
        håndterUtbetalingErstattet(aktørId, fødselsnummer, organisasjonsnummer, utbetalingId = UUID.randomUUID())
    }

    private fun håndterOverstyringIgangsatt(
        fødselsnummer: String,
        kildeId: UUID,
    ) {
        sisteMeldingId =
            meldingssender.sendOverstyringIgangsatt(
                fødselsnummer = fødselsnummer,
                orgnummer = ORGNR,
                berørtePerioder =
                    listOf(
                        mapOf(
                            "vedtaksperiodeId" to "${testperson.vedtaksperiodeId1}",
                        ),
                    ),
                kilde = kildeId,
            )
    }

    private fun erRevurdering(vedtaksperiodeId: UUID): Boolean {
        return sessionOf(dataSource).use { session ->
            @Language("PostgreSQL")
            val query =
                "SELECT true FROM selve_vedtaksperiode_generasjon WHERE vedtaksperiode_id = ? AND tilstand = '${Generasjon.VedtakFattet.navn()}' ORDER BY id DESC"
            session.run(queryOf(query, vedtaksperiodeId).map { it.boolean(1) }.asSingle) ?: false
        }
    }

    protected fun assertUtbetalinger(
        utbetalingId: UUID,
        forventetAntall: Int,
    ) {
        @Language("PostgreSQL")
        val query =
            "SELECT COUNT(1) FROM utbetaling_id ui INNER JOIN utbetaling u on ui.id = u.utbetaling_id_ref WHERE ui.utbetaling_id = ?"
        val antall =
            sessionOf(dataSource).use {
                it.run(queryOf(query, utbetalingId).map { it.int(1) }.asSingle)
            }
        assertEquals(forventetAntall, antall)
    }

    protected fun assertFeilendeMeldinger(
        forventetAntall: Int,
        hendelseId: UUID,
    ) {
        @Language("PostgreSQL")
        val query = "SELECT COUNT(1) FROM feilende_meldinger WHERE id = :id"
        val antall =
            sessionOf(dataSource).use { session ->
                session.run(queryOf(query, mapOf("id" to hendelseId)).map { it.int(1) }.asSingle)
            }
        assertEquals(forventetAntall, antall)
    }

    protected fun assertKommandokjedetilstander(
        hendelseId: UUID,
        vararg forventedeTilstander: Kommandokjedetilstand,
    ) {
        @Language("PostgreSQL")
        val query = "SELECT tilstand FROM command_context WHERE hendelse_id = ? ORDER BY id"
        val tilstander =
            sessionOf(dataSource).use { session ->
                session.run(
                    queryOf(query, hendelseId).map { it.string("tilstand") }.asList,
                )
            }
        assertEquals(forventedeTilstander.map { it.name }.toList(), tilstander)
    }

    protected fun assertGodkjenningsbehovBesvart(
        godkjent: Boolean,
        automatiskBehandlet: Boolean,
        vararg årsakerTilAvvist: String,
    ) {
        val løsning = testRapid.inspektør.løsning("Godkjenning") ?: fail("Forventet å finne svar på godkjenningsbehov")
        assertTrue(løsning.path("godkjent").isBoolean)
        assertEquals(godkjent, løsning.path("godkjent").booleanValue())
        assertEquals(automatiskBehandlet, løsning.path("automatiskBehandling").booleanValue())
        assertNotNull(løsning.path("godkjenttidspunkt").asLocalDateTime())
        if (årsakerTilAvvist.isNotEmpty()) {
            val begrunnelser = løsning["begrunnelser"].map { it.asText() }
            assertEquals(begrunnelser, begrunnelser.distinct())
            assertEquals(årsakerTilAvvist.toSet(), begrunnelser.toSet())
        }
    }

    protected fun assertGodkjenningsbehovIkkeBesvart() = testRapid.inspektør.løsning("Godkjenningsbehov") == null

    protected fun assertVedtaksperiodeAvvist(
        periodetype: String,
        begrunnelser: List<String>? = null,
        kommentar: String? = null,
    ) {
        testRapid.inspektør.hendelser("vedtaksperiode_avvist").first().let {
            assertEquals(periodetype, it.path("periodetype").asText())
            assertEquals(begrunnelser, it.path("begrunnelser")?.map(JsonNode::asText))
            // TODO: BUG: Vi sender faktisk kommentar som "null", ikke null...
            val faktiskKommentar = it.takeIf { it.hasNonNull("kommentar") }?.get("kommentar")?.asText()
            if (kommentar == null) {
                assertEquals("null", faktiskKommentar)
            } else {
                assertEquals(kommentar, faktiskKommentar)
            }
        }
    }

    protected fun assertSaksbehandleroppgave(
        vedtaksperiodeId: UUID = testperson.vedtaksperiodeId1,
        oppgavestatus: Oppgavestatus,
    ) {
        @Language("PostgreSQL")
        val query =
            "SELECT status FROM oppgave WHERE vedtak_ref = (SELECT id FROM vedtak WHERE vedtaksperiode_id = ?) ORDER by id DESC"
        val sisteOppgavestatus =
            sessionOf(dataSource).use { session ->
                session.run(
                    queryOf(
                        query,
                        vedtaksperiodeId,
                    ).map { enumValueOf<Oppgavestatus>(it.string("status")) }.asSingle,
                )
            }
        assertEquals(oppgavestatus, sisteOppgavestatus)
    }

    protected fun assertHarOppgaveegenskap(
        oppgaveId: Int,
        vararg forventedeEgenskaper: Egenskap,
    ) {
        val egenskaper = hentOppgaveegenskaper(oppgaveId)
        assertTrue(egenskaper.containsAll(forventedeEgenskaper.toList()))
    }

    protected fun assertHarIkkeOppgaveegenskap(
        oppgaveId: Int,
        vararg forventedeEgenskaper: Egenskap,
    ) {
        val egenskaper = hentOppgaveegenskaper(oppgaveId)
        assertTrue(egenskaper.none { it in forventedeEgenskaper })
    }

    private fun hentOppgaveegenskaper(oppgaveId: Int): Set<Egenskap> {
        @Language("PostgreSQL")
        val query = "select egenskaper from oppgave o where id = :oppgaveId"
        val egenskaper =
            requireNotNull(
                sessionOf(dataSource).use { session ->
                    session.run(
                        queryOf(query, mapOf("oppgaveId" to oppgaveId)).map { row ->
                            row.array<String>("egenskaper").map<String, Egenskap>(::enumValueOf).toSet()
                        }.asSingle,
                    )
                },
            ) { "Forventer å finne en oppgave for id=$oppgaveId" }

        return egenskaper
    }

    protected fun assertSaksbehandleroppgaveBleIkkeOpprettet(vedtaksperiodeId: UUID = testperson.vedtaksperiodeId1) {
        @Language("PostgreSQL")
        val query = "SELECT 1 FROM oppgave WHERE vedtak_ref = (SELECT id FROM vedtak WHERE vedtaksperiode_id = ?)"
        val antallOppgaver =
            sessionOf(dataSource).use { session ->
                session.run(queryOf(query, vedtaksperiodeId).map { it.int(1) }.asList)
            }
        assertEquals(0, antallOppgaver.size)
    }

    protected fun assertVarsler(
        vedtaksperiodeId: UUID,
        forventetAntall: Int,
    ) {
        val antall =
            sessionOf(dataSource).use { session ->
                @Language("PostgreSQL")
                val query = "SELECT COUNT(1) FROM selve_varsel WHERE vedtaksperiode_id = ?"
                session.run(queryOf(query, vedtaksperiodeId).map { it.int(1) }.asSingle)
            }
        assertEquals(forventetAntall, antall)
    }

    protected fun assertGodkjentVarsel(
        vedtaksperiodeId: UUID,
        varselkode: String,
    ) {
        val antall =
            sessionOf(dataSource).use { session ->
                @Language("PostgreSQL")
                val query =
                    "SELECT COUNT(1) FROM selve_varsel WHERE vedtaksperiode_id = ? AND kode = ? AND status = 'GODKJENT'"
                session.run(queryOf(query, vedtaksperiodeId, varselkode).map { it.int(1) }.asSingle)
            }
        assertEquals(1, antall)
    }

    protected fun assertVarsel(
        vedtaksperiodeId: UUID,
        varselkode: String,
    ) {
        val antall =
            sessionOf(dataSource).use { session ->
                @Language("PostgreSQL")
                val query =
                    "SELECT COUNT(1) FROM selve_varsel WHERE vedtaksperiode_id = ? AND kode = ?"
                session.run(queryOf(query, vedtaksperiodeId, varselkode).map { it.int(1) }.asSingle)
            }
        assertEquals(1, antall)
    }

    protected fun assertSkjermet(
        fødselsnummer: String = FØDSELSNUMMER,
        skjermet: Boolean?,
    ) {
        assertEquals(skjermet, EgenAnsattDao(dataSource).erEgenAnsatt(fødselsnummer))
    }

    protected fun assertAdressebeskyttelse(
        fødselsnummer: String = FØDSELSNUMMER,
        adressebeskyttelse: Adressebeskyttelse?,
    ) {
        assertEquals(adressebeskyttelse, PersonDao(dataSource).finnAdressebeskyttelse(fødselsnummer))
    }

    protected fun assertVedtaksperiodeEksisterer(vedtaksperiodeId: UUID) {
        assertEquals(1, vedtak(vedtaksperiodeId))
    }

    protected fun assertVedtaksperiodeForkastet(vedtaksperiodeId: UUID) {
        assertEquals(1, forkastedeVedtak(vedtaksperiodeId))
    }

    protected fun assertVedtaksperiodeEksistererIkke(vedtaksperiodeId: UUID) {
        assertEquals(0, vedtak(vedtaksperiodeId))
    }

    protected fun assertPersonEksisterer(
        fødselsnummer: String,
        aktørId: String,
    ) {
        assertEquals(
            1,
            person(fødselsnummer, aktørId),
        ) { "Person med fødselsnummer=$fødselsnummer og aktørId=$aktørId finnes ikke i databasen" }
    }

    protected fun assertPersonEksistererIkke(
        fødselsnummer: String,
        aktørId: String,
    ) {
        assertEquals(0, person(fødselsnummer, aktørId))
    }

    protected fun assertArbeidsgiverEksisterer(organisasjonsnummer: String) {
        assertEquals(
            1,
            arbeidsgiver(organisasjonsnummer),
        ) { "Arbeidsgiver med organisasjonsnummer=$organisasjonsnummer finnes ikke i databasen" }
    }

    protected fun assertUtgåendeMelding(hendelse: String) {
        val meldinger = testRapid.inspektør.hendelser(hendelse, sisteMeldingId)
        assertEquals(1, meldinger.size) {
            "Utgående meldinger: ${meldinger.joinToString { it.path("@event_name").asText() }}"
        }
    }

    private fun assertIngenUtgåendeMeldinger() {
        val meldinger = testRapid.inspektør.hendelser(sisteMeldingId)
        assertEquals(0, meldinger.size) {
            "Utgående meldinger: ${meldinger.joinToString { it.path("@event_name").asText() }}"
        }
    }

    protected fun assertIkkeUtgåendeMelding(hendelse: String) {
        val meldinger = testRapid.inspektør.hendelser(hendelse)
        assertEquals(0, meldinger.size) {
            "Utgående meldinger: ${meldinger.joinToString { it.path("@event_name").asText() }}"
        }
    }

    private fun assertUtgåendeBehovløsning(behov: String) {
        val løsning = testRapid.inspektør.løsningOrNull(behov)
        assertNotNull(løsning)
    }

    protected fun assertInnholdIBehov(
        behov: String,
        block: (JsonNode) -> Unit,
    ) {
        val etterspurtBehov = testRapid.inspektør.behov(behov).last()
        block(etterspurtBehov)
    }

    private fun assertEtterspurteBehov(vararg behov: String) {
        val etterspurteBehov = testRapid.inspektør.behov(sisteMeldingId)
        val forårsaketAvId = inspektør.siste("behov")["@forårsaket_av"]["id"].asText()
        assertEquals(behov.toList(), etterspurteBehov) {
            val ikkeEtterspurt = behov.toSet() - etterspurteBehov.toSet()
            "Forventet at følgende behov skulle være etterspurt: $ikkeEtterspurt\nFaktisk etterspurte behov: $etterspurteBehov\n"
        }
        assertEquals(forårsaketAvId, sisteMeldingId.toString())
    }

    protected fun assertIngenEtterspurteBehov() {
        assertEquals(emptyList<String>(), testRapid.inspektør.behov(sisteMeldingId))
    }

    protected fun assertSisteEtterspurteBehov(behov: String) {
        val sisteEtterspurteBehov = testRapid.inspektør.behov().last()
        assertEquals(sisteEtterspurteBehov, behov)
    }

    protected fun assertUtbetaling(
        arbeidsgiverbeløp: Int,
        personbeløp: Int,
    ) {
        assertEquals(arbeidsgiverbeløp, finnbeløp("arbeidsgiver"))
        assertEquals(personbeløp, finnbeløp("person"))
    }

    protected fun assertOverstyringer(
        vedtaksperiodeId: UUID,
        vararg forventedeOverstyringstyper: OverstyringType,
    ) {
        val utførteOverstyringstyper = testMediator.overstyringstyperForVedtaksperiode(vedtaksperiodeId)
        assertEquals(forventedeOverstyringstyper.toSet(), utførteOverstyringstyper.toSet()) {
            val ikkeEtterspurt = utførteOverstyringstyper.toSet() - forventedeOverstyringstyper.toSet()
            "Følgende overstyringstyper ble utført i tillegg til de forventede: $ikkeEtterspurt\nForventede typer: ${forventedeOverstyringstyper.joinToString()}\n"
        }
    }

    protected fun assertTotrinnsvurdering(oppgaveId: Long) {
        @Language("PostgreSQL")
        val query =
            """
            SELECT 1 FROM totrinnsvurdering
            INNER JOIN vedtak v on totrinnsvurdering.vedtaksperiode_id = v.vedtaksperiode_id
            INNER JOIN oppgave o on v.id = o.vedtak_ref
            WHERE o.id = ?
            AND utbetaling_id_ref IS NULL
            """.trimIndent()
        val erToTrinnsvurdering =
            sessionOf(dataSource).use { session ->
                session.run(queryOf(query, oppgaveId).map { it.boolean(1) }.asSingle)
            } ?: throw IllegalStateException("Finner ikke oppgave med id $oppgaveId")
        assertTrue(erToTrinnsvurdering) {
            "Forventer at oppgaveId=$oppgaveId krever totrinnsvurdering"
        }
    }

    internal fun erFerdigstilt(godkjenningsbehovId: UUID): Boolean {
        @Language("PostgreSQL")
        val query = "SELECT tilstand FROM command_context WHERE hendelse_id = ? ORDER by id DESC LIMIT 1"
        return sessionOf(dataSource).use { session ->
            session.run(queryOf(query, godkjenningsbehovId).map { it.string("tilstand") }.asSingle) == "FERDIG"
        }
    }

    internal fun commandContextId(godkjenningsbehovId: UUID): UUID {
        @Language("PostgreSQL")
        val query = "SELECT context_id FROM command_context WHERE hendelse_id = ? ORDER by id DESC LIMIT 1"
        return sessionOf(dataSource).use { session ->
            requireNotNull(
                session.run(queryOf(query, godkjenningsbehovId).map { it.uuid("context_id") }.asSingle),
            )
        }
    }

    private fun finnbeløp(type: String): Int? {
        @Suppress("SqlResolve")
        @Language("PostgreSQL")
        val query = "SELECT ${type}beløp FROM utbetaling_id WHERE utbetaling_id = ?"
        return sessionOf(dataSource).use {
            it.run(queryOf(query, utbetalingId).map { it.intOrNull("${type}beløp") }.asSingle)
        }
    }

    protected fun person(
        fødselsnummer: String,
        aktørId: String,
    ): Int {
        return sessionOf(dataSource).use { session ->
            @Language("PostgreSQL")
            val query = "SELECT COUNT(*) FROM person WHERE fødselsnummer = ? AND aktør_id = ?"
            requireNotNull(
                session.run(queryOf(query, fødselsnummer, aktørId).map { row -> row.int(1) }.asSingle),
            )
        }
    }

    protected fun arbeidsgiver(organisasjonsnummer: String): Int {
        return sessionOf(dataSource).use { session ->
            @Language("PostgreSQL")
            val query = "SELECT COUNT(*) FROM arbeidsgiver WHERE orgnummer = ?"
            requireNotNull(
                session.run(queryOf(query, organisasjonsnummer.toLong()).map { row -> row.int(1) }.asSingle),
            )
        }
    }

    protected fun vedtak(vedtaksperiodeId: UUID): Int {
        return sessionOf(dataSource).use { session ->
            @Language("PostgreSQL")
            val query = "SELECT COUNT(*) FROM vedtak WHERE vedtaksperiode_id = ?"
            requireNotNull(
                session.run(queryOf(query, vedtaksperiodeId).map { row -> row.int(1) }.asSingle),
            )
        }
    }

    private fun forkastedeVedtak(vedtaksperiodeId: UUID): Int {
        return sessionOf(dataSource).use { session ->
            @Language("PostgreSQL")
            val query = "SELECT COUNT(*) FROM vedtak WHERE vedtaksperiode_id = ? AND forkastet = TRUE"
            requireNotNull(
                session.run(queryOf(query, vedtaksperiodeId).map { row -> row.int(1) }.asSingle),
            )
        }
    }

    private fun List<OverstyringRefusjonselement>.byggRefusjonselementEvent() =
        this.map {
            OverstyrtRefusjonselementEvent(
                fom = it.fom,
                tom = it.tom,
                beløp = it.belop,
            )
        }

    private fun lagVarseldefinisjoner() {
        Varselkode.entries.forEach { varselkode ->
            lagVarseldefinisjon(varselkode.name)
        }
    }

    private fun lagVarseldefinisjon(varselkode: String) {
        @Language("PostgreSQL")
        val query =
            "INSERT INTO api_varseldefinisjon(unik_id, kode, tittel, forklaring, handling, avviklet, opprettet) VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT (unik_id) DO NOTHING"
        sessionOf(dataSource).use { session ->
            session.run(
                queryOf(
                    query,
                    UUID.nameUUIDFromBytes(varselkode.toByteArray()),
                    varselkode,
                    "En tittel for varselkode=$varselkode",
                    "En forklaring for varselkode=$varselkode",
                    "En handling for varselkode=$varselkode",
                    false,
                    LocalDateTime.now(),
                ).asUpdate,
            )
        }
    }

    protected fun mockSnapshot(fødselsnummer: String = FØDSELSNUMMER) {
        every { snapshotClient.hentSnapshot(fødselsnummer) } returns
            snapshot(
                versjon = 1,
                fødselsnummer = godkjenningsbehovTestdata.fødselsnummer,
                aktørId = godkjenningsbehovTestdata.aktørId,
                organisasjonsnummer = godkjenningsbehovTestdata.organisasjonsnummer,
                vedtaksperiodeId = godkjenningsbehovTestdata.vedtaksperiodeId,
                utbetalingId = godkjenningsbehovTestdata.utbetalingId,
                arbeidsgiverbeløp = 0,
                personbeløp = 0,
            )
    }

    protected enum class Kommandokjedetilstand {
        NY,
        SUSPENDERT,
        FERDIG,
        AVBRUTT,
    }
}
