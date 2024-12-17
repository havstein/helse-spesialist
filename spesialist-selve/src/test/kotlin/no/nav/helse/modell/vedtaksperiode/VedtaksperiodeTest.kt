package no.nav.helse.modell.vedtaksperiode

import io.mockk.mockk
import no.nav.helse.februar
import no.nav.helse.januar
import no.nav.helse.modell.person.vedtaksperiode.SpleisBehandling
import no.nav.helse.modell.person.vedtaksperiode.SpleisVedtaksperiode
import no.nav.helse.modell.person.vedtaksperiode.TilstandDto
import no.nav.helse.modell.person.vedtaksperiode.Varsel
import no.nav.helse.modell.vedtak.AvsluttetUtenVedtak
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime
import java.util.UUID

class VedtaksperiodeTest {
    @Test
    fun `ignorerer behandling som ikke er relevant for vedtaksperioden`() {
        val vedtaksperiodeId = UUID.randomUUID()
        val annenVedtaksperiodeId = UUID.randomUUID()
        val vedtaksperiode = nyVedtaksperiode(vedtaksperiodeId)
        vedtaksperiode.vedtakFattet(UUID.randomUUID())

        vedtaksperiode.nySpleisBehandling(nySpleisBehandling(annenVedtaksperiodeId))

        val antallGenerasjoner = vedtaksperiode.toDto().behandlinger.size
        assertEquals(1, antallGenerasjoner) // Det har ikke blitt opprettet noen ny generasjon for denne vedtaksperioden
    }

    @Test
    fun `oppretter generasjon når Spleis forteller om ny behandling uavhengig av tilstand på tidligere generasjon - Spleis er master for behandlinger`() {
        val vedtaksperiodeId = UUID.randomUUID()
        val vedtaksperiode = nyVedtaksperiode(vedtaksperiodeId)

        vedtaksperiode.nySpleisBehandling(nySpleisBehandling(vedtaksperiodeId))

        val generasjoner = vedtaksperiode.toDto().behandlinger
        assertEquals(TilstandDto.VidereBehandlingAvklares, generasjoner.first().tilstand) // tilstand på tidligere generasjon er ikke avgjørende for om vi oppretter generasjon
        assertEquals(2, generasjoner.size) // Det har ikke blitt opprettet noen ny generasjon for denne vedtaksperioden
    }

    @Test
    fun `vedtaksperioden mottar nye varsler`() {
        val vedtaksperiodeId = UUID.randomUUID()
        val vedtaksperiode = nyVedtaksperiode(vedtaksperiodeId)
        vedtaksperiode.nyeVarsler(listOf(nyttVarsel(vedtaksperiodeId)))
        val gjeldendeGenerasjon = vedtaksperiode.toDto().behandlinger.single()
        assertEquals(1, gjeldendeGenerasjon.varsler.size)
    }

    @Test
    fun `vedtaksperioden ignorerer varsler som ikke er relevante for den`() {
        val vedtaksperiodeId = UUID.randomUUID()
        val vedtaksperiode = nyVedtaksperiode(vedtaksperiodeId)
        vedtaksperiode.nyeVarsler(listOf(nyttVarsel(UUID.randomUUID())))
        val gjeldendeGenerasjon = vedtaksperiode.toDto().behandlinger.single()
        assertEquals(0, gjeldendeGenerasjon.varsler.size)
    }

    @Test
    fun `oppretter ny generasjon om spesialist mottar ny behandling når gjeldende generasjon er avsluttet`() {
        val vedtaksperiodeId = UUID.randomUUID()
        val spleisBehandlingId = UUID.randomUUID()
        val vedtaksperiode = nyVedtaksperiode(vedtaksperiodeId, spleisBehandlingId)
        vedtaksperiode.nyUtbetaling(UUID.randomUUID())
        vedtaksperiode.vedtakFattet(spleisBehandlingId)
        vedtaksperiode.nySpleisBehandling(nySpleisBehandling(vedtaksperiodeId))
        val generasjoner = vedtaksperiode.toDto().behandlinger
        val nyGjeldendeGenerasjon = generasjoner.last()
        assertEquals(TilstandDto.VidereBehandlingAvklares, nyGjeldendeGenerasjon.tilstand)
        assertEquals(2, generasjoner.size)
    }

