package no.nav.helse.e2e

import AbstractE2ETest
import java.util.UUID
import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.Testdata.VEDTAKSPERIODE_ID
import no.nav.helse.modell.varsel.Varsel.Status
import no.nav.helse.modell.varsel.Varselkode
import no.nav.helse.modell.varsel.Varselkode.SB_BO_2
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

internal class BeslutterE2ETest: AbstractE2ETest() {

    @Test
    fun `ingen varsel om beslutteroppgave`() {
        fremTilOppgave()

        assertIngenVarsel(SB_BO_2, VEDTAKSPERIODE_ID)
    }

    @Test
    fun `varsel om beslutteroppgave ved overstyring av dager`() {
        fremTilOppgave()
        håndterOverstyrTidslinje()
        håndterEgenansattløsning()
        håndterVergemålløsning()
        håndterDigitalKontaktinformasjonløsning()
        håndterÅpneOppgaverløsning()

        assertVarsel(SB_BO_2, VEDTAKSPERIODE_ID, Status.AKTIV)
    }

    private fun assertVarsel(varselkode: Varselkode, vedtaksperiodeId: UUID, status: Status) {
        val antallVarsler = sessionOf(dataSource).use { session ->
            @Language("PostgreSQL")
            val query = "SELECT count(*) FROM selve_varsel WHERE kode = ? AND vedtaksperiode_id = ? AND status = ?"
            requireNotNull(
                session.run(
                    queryOf(
                        query,
                        varselkode.name,
                        vedtaksperiodeId,
                        status.name
                    ).map { it.int(1) }.asSingle
                )
            )
        }
        Assertions.assertEquals(1, antallVarsler)
    }

    private fun assertIngenVarsel(varselkode: Varselkode, vedtaksperiodeId: UUID) {
        val antallVarsler = sessionOf(dataSource).use { session ->
            @Language("PostgreSQL")
            val query = "SELECT count(*) FROM selve_varsel WHERE kode = ? AND vedtaksperiode_id = ?"
            requireNotNull(
                session.run(
                    queryOf(
                        query,
                        varselkode.name,
                        vedtaksperiodeId,
                    ).map { it.int(1) }.asSingle
                )
            )
        }
        Assertions.assertEquals(0, antallVarsler)
    }

    private fun fremTilOppgave() {
        håndterSøknad()
        håndterVedtaksperiodeOpprettet()
        håndterGodkjenningsbehov()
        håndterPersoninfoløsning()
        håndterEnhetløsning()
        håndterInfotrygdutbetalingerløsning()
        håndterArbeidsgiverinformasjonløsning()
        håndterArbeidsforholdløsning()
        håndterEgenansattløsning()
        håndterVergemålløsning()
        håndterDigitalKontaktinformasjonløsning()
        håndterÅpneOppgaverløsning()
        håndterRisikovurderingløsning(kanGodkjennesAutomatisk = false)
    }
}