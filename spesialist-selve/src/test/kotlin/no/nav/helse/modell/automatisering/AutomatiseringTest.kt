package no.nav.helse.modell.automatisering

import ToggleHelpers.disable
import ToggleHelpers.enable
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.januar
import no.nav.helse.mediator.Toggle
import no.nav.helse.modell.VedtakDao
import no.nav.helse.modell.WarningDao
import no.nav.helse.modell.egenansatt.EgenAnsattDao
import no.nav.helse.modell.gosysoppgaver.ÅpneGosysOppgaverDao
import no.nav.helse.modell.overstyring.OverstyringDao
import no.nav.helse.modell.person.PersonDao
import no.nav.helse.modell.risiko.Risikovurdering
import no.nav.helse.modell.risiko.RisikovurderingDao
import no.nav.helse.modell.sykefraværstilfelle.Sykefraværstilfelle
import no.nav.helse.modell.utbetaling.Utbetaling
import no.nav.helse.modell.utbetaling.Utbetalingtype
import no.nav.helse.modell.varsel.Varsel
import no.nav.helse.modell.vedtaksperiode.Generasjon
import no.nav.helse.modell.vedtaksperiode.Inntektskilde
import no.nav.helse.modell.vedtaksperiode.Periodetype
import no.nav.helse.modell.vergemal.VergemålDao
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class AutomatiseringTest {

    private val vedtakDaoMock = mockk<VedtakDao>()
    private val warningDaoMock = mockk<WarningDao>()
    private val risikovurderingDaoMock = mockk<RisikovurderingDao> {
        every { hentRisikovurdering(vedtaksperiodeId) } returns Risikovurdering.restore(true)
    }
    private val åpneGosysOppgaverDaoMock = mockk<ÅpneGosysOppgaverDao>(relaxed = true)
    private val egenAnsattDao = mockk<EgenAnsattDao>(relaxed = true)
    private val personDaoMock = mockk<PersonDao>(relaxed = true)
    private val automatiseringDaoMock = mockk<AutomatiseringDao>(relaxed = true)
    private val vergemålDaoMock = mockk<VergemålDao>(relaxed = true)
    private val overstyringDaoMock = mockk<OverstyringDao>(relaxed = true)
    private var stikkprøveFullRefusjonEnArbeidsgiver = false
    private var stikkprøveUtsEnArbeidsgiverFørstegangsbehandling = false
    private var stikkprøveUtsEnArbeidsgiverForlengelse = false
    private val stikkprøver = object : Stikkprøver {
        override fun utsFlereArbeidsgivereFørstegangsbehandling() = false
        override fun utsFlereArbeidsgivereForlengelse() = false
        override fun utsEnArbeidsgiverFørstegangsbehandling() = stikkprøveUtsEnArbeidsgiverFørstegangsbehandling
        override fun utsEnArbeidsgiverForlengelse() = stikkprøveUtsEnArbeidsgiverForlengelse
        override fun fullRefusjonFlereArbeidsgivereFørstegangsbehandling() = false
        override fun fullRefusjonFlereArbeidsgivereForlengelse() = false
        override fun fullRefusjonEnArbeidsgiver() = stikkprøveFullRefusjonEnArbeidsgiver
    }

    private val automatisering =
        Automatisering(
            warningDao = warningDaoMock,
            risikovurderingDao = risikovurderingDaoMock,
            automatiseringDao = automatiseringDaoMock,
            åpneGosysOppgaverDao = åpneGosysOppgaverDaoMock,
            egenAnsattDao = egenAnsattDao,
            vergemålDao = vergemålDaoMock,
            personDao = personDaoMock,
            vedtakDao = vedtakDaoMock,
            overstyringDao = overstyringDaoMock,
            stikkprøver = stikkprøver
        )

    companion object {
        private const val fødselsnummer = "12345678910"
        private val vedtaksperiodeId = UUID.randomUUID()
        private val utbetalingId = UUID.randomUUID()
        private val hendelseId = UUID.randomUUID()
        private val periodetype = Periodetype.FORLENGELSE
    }

    @BeforeEach
    fun setupDefaultTilHappyCase() {
        every { risikovurderingDaoMock.hentRisikovurdering(vedtaksperiodeId) } returns Risikovurdering.restore(true)
        every { warningDaoMock.finnAktiveWarnings(vedtaksperiodeId) } returns emptyList()
        every { vedtakDaoMock.finnVedtaksperiodetype(vedtaksperiodeId) } returns periodetype
        every { vedtakDaoMock.finnInntektskilde(vedtaksperiodeId) } returns Inntektskilde.EN_ARBEIDSGIVER
        every { åpneGosysOppgaverDaoMock.harÅpneOppgaver(any()) } returns 0
        every { egenAnsattDao.erEgenAnsatt(any()) } returns false
        every { overstyringDaoMock.harVedtaksperiodePågåendeOverstyring(any()) } returns false
        stikkprøveFullRefusjonEnArbeidsgiver = false
        stikkprøveUtsEnArbeidsgiverForlengelse = false
    }

    @Test
    fun `vedtaksperiode som oppfyller krav blir automatisk godkjent og lagret`() {
        gårAutomatisk()
    }

    @Test
    fun `vedtaksperiode med warnings er ikke automatiserbar`() {
        val gjeldendeGenerasjon = Generasjon(UUID.randomUUID(), vedtaksperiodeId, 1.januar, 31.januar, 1.januar)
        gjeldendeGenerasjon.håndterNyttVarsel(
            Varsel(UUID.randomUUID(), "RV_IM_1", LocalDateTime.now(), vedtaksperiodeId),
            hendelseId
        )
        support.run {
            forsøkAutomatisering(generasjoner = listOf(gjeldendeGenerasjon))
            assertGikkTilManuell()
        }
    }

    @Test
    fun `vedtaksperiode uten ok risikovurdering er ikke automatiserbar`() {
        every { risikovurderingDaoMock.hentRisikovurdering(vedtaksperiodeId) } returns Risikovurdering.restore(false)
        gårTilManuell()
    }

    @Test
    fun `vedtaksperiode med null risikovurdering er ikke automatiserbar`() {
        every { risikovurderingDaoMock.hentRisikovurdering(vedtaksperiodeId) } returns null
        gårTilManuell()
    }

    @Test
    fun `vedtaksperiode med åpne oppgaver er ikke automatiserbar`() {
        every { åpneGosysOppgaverDaoMock.harÅpneOppgaver(any()) } returns 1
        gårTilManuell()
    }

    @Test
    fun `vedtaksperiode med _null_ åpne oppgaver er ikke automatiserbar`() {
        every { åpneGosysOppgaverDaoMock.harÅpneOppgaver(any()) } returns null
        gårTilManuell()
    }

    @Test
    fun `vedtaksperiode med egen ansatt er ikke automatiserbar`() {
        every { egenAnsattDao.erEgenAnsatt(any()) } returns true
        gårTilManuell()
    }

    @Test
    fun `vedtaksperiode plukket ut til stikkprøve skal ikke automatisk godkjennes`() {
        stikkprøveFullRefusjonEnArbeidsgiver = true
        gårTilManuell()
    }

    @Test
    fun `person med flere arbeidsgivere skal automatisk godkjennes`() {
        every { vedtakDaoMock.finnInntektskilde(vedtaksperiodeId) } returns Inntektskilde.FLERE_ARBEIDSGIVERE
        gårAutomatisk()
    }

    @Test
    fun `periode til revurdering skal ikke automatisk godkjennes`() {
        gårTilManuell(enUtbetaling(personbeløp = 1, type = Utbetalingtype.REVURDERING))
    }

    @Test
    fun `revurdering uten endringer i beløp kan automatisk godkjennes`() {
        gårAutomatisk(
            enUtbetaling(
                arbeidsgiverbeløp = 0,
                personbeløp = 0,
                type = Utbetalingtype.REVURDERING
            )
        )
    }

    @Test
    fun `periode til revurdering skal automatisk godkjennes om toggle er på`() {
        Toggle.AutomatiserRevuderinger.enable()
        gårAutomatisk(enUtbetaling(type = Utbetalingtype.REVURDERING))
        Toggle.AutomatiserRevuderinger.disable()
    }

    @Test
    fun `periode med vergemål skal ikke automatisk godkjennes`() {
        every { vergemålDaoMock.harVergemål(fødselsnummer) } returns true
        gårTilManuell()
    }

    @Test
    fun `periode med utbetaling til sykmeldt skal ikke automatisk godkjennes`() {
        Toggle.AutomatiserUtbetalingTilSykmeldt.disable()
        gårTilManuell(enUtbetaling(personbeløp = 500))
        Toggle.AutomatiserUtbetalingTilSykmeldt.enable()
    }

    @Test
    fun `forlengelse med utbetaling til sykmeldt skal automatisk godkjennes`() {
        gårAutomatisk(enUtbetaling(personbeløp = 500))
    }

    @Test
    fun `forlengelse med utbetaling til sykmeldt som plukkes ut som stikkprøve skal ikke automatisk godkjennes`() {
        stikkprøveUtsEnArbeidsgiverForlengelse = true
        gårTilManuell(enUtbetaling(personbeløp = 500))
    }

    @Test
    fun `førstegangsbehandling med utbetaling til sykmeldt skal automatisk godkjennes`() {
        gårAutomatisk(enUtbetaling(personbeløp = 500))
    }

    @Test
    fun `førstegangsbehandling med utbetaling til sykmeldt som plukkes ut som stikkprøve skal ikke automatisk godkjennes`() {
        stikkprøveUtsEnArbeidsgiverFørstegangsbehandling = true
        support.run {
            forsøkAutomatisering(
                periodetype = Periodetype.FØRSTEGANGSBEHANDLING,
                utbetaling = enUtbetaling(personbeløp = 500)
            )
            assertGikkTilManuell()
        }
    }

    @Test
    fun `periode med delvis refusjon skal automatisk godkjennes`() {
        gårAutomatisk(enUtbetaling(personbeløp = 500, arbeidsgiverbeløp = 500))
    }

    @Test
    fun `periode med pågående overstyring skal ikke automatisk godkjennes`() {
        every { overstyringDaoMock.harVedtaksperiodePågåendeOverstyring(any()) } returns true
        gårTilManuell()
    }

    @Test
    fun `nullrevurdering grunnet saksbehandleroverstyring skal ikke automatisk godkjennes`() {
        support.forsøkAutomatisering(
            utbetaling = enUtbetaling(
                arbeidsgiverbeløp = 0,
                personbeløp = 0,
                type = Utbetalingtype.REVURDERING
            )
        )
        support.assertBleAutomatiskGodkjent()

        clearMocks(support.onAutomatiserbar, automatiseringDaoMock)

        every { overstyringDaoMock.harVedtaksperiodePågåendeOverstyring(any()) } returns true
        support.forsøkAutomatisering()
        support.assertGikkTilManuell()
    }

    private val support = object {
        val onAutomatiserbar = mockk<() -> Unit>(relaxed = true)
        fun forsøkAutomatisering(
            periodetype: Periodetype = Companion.periodetype,
            generasjoner: List<Generasjon> = emptyList(),
            utbetaling: Utbetaling = enUtbetaling(),
        ) = automatisering.utfør(
            fødselsnummer,
            vedtaksperiodeId,
            hendelseId,
            utbetaling,
            periodetype,
            sykefraværstilfelle = Sykefraværstilfelle(fødselsnummer, 1.januar, generasjoner),
            periodeTom = 31.januar,
            onAutomatiserbar,
        )

        fun assertBleAutomatiskGodkjent() {
            verify(exactly = 1) { onAutomatiserbar() }
            verify(exactly = 1) { automatiseringDaoMock.automatisert(vedtaksperiodeId, hendelseId, utbetalingId) }
            verify(exactly = 0) { automatiseringDaoMock.manuellSaksbehandling(any(), any(), any(), any()) }
        }

        fun assertGikkTilManuell() {
            verify(exactly = 0) { onAutomatiserbar() }
            verify(exactly = 0) { automatiseringDaoMock.automatisert(any(), any(), any()) }
            verify(exactly = 1) {
                if (stikkprøveUtsEnArbeidsgiverForlengelse || stikkprøveFullRefusjonEnArbeidsgiver || stikkprøveUtsEnArbeidsgiverFørstegangsbehandling)
                    automatiseringDaoMock.stikkprøve(any(), any(), any())
                else automatiseringDaoMock.manuellSaksbehandling(any(), vedtaksperiodeId, hendelseId, utbetalingId)
            }
        }
    }

    private fun enUtbetaling(
        arbeidsgiverbeløp: Int = 500,
        personbeløp: Int = 0,
        type: Utbetalingtype = Utbetalingtype.UTBETALING,
    ) = Utbetaling(utbetalingId, arbeidsgiverbeløp, personbeløp, type)

    private fun gårTilManuell(utbetaling: Utbetaling = enUtbetaling()) = support.run {
        forsøkAutomatisering(utbetaling = utbetaling)
        assertGikkTilManuell()
    }

    private fun gårAutomatisk(utbetaling: Utbetaling = enUtbetaling()) = support.run {
        forsøkAutomatisering(utbetaling = utbetaling)
        assertBleAutomatiskGodkjent()
    }
}

