package no.nav.helse.db

import DatabaseIntegrationTest
import no.nav.helse.spesialist.api.periodehistorikk.PeriodehistorikkType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PeriodehistorikkTest : DatabaseIntegrationTest() {

    @Test
    fun `lagre periodehistorikk ved hjelp av oppgaveId`() {
        opprettPerson()
        opprettArbeidsgiver()
        opprettSaksbehandler()
        opprettVedtaksperiode()
        opprettOppgave()

        periodehistorikk.lagre(
            PeriodehistorikkType.TOTRINNSVURDERING_TIL_GODKJENNING,
            SAKSBEHANDLER_OID,
            oppgaveId
        )
        val result = periodehistorikkDao.finn(UTBETALING_ID)

        assertEquals(1, result.size)
    }

    @Test
    fun `lagre periodehistorikk ved hjelp av utbetalingId`() {
        opprettPerson()
        opprettArbeidsgiver()
        opprettSaksbehandler()
        opprettVedtaksperiode()
        opprettOppgave()

        periodehistorikk.lagre(
            PeriodehistorikkType.TOTRINNSVURDERING_TIL_GODKJENNING,
            SAKSBEHANDLER_OID,
            UTBETALING_ID
        )
        val result = periodehistorikkDao.finn(UTBETALING_ID)

        assertEquals(1, result.size)
    }
}