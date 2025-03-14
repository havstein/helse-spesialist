package no.nav.helse.e2e

import no.nav.helse.spesialist.api.graphql.schema.ApiLovhjemmel
import no.nav.helse.spesialist.api.graphql.schema.ApiOverstyringArbeidsforhold
import no.nav.helse.spesialist.testhjelp.lagOrganisasjonsnummer
import org.junit.jupiter.api.Test
import java.util.UUID

internal class TotrinnsvurderingE2ETest : AbstractE2ETest() {
    @Test
    fun `sak blir trukket til totrinnsvurdering ved overstyring av inntekt og refusjon`() {
        vedtaksløsningenMottarNySøknad()
        spleisOppretterNyBehandling()
        spesialistBehandlerGodkjenningsbehovFremTilOppgave()
        håndterOverstyrInntektOgRefusjon()
        spesialistBehandlerGodkjenningsbehovFremTilOppgave(
            harRisikovurdering = true,
            harOppdatertMetadata = true,
            godkjenningsbehovTestdata = godkjenningsbehovTestdata.copy(utbetalingId = UUID.randomUUID()),
        )
        assertOverstyringer(VEDTAKSPERIODE_ID)
        assertTotrinnsvurdering(2.oppgave(VEDTAKSPERIODE_ID))
    }

    @Test
    fun `sak blir trukket til totrinnsvurdering ved overstyring av arbeidsforhold`() {
        val orgnrGhost = lagOrganisasjonsnummer()
        vedtaksløsningenMottarNySøknad()
        spleisOppretterNyBehandling()
        spesialistBehandlerGodkjenningsbehovFremTilOppgave(
            godkjenningsbehovTestdata =
                godkjenningsbehovTestdata.copy(
                    orgnummereMedRelevanteArbeidsforhold = listOf(orgnrGhost),
                ),
        )
        håndterOverstyrArbeidsforhold(
            overstyrteArbeidsforhold =
                listOf(
                    ApiOverstyringArbeidsforhold(
                        orgnummer = orgnrGhost,
                        deaktivert = true,
                        begrunnelse = "begrunnelse",
                        forklaring = "forklaring",
                        lovhjemmel = ApiLovhjemmel("8-15", null, null, "folketrygdloven", "1998-12-18"),
                    ),
                ),
        )
        spesialistBehandlerGodkjenningsbehovFremTilOppgave(
            harRisikovurdering = true,
            harOppdatertMetadata = true,
            godkjenningsbehovTestdata = godkjenningsbehovTestdata.copy(utbetalingId = UUID.randomUUID()),
        )
        assertOverstyringer(VEDTAKSPERIODE_ID)
        assertTotrinnsvurdering(2.oppgave(VEDTAKSPERIODE_ID))
    }

    @Test
    fun `sak blir trukket til totrinnsvurdering ved overstyring av tidslinje`() {
        vedtaksløsningenMottarNySøknad()
        spleisOppretterNyBehandling()
        spesialistBehandlerGodkjenningsbehovFremTilOppgave()
        håndterOverstyrTidslinje()
        spesialistBehandlerGodkjenningsbehovFremTilOppgave(
            harRisikovurdering = true,
            harOppdatertMetadata = true,
            godkjenningsbehovTestdata = godkjenningsbehovTestdata.copy(utbetalingId = UUID.randomUUID()),
        )
        assertOverstyringer(VEDTAKSPERIODE_ID)
        assertTotrinnsvurdering(2.oppgave(VEDTAKSPERIODE_ID))
    }
}
