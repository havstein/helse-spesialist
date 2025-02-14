package no.nav.helse.modell

import DatabaseIntegrationTest
import no.nav.helse.spesialist.api.periodehistorikk.PeriodehistorikkType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID

class PeriodehistorikkApiDaoTest : DatabaseIntegrationTest() {

    @Test
    fun `lagre og finn periodehistorikk`() {
        val periodeId = UUID.randomUUID()
        opprettSaksbehandler()

        periodehistorikk.lagre(
            PeriodehistorikkType.TOTRINNSVURDERING_TIL_GODKJENNING,
            SAKSBEHANDLER_OID,
            periodeId
        )
        val result = periodehistorikkDao.finn(periodeId)

        assertEquals(1, result.size)
    }

}
