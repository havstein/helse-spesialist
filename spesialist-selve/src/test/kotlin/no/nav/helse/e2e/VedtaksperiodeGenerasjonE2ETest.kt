package no.nav.helse.e2e

import AbstractE2ETest
import java.util.UUID
import no.nav.helse.DbQueries
import no.nav.helse.Testdata.UTBETALING_ID
import no.nav.helse.Testdata.VEDTAKSPERIODE_ID
import no.nav.helse.januar
import no.nav.helse.modell.vedtaksperiode.Generasjon
import no.nav.helse.query
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class VedtaksperiodeGenerasjonE2ETest : AbstractE2ETest() {

    @Test
    fun `Oppretter første generasjon når vedtaksperioden blir opprettet`() {
        håndterSøknad()
        håndterVedtaksperiodeOpprettet()
        assertGenerasjoner(VEDTAKSPERIODE_ID, 1)
    }

    @Test
    fun `Forventer at det eksisterer generasjon for perioden ved vedtaksperiode_endret`() {
        håndterSøknad()
        assertThrows<UninitializedPropertyAccessException> { håndterVedtaksperiodeEndret() }
        assertGenerasjoner(VEDTAKSPERIODE_ID, 0)
    }

    @Test
    fun `Oppretter ikke ny generasjon ved vedtaksperiode_endret dersom det finnes en ubehandlet generasjon fra før av`() {
        håndterSøknad()
        håndterVedtaksperiodeOpprettet()
        håndterVedtaksperiodeEndret()
        assertGenerasjoner(VEDTAKSPERIODE_ID, 1)
    }

    @Test
    fun `Låser gjeldende generasjon når perioden er godkjent og utbetalt`() {
        fremTilSaksbehandleroppgave()
        håndterSaksbehandlerløsning()
        håndterVedtakFattet()
        assertFerdigBehandledeGenerasjoner(VEDTAKSPERIODE_ID, 1)
    }

    @Test
    fun `Oppretter ny generasjon når perioden blir revurdert`() {
        fremTilSaksbehandleroppgave()
        håndterSaksbehandlerløsning()
        håndterVedtakFattet()

        val utbetalingId2 = UUID.randomUUID()
        håndterGodkjenningsbehov(utbetalingId = utbetalingId2, harOppdatertMetainfo = true) //revurdering
        assertGenerasjoner(VEDTAKSPERIODE_ID, 2)
        assertFerdigBehandledeGenerasjoner(VEDTAKSPERIODE_ID, 1)
    }

    @Test
    fun `Kobler til utbetaling når perioden har fått en ny utbetaling`() {
        håndterSøknad()
        håndterVedtaksperiodeOpprettet()
        håndterVedtaksperiodeNyUtbetaling(utbetalingId = UTBETALING_ID)
        assertGenerasjonerMedUtbetaling(VEDTAKSPERIODE_ID, UTBETALING_ID, 1)
    }

    @Test
    fun `Gammel utbetaling erstattes av ny utbetaling dersom perioden ikke er ferdig behandlet`() {
        val gammel = UUID.randomUUID()
        val ny = UUID.randomUUID()
        håndterSøknad()
        håndterVedtaksperiodeOpprettet()
        håndterVedtaksperiodeNyUtbetaling(utbetalingId = gammel)
        håndterVedtaksperiodeNyUtbetaling(utbetalingId = ny)
        assertGenerasjonerMedUtbetaling(VEDTAKSPERIODE_ID, gammel, 0)
        assertGenerasjonerMedUtbetaling(VEDTAKSPERIODE_ID, ny, 1)
    }

    @Test
    fun `fjerner knytning til utbetaling når utbetalingen blir forkastet`() {
        fremTilSaksbehandleroppgave(1.januar, 31.januar, utbetalingId = UTBETALING_ID)
        assertGenerasjonerMedUtbetaling(VEDTAKSPERIODE_ID, UTBETALING_ID, 1)
        håndterUtbetalingForkastet()
        assertGenerasjonerMedUtbetaling(VEDTAKSPERIODE_ID, UTBETALING_ID, 0)
    }

    @Test
    fun `Flytter aktive varsler for auu`() {
        håndterSøknad()
        håndterVedtaksperiodeOpprettet()
        håndterVedtakFattet()
        håndterAktivitetsloggNyAktivitet(varselkoder = listOf("RV_IM_1"))

        val utbetalingId = UUID.randomUUID()
        håndterVedtaksperiodeEndret()
        håndterVedtaksperiodeNyUtbetaling(utbetalingId = utbetalingId)
        assertGenerasjoner(VEDTAKSPERIODE_ID, 2)
        assertGenerasjonHarVarsler(VEDTAKSPERIODE_ID, utbetalingId, 1)
    }

    @Test
    fun `Flytter aktive varsler for vanlige generasjoner`() {
        fremTilSaksbehandleroppgave()
        håndterSaksbehandlerløsning()
        håndterVedtakFattet()
        håndterAktivitetsloggNyAktivitet(varselkoder = listOf("RV_IM_1"))

        val utbetalingId = UUID.randomUUID()
        håndterVedtaksperiodeEndret()
        håndterVedtaksperiodeNyUtbetaling(utbetalingId = utbetalingId)
        assertGenerasjoner(VEDTAKSPERIODE_ID, 2)
        assertGenerasjonHarVarsler(VEDTAKSPERIODE_ID, utbetalingId, 1)
    }

    private val dbQueries = DbQueries(dataSource)

    private fun assertGenerasjonHarVarsler(vedtaksperiodeId: UUID, utbetalingId: UUID, forventetAntall: Int) {
        val antall = dbQueries.run {
            query(
                """
                    SELECT COUNT(1) FROM selve_vedtaksperiode_generasjon svg
                    INNER JOIN selve_varsel sv on svg.id = sv.generasjon_ref
                    WHERE svg.vedtaksperiode_id = :vedtaksperiodeId AND utbetaling_id = :utbetalingId
                """.trimIndent(), "vedtaksperiodeId" to vedtaksperiodeId, "utbetalingId" to utbetalingId
            ).single { it.int(1) }
        }
        assertEquals(forventetAntall, antall) { "Forventet $forventetAntall varsler for $vedtaksperiodeId, $utbetalingId, fant $antall" }
    }

    private fun assertGenerasjoner(vedtaksperiodeId: UUID, forventetAntall: Int) {
        val antall = dbQueries.run {
            query(
                "SELECT COUNT(1) FROM selve_vedtaksperiode_generasjon WHERE vedtaksperiode_id = :vedtaksperiodeId",
                "vedtaksperiodeId" to vedtaksperiodeId
            ).single { it.int(1) }
        }
        assertEquals(forventetAntall, antall) { "Forventet $forventetAntall generasjoner for $vedtaksperiodeId, fant $antall" }
    }

    private fun assertFerdigBehandledeGenerasjoner(vedtaksperiodeId: UUID, forventetAntall: Int) {
        val antall = dbQueries.run {
            query("SELECT COUNT(1) FROM selve_vedtaksperiode_generasjon WHERE vedtaksperiode_id = :vedtaksperiodeId AND tilstand = '${Generasjon.Låst.navn()}'",
                "vedtaksperiodeId" to vedtaksperiodeId).single { it.int(1) }
        }
        assertEquals(forventetAntall, antall) { "Forventet $forventetAntall ferdig behandlede generasjoner for $vedtaksperiodeId, fant $antall" }
    }

    private fun assertGenerasjonerMedUtbetaling(vedtaksperiodeId: UUID, utbetalingId: UUID, forventetAntall: Int) {
        val antall = dbQueries.run {
            query("SELECT COUNT(1) FROM selve_vedtaksperiode_generasjon WHERE vedtaksperiode_id = :vedtaksperiodeId AND utbetaling_id = :utbetalingId".trimIndent(), "vedtaksperiodeId" to vedtaksperiodeId, "utbetalingId" to utbetalingId).single { it.int(1) }
        }
        assertEquals(forventetAntall, antall) { "Forventet $forventetAntall generasjoner med utbetalingId=$utbetalingId for $vedtaksperiodeId, fant $antall" }
    }
}
