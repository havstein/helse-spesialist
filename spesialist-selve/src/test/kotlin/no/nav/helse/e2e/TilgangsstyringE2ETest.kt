package no.nav.helse.e2e

import AbstractE2ETestV2
import graphql.GraphQLError
import graphql.schema.DataFetchingEnvironment
import io.mockk.every
import io.mockk.mockk
import java.util.UUID
import kotlinx.coroutines.runBlocking
import no.nav.helse.Testdata.AKTØR
import no.nav.helse.Testdata.FØDSELSNUMMER
import no.nav.helse.Testdata.ORGNR
import no.nav.helse.Testdata.SNAPSHOT
import no.nav.helse.Testdata.UTBETALING_ID
import no.nav.helse.Testdata.VEDTAKSPERIODE_ID
import no.nav.helse.januar
import no.nav.helse.spesialist.api.SaksbehandlerTilganger
import no.nav.helse.spesialist.api.arbeidsgiver.ArbeidsgiverApiDao
import no.nav.helse.spesialist.api.egenAnsatt.EgenAnsattApiDao
import no.nav.helse.spesialist.api.graphql.query.PersonQuery
import no.nav.helse.spesialist.api.notat.NotatDao
import no.nav.helse.spesialist.api.oppgave.OppgaveApiDao
import no.nav.helse.spesialist.api.oppgave.Oppgavestatus.AvventerSaksbehandler
import no.nav.helse.spesialist.api.overstyring.OverstyringApiDao
import no.nav.helse.spesialist.api.periodehistorikk.PeriodehistorikkDao
import no.nav.helse.spesialist.api.person.PersonApiDao
import no.nav.helse.spesialist.api.risikovurdering.RisikovurderingApiDao
import no.nav.helse.spesialist.api.snapshot.SnapshotApiDao
import no.nav.helse.spesialist.api.snapshot.SnapshotMediator
import no.nav.helse.spesialist.api.tildeling.TildelingDao
import no.nav.helse.spesialist.api.totrinnsvurdering.TotrinnsvurderingApiDao
import no.nav.helse.spesialist.api.varsel.ApiVarselRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class TilgangsstyringE2ETest : AbstractE2ETestV2() {

    @Test
    fun `Gir 404 når det ikke er noe å vise ennå, selv om saksbehandler har tilgang`() {
        settOppDefaultDataOgTilganger()

        sendMeldingerOppTilEgenAnsatt()

        assertKanIkkeHentePerson("Finner ikke data for person med fødselsnummer ")

        håndterEgenansattløsning(erEgenAnsatt = true)
        assertKanIkkeHentePerson("Finner ikke data for person med fødselsnummer ")

        sendFramTilOppgave()
        assertSaksbehandleroppgaveBleIkkeOpprettet()

        saksbehandlertilgangTilSkjermede(harTilgang = true)
        assertKanIkkeHentePerson("Finner ikke data for person med fødselsnummer ")
    }

    @Test
    fun `Kan hente person om den er klar til visning`() {
        settOppDefaultDataOgTilganger()

        sendMeldingerOppTilEgenAnsatt()

        assertKanIkkeHentePerson("Finner ikke data for person med fødselsnummer ")
        håndterEgenansattløsning(erEgenAnsatt = false)
        assertKanIkkeHentePerson("Finner ikke data for person med fødselsnummer ")
        sendFramTilOppgave()
        assertSaksbehandleroppgave(oppgavestatus = AvventerSaksbehandler)
        assertKanHentePerson()
    }

    @Test
    fun `Kan ikke hente person hvis tilgang mangler`() {
        settOppDefaultDataOgTilganger()

        sendMeldingerOppTilEgenAnsatt()

        assertKanIkkeHentePerson("Finner ikke data for person med fødselsnummer ")
        håndterEgenansattløsning(erEgenAnsatt = false)
        assertKanIkkeHentePerson("Finner ikke data for person med fødselsnummer ")
        sendFramTilOppgave()
        assertSaksbehandleroppgave(oppgavestatus = AvventerSaksbehandler)

        assertKanHentePerson()

        håndterEndretSkjermetinfo(FØDSELSNUMMER, true)

        assertKanIkkeHentePerson("Har ikke tilgang til person med fødselsnummer ")

        saksbehandlertilgangTilSkjermede(harTilgang = true)

        assertKanHentePerson()
    }

    private fun sendMeldingerOppTilEgenAnsatt(): UUID {
        håndterSøknad(AKTØR, FØDSELSNUMMER, ORGNR)
        val skjæringstidspunkt = 1.januar
        val fom = 1.januar
        val tom = 31.januar
        håndterVedtaksperiodeOpprettet()
        håndterVedtaksperiodeNyUtbetaling()
        håndterUtbetalingEndret()
        håndterAktivitetsloggNyAktivitet(varselkoder = listOf("RV_IM_1"))
        val godkjenningsmeldingId = sendGodkjenningsbehov(
            AKTØR,
            FØDSELSNUMMER,
            ORGNR,
            VEDTAKSPERIODE_ID,
            UTBETALING_ID,
            periodeFom = fom,
            periodeTom = tom,
            skjæringstidspunkt = skjæringstidspunkt
        )
        håndterPersoninfoløsning()
        håndterEnhetløsning()
        håndterInfotrygdutbetalingerløsning()
        håndterArbeidsgiverinformasjonløsning()
        håndterArbeidsforholdløsning()
        return godkjenningsmeldingId
    }

    private fun sendFramTilOppgave() {
        håndterVergemålløsning()
        håndterÅpneOppgaverløsning()
        håndterRisikovurderingløsning()
    }

    private fun fetchPerson() = runBlocking { personQuery.person(FØDSELSNUMMER, null, dataFetchingEnvironment) }

    private fun assertKanIkkeHentePerson(feilmelding: String) {
        fetchPerson().let { response ->
            assertFeilmelding(feilmelding, response.errors)
            assertNull(response.data)
        }
    }

    private fun assertKanHentePerson() {
        fetchPerson().let { response ->
            assertEquals(emptyList<GraphQLError>(), response.errors)
            assertNotNull(response.data)
        }
    }

    private fun settOppDefaultDataOgTilganger() {
        every { snapshotClient.hentSnapshot(FØDSELSNUMMER) } returns SNAPSHOT
        every { dataFetchingEnvironment.graphQlContext.get<String>("saksbehandlerNavn") } returns "saksbehandler"
        saksbehandlertilgangTilSkjermede(harTilgang = false)
    }

    private fun saksbehandlertilgangTilSkjermede(harTilgang: Boolean) {
        every { dataFetchingEnvironment.graphQlContext.get<SaksbehandlerTilganger>("tilganger") } returns mockk(relaxed = true) {
            every { harTilgangTilSkjermedePersoner() } returns harTilgang
        }
    }

    private val dataFetchingEnvironment = mockk<DataFetchingEnvironment>(relaxed = true)

    private val personQuery = PersonQuery(
        personApiDao = PersonApiDao(dataSource),
        egenAnsattApiDao = EgenAnsattApiDao(dataSource),
        tildelingDao = TildelingDao(dataSource),
        arbeidsgiverApiDao = ArbeidsgiverApiDao(dataSource),
        overstyringApiDao = OverstyringApiDao(dataSource),
        risikovurderingApiDao = RisikovurderingApiDao(dataSource),
        varselRepository = ApiVarselRepository(dataSource),
        oppgaveApiDao = OppgaveApiDao(dataSource),
        periodehistorikkDao = PeriodehistorikkDao(dataSource),
        notatDao = NotatDao(dataSource),
        totrinnsvurderingApiDao = TotrinnsvurderingApiDao(dataSource),
        snapshotMediator = SnapshotMediator(SnapshotApiDao(dataSource), mockk(relaxed = true)),
        reservasjonClient = mockk(relaxed = true),
    )

    companion object {
        private fun assertFeilmelding(feilmelding: String, errors: List<GraphQLError>) {
            assertTrue(errors.any { it.message.contains(feilmelding) }) {
                "Forventet at $errors skulle inneholde \"$feilmelding\""
            }
        }
    }
}
