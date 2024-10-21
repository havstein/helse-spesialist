package no.nav.helse.modell.kommando

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.helse.db.OverstyringRepository
import no.nav.helse.februar
import no.nav.helse.januar
import no.nav.helse.mediator.oppgave.OppgaveService
import no.nav.helse.modell.person.vedtaksperiode.Varsel
import no.nav.helse.modell.sykefraværstilfelle.Sykefraværstilfelle
import no.nav.helse.modell.totrinnsvurdering.TotrinnsvurderingService
import no.nav.helse.modell.totrinnsvurdering.TotrinnsvurderingOld
import no.nav.helse.modell.vedtaksperiode.Generasjon
import no.nav.helse.modell.vedtaksperiode.SpleisVedtaksperiode
import no.nav.helse.spesialist.api.overstyring.OverstyringType
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.time.LocalDateTime
import java.util.UUID

internal class VurderBehovForTotrinnskontrollTest {
    private companion object {
        private val VEDTAKSPERIODE_ID_2 = UUID.randomUUID()
        private val VEDTAKSPERIODE_ID_1 = UUID.randomUUID()
        private val FØDSELSNUMMER = "fnr"
    }

    private val totrinnsvurderingService = mockk<TotrinnsvurderingService>(relaxed = true)
    private val oppgaveService = mockk<OppgaveService>(relaxed = true)
    private val overstyringRepository = mockk<OverstyringRepository>(relaxed = true)
    private lateinit var context: CommandContext

    val sykefraværstilfelle =
        Sykefraværstilfelle(
            FØDSELSNUMMER,
            1.januar,
            listOf(
                Generasjon(UUID.randomUUID(), VEDTAKSPERIODE_ID_1, 1.januar, 31.januar, 1.januar),
                Generasjon(UUID.randomUUID(), VEDTAKSPERIODE_ID_2, 1.februar, 28.februar, 1.januar),
            ),
        )
    private val command =
        VurderBehovForTotrinnskontroll(
            fødselsnummer = FØDSELSNUMMER,
            vedtaksperiodeId = VEDTAKSPERIODE_ID_2,
            oppgaveService = oppgaveService,
            overstyringRepository = overstyringRepository,
            totrinnsvurderingService = totrinnsvurderingService,
            sykefraværstilfelle = sykefraværstilfelle,
            spleisVedtaksperioder = listOf(SpleisVedtaksperiode(VEDTAKSPERIODE_ID_1, UUID.randomUUID(), 1.januar, 31.januar, 1.januar), SpleisVedtaksperiode(VEDTAKSPERIODE_ID_2, UUID.randomUUID(), 1.februar, 28.februar, 1.januar)),
        )

    @BeforeEach
    fun setUp() {
        context = CommandContext(UUID.randomUUID())
    }

    @Test
    fun `Oppretter totrinnsvurdering dersom vedtaksperioden finnes i overstyringer_for_vedtaksperioder`() {
        every { overstyringRepository.finnOverstyringerMedTypeForVedtaksperiode(any()) } returns listOf(OverstyringType.Dager)

        assertTrue(command.execute(context))

        verify(exactly = 1) { totrinnsvurderingService.opprett(any()) }
    }

    @Test
    fun `Oppretter totrinssvurdering dersom vedtaksperioden har varsel for lovvalg og medlemskap, og ikke har hatt oppgave som har vært ferdigstilt før`() {
        sykefraværstilfelle.håndter(
            Varsel(UUID.randomUUID(), "RV_MV_1", LocalDateTime.now(), VEDTAKSPERIODE_ID_2),
        )
        every { oppgaveService.harFerdigstiltOppgave(VEDTAKSPERIODE_ID_2) } returns false

        assertTrue(command.execute(context))
        verify(exactly = 1) { totrinnsvurderingService.opprett(any()) }
    }

