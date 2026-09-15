package no.nav.helse.spesialist.application.tilgangskontroll

import no.nav.helse.spesialist.domain.tilgangskontroll.Brukerrolle
import no.nav.helse.spesialist.domain.tilgangskontroll.Brukerroller
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.*

class TilgangsgrupperTilBrukerrollerTest {
    private val næringsdrivendeGruppeId = UUID.randomUUID()
    private val porteføljestyringGruppeId = UUID.randomUUID()
    private val annenGruppeId1 = UUID.randomUUID()
    private val annenGruppeId2 = UUID.randomUUID()

    private val tilgangsgrupperTilBrukerroller =
        tilgangsgrupperTilBrukerroller(
            næringsdrivendeBeta = listOf(næringsdrivendeGruppeId),
            porteføljestyring = listOf(porteføljestyringGruppeId),
        )

    @Test
    fun `ingen grupper gir ingen roller`() {
        val roller = tilgangsgrupperTilBrukerroller.finnBrukerrollerFraTilgangsgrupper(emptyList())

        assertEquals(Brukerroller(), roller)
    }

    @Test
    fun `har næringsdrivende beta-gruppe blant to grupper`() {
        val grupper = listOf(annenGruppeId1, næringsdrivendeGruppeId)
        val roller = tilgangsgrupperTilBrukerroller.finnBrukerrollerFraTilgangsgrupper(grupper)

        assertEquals(Brukerroller(Brukerrolle.SelvstendigNæringsdrivendeBeta), roller)
    }

    @Test
    fun `har porteføljestyring-gruppe blant flere grupper`() {
        val grupper = listOf(annenGruppeId1, porteføljestyringGruppeId)
        val roller = tilgangsgrupperTilBrukerroller.finnBrukerrollerFraTilgangsgrupper(grupper)

        assertEquals(Brukerroller(Brukerrolle.Porteføljestyring), roller)
    }

    @Test
    fun `tre grupper uten næringsdrivende beta-gruppe gir ingen roller`() {
        val grupper = listOf(annenGruppeId1, annenGruppeId2, UUID.randomUUID())
        val roller = tilgangsgrupperTilBrukerroller.finnBrukerrollerFraTilgangsgrupper(grupper)

        assertEquals(Brukerroller(), roller)
    }
}
