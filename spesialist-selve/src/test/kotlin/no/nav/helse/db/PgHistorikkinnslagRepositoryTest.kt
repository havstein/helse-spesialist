package no.nav.helse.db

import DatabaseIntegrationTest
import no.nav.helse.modell.periodehistorikk.HistorikkinnslagDto
import no.nav.helse.modell.saksbehandler.SaksbehandlerDto
import no.nav.helse.spesialist.api.periodehistorikk.PeriodehistorikkType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PgHistorikkinnslagRepositoryTest : DatabaseIntegrationTest() {

    @Test
    fun `lagre periodehistorikk ved hjelp av oppgaveId`() {
        opprettPerson()
        opprettArbeidsgiver()
        opprettSaksbehandler()
        opprettVedtaksperiode()
        opprettOppgave()

        val saksbehandler = SaksbehandlerDto(SAKSBEHANDLER_EPOST, SAKSBEHANDLER_OID, SAKSBEHANDLER_NAVN, SAKSBEHANDLER_IDENT)
        val historikkinnslag = HistorikkinnslagDto.fjernetFraPåVentInnslag(saksbehandler)

        pgHistorikkinnslagRepository.lagre(historikkinnslag, oppgaveId)
        val result = periodehistorikkApiDao.finn(UTBETALING_ID)

        assertEquals(1, result.size)
    }

    @Test
    fun `lagre periodehistorikk ved hjelp av utbetalingId`() {
        opprettPerson()
        opprettArbeidsgiver()
        opprettSaksbehandler()
        opprettVedtaksperiode()
        opprettOppgave()

        pgHistorikkinnslagRepository.lagre(
            PeriodehistorikkType.TOTRINNSVURDERING_TIL_GODKJENNING,
            SAKSBEHANDLER_OID,
            UTBETALING_ID
        )
        val result = periodehistorikkApiDao.finn(UTBETALING_ID)

        assertEquals(1, result.size)
    }
}
