package no.nav.helse.modell.saksbehandler

import AbstractEndToEndTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.util.*

internal class SaksbehandlerDaoTest : AbstractEndToEndTest() {

    private var saksbehandlere: List<SaksbehandlerDto> = emptyList()

    @Test
    fun `oppretter og finner saksbehandler`() {
        saksbehandlerDao.opprettSaksbehandler(SAKSBEHANDLER_OID, "Navn Navnesen", SAKSBEHANDLEREPOST)
        saksbehandlere = saksbehandlerDao.finnSaksbehandler(SAKSBEHANDLER_OID)
        assertSaksbehandler(SAKSBEHANDLER_OID, "Navn Navnesen", SAKSBEHANDLEREPOST)
    }

    private fun assertSaksbehandler(oid: UUID, navn: String, epost: String) {
        val saksbehandler = saksbehandlere.firstOrNull { it.oid == oid }
        assertNotNull(saksbehandler)
        assertEquals(navn, saksbehandler?.navn)
        assertEquals(epost, saksbehandler?.epost)
    }
}
