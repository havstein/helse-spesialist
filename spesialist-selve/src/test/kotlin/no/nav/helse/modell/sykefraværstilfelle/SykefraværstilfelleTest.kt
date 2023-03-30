package no.nav.helse.modell.sykefraværstilfelle

import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.februar
import no.nav.helse.januar
import no.nav.helse.modell.varsel.VarselRepository
import no.nav.helse.modell.vedtaksperiode.Generasjon
import no.nav.helse.modell.vedtaksperiode.Vedtaksperiode
import no.nav.helse.modell.vedtaksperiode.Vedtaksperiode.Companion.harAktiveVarsler
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class SykefraværstilfelleTest {

    @Test
    fun `har ikke aktive varsler`() {
        val vedtaksperiodeId1 = UUID.randomUUID()
        val vedtaksperiodeId2 = UUID.randomUUID()
        val gjeldendeGenerasjon1 = generasjon(vedtaksperiodeId1)
        val gjeldendeGenerasjon2 = generasjon(vedtaksperiodeId2)
        gjeldendeGenerasjon1.håndterTidslinjeendring(1.januar, 31.januar, 1.januar)
        gjeldendeGenerasjon2.håndterTidslinjeendring(1.februar, 28.februar, 1.februar)
        val vedtaksperiode1 = Vedtaksperiode(vedtaksperiodeId1, gjeldendeGenerasjon1)
        val vedtaksperiode2 = Vedtaksperiode(vedtaksperiodeId2, gjeldendeGenerasjon2)
        assertFalse(listOf(vedtaksperiode1, vedtaksperiode2).harAktiveVarsler(28.februar))
    }
    @Test
    fun `har ikke aktive varsler når generasjonene har utbetalingId men ikke fom`() {
        val vedtaksperiodeId1 = UUID.randomUUID()
        val vedtaksperiodeId2 = UUID.randomUUID()
        val gjeldendeGenerasjon1 = generasjon(vedtaksperiodeId1)
        val gjeldendeGenerasjon2 = generasjon(vedtaksperiodeId2)
        val utbetalingId = UUID.randomUUID()
        gjeldendeGenerasjon1.håndterNyUtbetaling(UUID.randomUUID(), utbetalingId, varselRepository)
        gjeldendeGenerasjon2.håndterNyUtbetaling(UUID.randomUUID(), utbetalingId, varselRepository)
        val vedtaksperiode1 = Vedtaksperiode(vedtaksperiodeId1, gjeldendeGenerasjon1)
        val vedtaksperiode2 = Vedtaksperiode(vedtaksperiodeId2, gjeldendeGenerasjon2)
        assertFalse(listOf(vedtaksperiode1, vedtaksperiode2).harAktiveVarsler(28.februar))
    }
    @Test
    fun `har aktive varsler`() {
        val vedtaksperiodeId1 = UUID.randomUUID()
        val vedtaksperiodeId2 = UUID.randomUUID()
        val gjeldendeGenerasjon1 = generasjon(vedtaksperiodeId1)
        val gjeldendeGenerasjon2 = generasjon(vedtaksperiodeId2)
        gjeldendeGenerasjon1.håndterTidslinjeendring(1.januar, 31.januar, 1.januar)
        gjeldendeGenerasjon2.håndterTidslinjeendring(1.februar, 28.februar, 1.februar)
        val vedtaksperiode1 = Vedtaksperiode(vedtaksperiodeId1, gjeldendeGenerasjon1)
        val vedtaksperiode2 = Vedtaksperiode(vedtaksperiodeId2, gjeldendeGenerasjon2)
        gjeldendeGenerasjon2.håndterRegelverksvarsel(UUID.randomUUID(), UUID.randomUUID(), "SB_EX_1", LocalDateTime.now(), varselRepository)
        assertTrue(listOf(vedtaksperiode1, vedtaksperiode2).harAktiveVarsler(28.februar))
    }

    private fun generasjon(vedtaksperiodeId: UUID = UUID.randomUUID()) = Generasjon(
        id = UUID.randomUUID(),
        vedtaksperiodeId = vedtaksperiodeId,
        fom = 1.januar,
        tom = 31.januar,
        skjæringstidspunkt = 1.januar
    )

    private val varselRepository = object : VarselRepository {
        override fun deaktiverFor(vedtaksperiodeId: UUID, generasjonId: UUID, varselkode: String, definisjonId: UUID?) {}
        override fun reaktiverFor(vedtaksperiodeId: UUID, generasjonId: UUID, varselkode: String) {}
        override fun godkjennFor(vedtaksperiodeId: UUID, generasjonId: UUID, varselkode: String, ident: String, definisjonId: UUID?) {}
        override fun avvisFor(vedtaksperiodeId: UUID, generasjonId: UUID, varselkode: String, ident: String, definisjonId: UUID?) {}
        override fun lagreVarsel(id: UUID, generasjonId: UUID, varselkode: String, opprettet: LocalDateTime, vedtaksperiodeId: UUID) {}
        override fun lagreDefinisjon(id: UUID, varselkode: String, tittel: String, forklaring: String?, handling: String?, avviklet: Boolean, opprettet: LocalDateTime) {}
        override fun oppdaterGenerasjonFor(id: UUID, gammelGenerasjonId: UUID, nyGenerasjonId: UUID) {}
    }
}