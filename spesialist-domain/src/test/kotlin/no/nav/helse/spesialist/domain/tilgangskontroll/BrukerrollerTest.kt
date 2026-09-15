package no.nav.helse.spesialist.domain.tilgangskontroll

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BrukerrollerTest {
    @Test
    fun `rollekrav er oppfylt hvis det ikke kreves noen roller`() {
        val påkrevdeRoller = Brukerroller.INGEN

        assertTrue(Brukerroller().harTilstrekkeligRolle(påkrevdeRoller))
        assertTrue(Brukerroller(Brukerrolle.Beslutter).harTilstrekkeligRolle(påkrevdeRoller))
    }

    @Test
    fun `rollekrav er oppfylt om saksbehandler har minst en av rollene og ikke ellers`() {
        val enSaksbehandlersRoller = Brukerroller(Brukerrolle.Beslutter)

        assertTrue(enSaksbehandlersRoller.harTilstrekkeligRolle(Brukerroller(Brukerrolle.Beslutter)))
        assertTrue(enSaksbehandlersRoller.harTilstrekkeligRolle(Brukerroller(Brukerrolle.Kode7, Brukerrolle.Beslutter)))
        assertFalse(enSaksbehandlersRoller.harTilstrekkeligRolle(Brukerroller(Brukerrolle.Kode7)))
    }
}