    @ParameterizedTest
    @EnumSource(value = Varsel.Status::class, names = ["AKTIV"], mode = EnumSource.Mode.EXCLUDE)
    fun `Oppretter ikke totrinnssvurdering dersom tidligere vedtaksperiode har varsel for lovvalg og medlemskap og er utbetalt`(
        status: Varsel.Status,
    ) {
        sykefraværstilfelle.håndter(
            Varsel(UUID.randomUUID(), "RV_MV_1", LocalDateTime.now(), VEDTAKSPERIODE_ID_1, status),
        )
        every { oppgaveService.harFerdigstiltOppgave(VEDTAKSPERIODE_ID_2) } returns false

        assertTrue(command.execute(context))
        verify(exactly = 0) { totrinnsvurderingService.opprett(any()) }
    }

    @Test
    fun `Hvis totrinnsvurdering har saksbehander skal oppgaven reserveres`() {
        val saksbehander = UUID.randomUUID()

        every { overstyringRepository.finnOverstyringerMedTypeForVedtaksperiode(any()) } returns listOf(OverstyringType.Dager)
        every { totrinnsvurderingService.opprett(any()) } returns
            TotrinnsvurderingOld(
                vedtaksperiodeId = VEDTAKSPERIODE_ID_2,
                erRetur = false,
                saksbehandler = saksbehander,
                beslutter = null,
                utbetalingIdRef = null,
                opprettet = LocalDateTime.now(),
                oppdatert = null,
            )

        assertTrue(command.execute(context))

        verify(exactly = 1) { totrinnsvurderingService.opprett(any()) }
        verify(exactly = 1) { oppgaveService.reserverOppgave(saksbehander, FØDSELSNUMMER) }
    }

    @Test
    fun `Hvis totrinnsvurdering har beslutter skal totrinnsvurderingen markeres som retur`() {
        val saksbehander = UUID.randomUUID()
        val beslutter = UUID.randomUUID()

        every { overstyringRepository.finnOverstyringerMedTypeForVedtaksperiode(any()) } returns listOf(OverstyringType.Dager)
        every { totrinnsvurderingService.opprett(any()) } returns
            TotrinnsvurderingOld(
                vedtaksperiodeId = VEDTAKSPERIODE_ID_2,
                erRetur = false,
                saksbehandler = saksbehander,
                beslutter = beslutter,
                utbetalingIdRef = null,
                opprettet = LocalDateTime.now(),
                oppdatert = null,
            )

        assertTrue(command.execute(context))

        verify(exactly = 1) { totrinnsvurderingService.opprett(any()) }
        verify(exactly = 1) { oppgaveService.reserverOppgave(saksbehander, FØDSELSNUMMER) }
        verify(exactly = 1) { totrinnsvurderingService.settAutomatiskRetur(VEDTAKSPERIODE_ID_2) }
    }

    @Test
    fun `Oppretter ikke totrinnsvurdering om det ikke er overstyring eller varsel for lovvalg og medlemskap`() {
        assertTrue(command.execute(context))

        verify(exactly = 0) { totrinnsvurderingService.opprett(any()) }
    }

    @Test
    fun `Oppretter trengerTotrinnsvurdering dersom oppgaven har blitt overstyrt`() {
        every { overstyringRepository.finnOverstyringerMedTypeForVedtaksperiode(any()) } returns listOf(OverstyringType.Dager)

        assertTrue(command.execute(context))
        verify(exactly = 1) { totrinnsvurderingService.opprett(any()) }
    }

    @Test
    fun `Oppretter trengerTotrinnsvurdering dersom oppgaven har fått avklart skjønnsfastsatt sykepengegrunnlag`() {
        every { overstyringRepository.finnOverstyringerMedTypeForVedtaksperiode(any()) } returns listOf(OverstyringType.Sykepengegrunnlag)

        assertTrue(command.execute(context))
        verify(exactly = 1) { totrinnsvurderingService.opprett(any()) }
    }
}
