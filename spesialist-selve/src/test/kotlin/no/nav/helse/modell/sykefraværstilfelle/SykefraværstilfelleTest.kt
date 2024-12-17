package no.nav.helse.modell.sykefraværstilfelle

import no.nav.helse.februar
import no.nav.helse.januar
import no.nav.helse.modell.person.vedtaksperiode.Varsel
import no.nav.helse.modell.person.vedtaksperiode.Behandling
import no.nav.helse.modell.person.vedtaksperiode.Behandling.Companion.forhindrerAutomatisering
import no.nav.helse.modell.person.vedtaksperiode.Sykefraværstilfelle
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

internal class SykefraværstilfelleTest {
    @Test
    fun `Kan ikke opprette et sykefraværstilfelle uten å ha en generasjon`() {
        assertThrows<IllegalStateException> {
            sykefraværstilfelle(gjeldendeGenerasjoner = emptyList())
        }
    }

    @Test
    fun `har ikke aktive varsler`() {
        val gjeldendeGenerasjon1 = generasjon(UUID.randomUUID())
        val gjeldendeGenerasjon2 = generasjon(UUID.randomUUID())
        assertFalse(listOf(gjeldendeGenerasjon1, gjeldendeGenerasjon2).forhindrerAutomatisering(28.februar))
    }

    @Test
    fun `har ikke aktive varsler når generasjonene har utbetalingId men ikke fom`() {
        val gjeldendeGenerasjon1 = generasjon(UUID.randomUUID())
        val gjeldendeGenerasjon2 = generasjon(UUID.randomUUID())
        val utbetalingId = UUID.randomUUID()
        gjeldendeGenerasjon1.håndterNyUtbetaling(utbetalingId)
        gjeldendeGenerasjon2.håndterNyUtbetaling(utbetalingId)
        assertFalse(listOf(gjeldendeGenerasjon1, gjeldendeGenerasjon2).forhindrerAutomatisering(28.februar))
    }

    @Test
    fun `har aktive varsler`() {
        val vedtaksperiodeId2 = UUID.randomUUID()
        val gjeldendeGenerasjon1 = generasjon(UUID.randomUUID())
        val gjeldendeGenerasjon2 = generasjon(vedtaksperiodeId2)
        gjeldendeGenerasjon2.håndterNyttVarsel(
            Varsel(UUID.randomUUID(), "SB_EX_1", LocalDateTime.now(), vedtaksperiodeId2),
        )
        assertTrue(listOf(gjeldendeGenerasjon1, gjeldendeGenerasjon2).forhindrerAutomatisering(28.februar))
    }

    @Test
    fun `thrower hvis generasjon ikke finnes`() {
        assertThrows<IllegalArgumentException> { sykefraværstilfelle().haster(UUID.randomUUID()) }
    }

    private fun generasjon(vedtaksperiodeId: UUID = UUID.randomUUID()) =
        Behandling(
            id = UUID.randomUUID(),
            vedtaksperiodeId = vedtaksperiodeId,
            fom = 1.januar,
            tom = 31.januar,
            skjæringstidspunkt = 1.januar,
        )

    private fun sykefraværstilfelle(
        fødselsnummer: String = "12345678910",
        skjæringstidspunkt: LocalDate = 1.januar,
        gjeldendeGenerasjoner: List<Behandling> = listOf(generasjon()),
    ) = Sykefraværstilfelle(
        fødselsnummer,
        skjæringstidspunkt,
        gjeldendeGenerasjoner,
    )
}