    @Test
    fun `oppretter ny generasjon om spesialist mottar ny behandling når gjeldende generasjon er AUU`() {
        val vedtaksperiodeId = UUID.randomUUID()
        val spleisBehandlingId = UUID.randomUUID()
        val vedtaksperiode = nyVedtaksperiode(vedtaksperiodeId, spleisBehandlingId)
        vedtaksperiode.avsluttetUtenVedtak(
            person = mockk(relaxed = true),
            avsluttetUtenVedtak = AvsluttetUtenVedtak(vedtaksperiodeId, emptyList(), spleisBehandlingId),
        )
        vedtaksperiode.nySpleisBehandling(nySpleisBehandling(vedtaksperiodeId))
        val generasjoner = vedtaksperiode.toDto().behandlinger
        val nyGjeldendeGenerasjon = generasjoner.last()
        assertEquals(TilstandDto.VidereBehandlingAvklares, nyGjeldendeGenerasjon.tilstand)
        assertEquals(2, generasjoner.size)
    }

    @Test
    fun `ny vedtaksperiode opprettes med spleisBehandlingId`() {
        val vedtaksperiodeId = UUID.randomUUID()
        val vedtaksperiode = nyVedtaksperiode(vedtaksperiodeId)
        val dto = vedtaksperiode.toDto()
        assertNotNull(dto.behandlinger.single().spleisBehandlingId)
    }

    @Test
    fun `oppdater gjeldende generasjon dersom gjeldende generasjon er klar til behandling ved godkjenningsbehov`() {
        val vedtaksperiodeId = UUID.randomUUID()
        val vedtaksperiode = nyVedtaksperiode(vedtaksperiodeId)
        vedtaksperiode.nyUtbetaling(UUID.randomUUID())
        vedtaksperiode.nyttGodkjenningsbehov(
            listOf(SpleisVedtaksperiode(vedtaksperiodeId, UUID.randomUUID(), 1.februar, 28.februar, 1.februar)),
        )
        val dto = vedtaksperiode.toDto()
        assertEquals(1, dto.behandlinger.size)
        assertEquals(1.februar, dto.behandlinger.single().fom)
        assertEquals(28.februar, dto.behandlinger.single().tom)
        assertEquals(1.februar, dto.behandlinger.single().skjæringstidspunkt)
    }

    @Test
    fun `oppdater gjeldende generasjon dersom gjeldende generasjon avventer videre avklaring ved godkjenningsbehov`() {
        val vedtaksperiodeId = UUID.randomUUID()
        val vedtaksperiode = nyVedtaksperiode(vedtaksperiodeId)
        vedtaksperiode.nyttGodkjenningsbehov(
            listOf(SpleisVedtaksperiode(vedtaksperiodeId, UUID.randomUUID(), 1.februar, 28.februar, 1.februar)),
        )
        val dto = vedtaksperiode.toDto()
        assertEquals(1, dto.behandlinger.size)
        assertEquals(1.februar, dto.behandlinger.single().fom)
        assertEquals(28.februar, dto.behandlinger.single().tom)
        assertEquals(1.februar, dto.behandlinger.single().skjæringstidspunkt)
    }

