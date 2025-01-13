package no.nav.helse.modell

import no.nav.helse.DatabaseIntegrationTest
import io.mockk.every
import io.mockk.mockk
import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.mediator.meldinger.Testmeldingfabrikk
import no.nav.helse.modell.overstyring.OverstyringIgangsatt
import no.nav.helse.modell.vedtaksperiode.Godkjenningsbehov
import no.nav.helse.modell.vedtaksperiode.vedtak.Saksbehandlerløsning
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import java.util.UUID

internal class MeldingDaoTest : DatabaseIntegrationTest() {
    private val godkjenningsbehov: Godkjenningsbehov = mockGodkjenningsbehov()

    private val saksbehandlerløsning: Saksbehandlerløsning = mockSaksbehandlerløsning()

    @Test
    fun `finn siste igangsatte overstyring om den er korrigert søknad`() {
        val fødselsnummer = FNR
        val overstyringIgangsatt = mockOverstyringIgangsatt(fødselsnummer, listOf(VEDTAKSPERIODE), "KORRIGERT_SØKNAD")

        val overstyringIgangsattForAnnenVedtaksperiode = mockOverstyringIgangsatt(fødselsnummer, listOf(VEDTAKSPERIODE), "SYKDOMSTIDSLINJE")

        meldingDao.lagre(overstyringIgangsatt)
        meldingDao.lagre(overstyringIgangsattForAnnenVedtaksperiode)
        assertNull(meldingDao.sisteOverstyringIgangsattOmKorrigertSøknad(fødselsnummer, VEDTAKSPERIODE))

        meldingDao.lagre(mockOverstyringIgangsatt(fødselsnummer, listOf(VEDTAKSPERIODE), "KORRIGERT_SØKNAD"))
        assertNotNull(meldingDao.sisteOverstyringIgangsattOmKorrigertSøknad(fødselsnummer, VEDTAKSPERIODE))
    }

    @Test
    fun `finn antall korrigerte søknader`() {
        meldingDao.opprettAutomatiseringKorrigertSøknad(VEDTAKSPERIODE, UUID.randomUUID())
        val actual = meldingDao.finnAntallAutomatisertKorrigertSøknad(VEDTAKSPERIODE)
        assertEquals(1, actual)
    }

    @Test
    fun `finn ut om automatisering av korrigert søknad allerede er håndtert`() {
        meldingDao.opprettAutomatiseringKorrigertSøknad(VEDTAKSPERIODE, HENDELSE_ID)
        val håndtert = meldingDao.erKorrigertSøknadAutomatiskBehandlet(HENDELSE_ID)
        assertTrue(håndtert)
    }

    @Test
    fun `lagrer og finner hendelser`() {
        meldingDao.lagre(godkjenningsbehov)
        val actual = meldingDao.finn(HENDELSE_ID)
            ?: fail { "Forventet å finne en hendelse med id $HENDELSE_ID" }
        assertEquals(FNR, actual.fødselsnummer())
    }

    @Test
    fun `lagrer og finner saksbehandlerløsning`() {
        meldingDao.lagre(saksbehandlerløsning)
        val actual = meldingDao.finn(HENDELSE_ID)
            ?: fail { "Forventet å finne en hendelse med id $HENDELSE_ID" }
        assertEquals(FNR, actual.fødselsnummer())
    }

    @Test
    fun `lagrer hendelser inkludert kobling til vedtak`() {
        opprettPerson()
        opprettArbeidsgiver()
        opprettVedtaksperiode()

        meldingDao.lagre(godkjenningsbehov)
        assertEquals(VEDTAKSPERIODE, finnKobling())
    }

    private fun mockOverstyringIgangsatt(fødselsnummer: String, berørtePeriodeIder: List<UUID>, årsak: String): OverstyringIgangsatt {
        return mockk<OverstyringIgangsatt>(relaxed = true) {
            every { id } returns UUID.randomUUID()
            every { fødselsnummer() } returns fødselsnummer
            every { berørteVedtaksperiodeIder } returns berørtePeriodeIder
            every { toJson() } returns Testmeldingfabrikk.lagOverstyringIgangsatt(
                fødselsnummer = fødselsnummer,
                berørtePerioder = berørtePeriodeIder.map {
                    mapOf(
                        "vedtaksperiodeId" to "$it",
                        "periodeFom" to "2022-01-01",
                        "orgnummer" to "orgnr",
                    )
                },
                årsak = årsak,
            )
        }
    }

    private fun mockGodkjenningsbehov(): Godkjenningsbehov {
        return mockk<Godkjenningsbehov>(relaxed = true) {
            every { id } returns HENDELSE_ID
            every { fødselsnummer() } returns FNR
            every { vedtaksperiodeId() } returns VEDTAKSPERIODE
            every { toJson() } returns Testmeldingfabrikk.lagGodkjenningsbehov(AKTØR, FNR, VEDTAKSPERIODE)
        }
    }

    private fun mockSaksbehandlerløsning(): Saksbehandlerløsning {
        return mockk<Saksbehandlerløsning>(relaxed = true) {
            every { id } returns HENDELSE_ID
            every { fødselsnummer() } returns FNR
            every { toJson() } returns Testmeldingfabrikk.lagSaksbehandlerløsning(FNR)
        }
    }

    private fun finnKobling(hendelseId: UUID = HENDELSE_ID) = sessionOf(dataSource).use { session ->
        session.run(
            queryOf(
                "SELECT vedtaksperiode_id FROM vedtaksperiode_hendelse WHERE hendelse_ref = ?", hendelseId
            ).map { UUID.fromString(it.string(1)) }.asSingle
        )
    }

}
