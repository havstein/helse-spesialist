import no.nav.helse.SpeilTilgangsgrupper
import no.nav.helse.TestRapidHelpers.oppgaveId
import no.nav.helse.TestRapidHelpers.siste
import no.nav.helse.db.NotatDao
import no.nav.helse.db.OpptegnelseDao
import no.nav.helse.db.Periodehistorikk
import no.nav.helse.db.ReservasjonDao
import no.nav.helse.db.SaksbehandlerDao
import no.nav.helse.db.TildelingDao
import no.nav.helse.db.TotrinnsvurderingDao
import no.nav.helse.mediator.GodkjenningService
import no.nav.helse.mediator.oppgave.OppgaveDao
import no.nav.helse.mediator.oppgave.OppgaveService
import no.nav.helse.modell.MeldingDao
import no.nav.helse.modell.overstyring.OverstyringDao
import no.nav.helse.modell.totrinnsvurdering.TotrinnsvurderingMediator
import no.nav.helse.rapids_rivers.asLocalDateTime
import no.nav.helse.testEnv
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue

// Snakk med Christian før du lager flere subklasser av denne. Det er mulig vi ønsker å lage (eller allerede har laget?)
// et annet opplegg for å teste samspillet mellom API og selve/mediator/modell
internal abstract class AbstractIntegrationTest : AbstractE2ETest() {
    protected val testRapid = __ikke_bruk_denne
    protected val oppgaveDao = OppgaveDao(dataSource)
    private val reservasjonDao = ReservasjonDao(dataSource)
    private val periodehistorikk = Periodehistorikk(dataSource)
    private val totrinnsvurderingDao = TotrinnsvurderingDao(dataSource)
    private val meldingDao = MeldingDao(dataSource)
    private val saksbehandlerDao = SaksbehandlerDao(dataSource)
    private val notatDao = NotatDao(dataSource)

    private val oppgaveService =
        OppgaveService(
            oppgaveRepository = OppgaveDao(dataSource),
            tildelingRepository = TildelingDao(dataSource),
            reservasjonRepository = reservasjonDao,
            opptegnelseRepository = OpptegnelseDao(dataSource),
            totrinnsvurderingRepository = totrinnsvurderingDao,
            saksbehandlerRepository = SaksbehandlerDao(dataSource),
            rapidsConnection = testRapid,
            meldingRepository = meldingDao,
            tilgangskontroll = TilgangskontrollForTestHarIkkeTilgang,
            tilgangsgrupper = SpeilTilgangsgrupper(testEnv),
        )

    val godkjenningService =
        GodkjenningService(
            dataSource = dataSource,
            oppgaveDao = oppgaveDao,
            meldingDao = meldingDao,
            overstyringDao = OverstyringDao(dataSource),
            rapidsConnection = testRapid,
            oppgaveService = oppgaveService,
            reservasjonDao = reservasjonDao,
            saksbehandlerRepository = saksbehandlerDao,
            notatRepository = notatDao,
            totrinnsvurderingMediator =
                TotrinnsvurderingMediator(
                    totrinnsvurderingDao,
                    oppgaveDao,
                    periodehistorikk,
                    notatDao,
                ),
        )

    protected fun sisteOppgaveId() = testRapid.inspektør.oppgaveId()

    protected fun assertSaksbehandlerløsning(
        godkjent: Boolean,
        automatiskBehandlet: Boolean,
        totrinnsvurdering: Boolean,
        vararg årsakerTilAvvist: String,
    ) {
        val løsning = testRapid.inspektør.siste("saksbehandler_løsning")
        assertTrue(løsning.path("godkjent").isBoolean)
        assertEquals(godkjent, løsning.path("godkjent").booleanValue())
        assertEquals(automatiskBehandlet, løsning.path("automatiskBehandling").booleanValue())
        assertNotNull(løsning.path("fødselsnummer").asText())
        assertNotNull(løsning.get("oppgaveId").asLong())
        assertNotNull(løsning.get("hendelseId").asText())
        assertNotNull(løsning.get("saksbehandlerident").asText())
        assertNotNull(løsning.path("saksbehandleroid").asText())
        assertNotNull(løsning.path("saksbehandlerepost").asText())
        assertNotNull(løsning.path("godkjenttidspunkt").asLocalDateTime())
        assertNotNull(løsning.path("saksbehandler"))
        if (årsakerTilAvvist.isNotEmpty()) {
            val begrunnelser = løsning["begrunnelser"].map { it.asText() }
            assertEquals(begrunnelser, begrunnelser.distinct())
            assertEquals(årsakerTilAvvist.toSet(), begrunnelser.toSet())
        }

        if (totrinnsvurdering) {
            assertNotNull(løsning["beslutter"])
        } else {
            assertNull(løsning["beslutter"])
        }
    }
}
