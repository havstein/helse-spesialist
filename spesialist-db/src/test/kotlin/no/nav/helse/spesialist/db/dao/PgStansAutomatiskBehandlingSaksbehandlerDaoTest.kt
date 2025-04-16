package no.nav.helse.spesialist.db.dao

import no.nav.helse.spesialist.db.AbstractDBIntegrationTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PgStansAutomatiskBehandlingSaksbehandlerDaoTest : AbstractDBIntegrationTest() {

    @Test
    fun `kan stanse automatisk behandling`() {
        opprettPerson(FNR)
        stansAutomatiskBehandlingSaksbehandlerDao.lagreStans(FNR)
        assertTrue(stansAutomatiskBehandlingSaksbehandlerDao.erStanset(FNR))
    }

    @Test
    fun `kan oppheve stans av automatisk behandling`() {
        opprettPerson(FNR)
        stansAutomatiskBehandlingSaksbehandlerDao.lagreStans(FNR)
        assertTrue(stansAutomatiskBehandlingSaksbehandlerDao.erStanset(FNR))
        stansAutomatiskBehandlingSaksbehandlerDao.opphevStans(FNR)
        assertFalse(stansAutomatiskBehandlingSaksbehandlerDao.erStanset(FNR))
    }
}