    @Test
    fun `ikke ny generasjon dersom gjeldende generasjon er avsluttet med vedtak og godkjenningsbehovet inneholder behandling for perioden som er siste gjeldende i Spesialist`() {
        val vedtaksperiodeId = UUID.randomUUID()
        val spleisBehandlingId = UUID.randomUUID()
        val vedtaksperiode = nyVedtaksperiode(vedtaksperiodeId, spleisBehandlingId)
        vedtaksperiode.nyUtbetaling(UUID.randomUUID())
        vedtaksperiode.vedtakFattet(UUID.randomUUID())
        vedtaksperiode.nyttGodkjenningsbehov(
            listOf(SpleisVedtaksperiode(vedtaksperiodeId, spleisBehandlingId, 1.januar, 31.januar, 1.januar)),
        )
        val dto = vedtaksperiode.toDto()
        assertEquals(1, dto.behandlinger.size)
    }

    @Test
    fun `ikke ny generasjon dersom gjeldende generasjon er avsluttet uten vedtak og godkjenningsbehovet inneholder behandling for perioden som er siste gjeldende i Spesialist`() {
        val vedtaksperiodeId = UUID.randomUUID()
        val spleisBehandlingId = UUID.randomUUID()
        val vedtaksperiode = nyVedtaksperiode(vedtaksperiodeId, spleisBehandlingId)
        vedtaksperiode.avsluttetUtenVedtak(mockk(relaxed = true), AvsluttetUtenVedtak(vedtaksperiodeId, emptyList(), spleisBehandlingId))
        vedtaksperiode.nyttGodkjenningsbehov(
            listOf(SpleisVedtaksperiode(vedtaksperiodeId, spleisBehandlingId, 1.januar, 31.januar, 1.januar)),
        )
        val dto = vedtaksperiode.toDto()
        assertEquals(1, dto.behandlinger.size)
    }

    @Test
    fun `ikke ny generasjon dersom gjeldende generasjon er avsluttet uten vedtak med varsler og godkjenningsbehovet inneholder behandling for perioden som er siste gjeldende i Spesialist`() {
        val vedtaksperiodeId = UUID.randomUUID()
        val spleisBehandlingId = UUID.randomUUID()
        val vedtaksperiode = nyVedtaksperiode(vedtaksperiodeId, spleisBehandlingId)
        vedtaksperiode.nyeVarsler(listOf(Varsel(UUID.randomUUID(), "RV_IM_1", LocalDateTime.now(), vedtaksperiodeId)))
        vedtaksperiode.avsluttetUtenVedtak(mockk(relaxed = true), AvsluttetUtenVedtak(vedtaksperiodeId, emptyList(), spleisBehandlingId))

        vedtaksperiode.nyttGodkjenningsbehov(
            listOf(SpleisVedtaksperiode(vedtaksperiodeId, spleisBehandlingId, 1.januar, 31.januar, 1.januar)),
        )
        val dto = vedtaksperiode.toDto()
        assertEquals(1, dto.behandlinger.size)
        assertEquals(TilstandDto.AvsluttetUtenVedtakMedVarsler, dto.behandlinger[0].tilstand)
    }

    @Test
    fun `Kan ikke gjenopprette vedtaksperiode uten generasjoner`() {
        assertThrows<IllegalStateException> {
            Vedtaksperiode.gjenopprett("987654321", UUID.randomUUID(), false, emptyList())
        }
    }

    private fun nySpleisBehandling(
        vedtaksperiodeId: UUID,
        spleisBehandlingId: UUID = UUID.randomUUID(),
    ) = SpleisBehandling("987654321", vedtaksperiodeId, spleisBehandlingId, 1.januar, 31.januar)

    private fun nyttVarsel(
        vedtaksperiodeId: UUID,
        varselkode: String = "SB_EX_1",
    ) = Varsel(UUID.randomUUID(), varselkode, LocalDateTime.now(), vedtaksperiodeId)

    private fun nyVedtaksperiode(
        vedtaksperiodeId: UUID,
        spleisBehandlingId: UUID = UUID.randomUUID(),
    ) = Vedtaksperiode.nyVedtaksperiode(nySpleisBehandling(vedtaksperiodeId, spleisBehandlingId))
}
