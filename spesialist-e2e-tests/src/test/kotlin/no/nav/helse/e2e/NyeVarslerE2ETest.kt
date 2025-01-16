package no.nav.helse.e2e

import org.junit.jupiter.api.Test
import java.util.UUID

internal class NyeVarslerE2ETest : AbstractE2ETest() {

    @Test
    fun `lagrer varsler når vi mottar ny aktivitet i aktivitetsloggen`() {
        vedtaksløsningenMottarNySøknad()
        spleisOppretterNyBehandling()
        håndterVedtaksperiodeNyUtbetaling()
        håndterAktivitetsloggNyAktivitet(varselkoder = listOf("EN_KODE"))

        assertVarsler(VEDTAKSPERIODE_ID, 1)
    }

    @Test
    fun `lagrer varsler når vi mottar flere ny aktivitet i aktivitetsloggen`() {
        vedtaksløsningenMottarNySøknad()
        spleisOppretterNyBehandling()
        håndterVedtaksperiodeNyUtbetaling()
        håndterAktivitetsloggNyAktivitet(varselkoder = listOf("EN_KODE"))
        håndterAktivitetsloggNyAktivitet(varselkoder = listOf("EN_ANNEN_KODE"))

        assertVarsler(VEDTAKSPERIODE_ID, 2)
    }

    @Test
    fun `lagrer flere varsler når vi mottar flere nye aktiviteter i samme aktivitetslogg`() {
        vedtaksløsningenMottarNySøknad()
        spleisOppretterNyBehandling()
        håndterVedtaksperiodeNyUtbetaling()
        håndterAktivitetsloggNyAktivitet(varselkoder = listOf("EN_KODE", "EN_ANNEN_KODE"))

        assertVarsler(VEDTAKSPERIODE_ID, 2)
    }

    @Test
    fun `gammelt avviksvarsel erstattes av nytt avviksvarsel`() {
        vedtaksløsningenMottarNySøknad()
        spleisOppretterNyBehandling()
        håndterVedtaksperiodeNyUtbetaling()
        håndterAktivitetsloggNyAktivitet(varselkoder = listOf("RV_IV_2"))
        håndterAktivitetsloggNyAktivitet(varselkoder = listOf("RV_IV_2"))

        assertVarsler(VEDTAKSPERIODE_ID, 1)
    }

    @Test
    fun `varsler for ulike vedtaksperioder går ikke i beina på hverandre`() {
        val v1 = UUID.randomUUID()
        val v2 = UUID.randomUUID()
        vedtaksløsningenMottarNySøknad()
        spleisOppretterNyBehandling(vedtaksperiodeId = v1)
        håndterVedtaksperiodeNyUtbetaling(vedtaksperiodeId = v1)
        vedtaksløsningenMottarNySøknad()
        spleisOppretterNyBehandling(vedtaksperiodeId = v2)
        håndterVedtaksperiodeNyUtbetaling(vedtaksperiodeId = v2)
        håndterAktivitetsloggNyAktivitet(vedtaksperiodeId = v1, varselkoder = listOf("EN_KODE"))
        håndterAktivitetsloggNyAktivitet(vedtaksperiodeId = v2, varselkoder = listOf("EN_KODE"))

        assertVarsler(v1, 1)
        assertVarsler(v2, 1)
    }
}
