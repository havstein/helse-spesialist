package no.nav.helse.modell.kommando

import io.mockk.mockk
import io.mockk.verify
import no.nav.helse.modell.VedtakDao
import no.nav.helse.modell.vedtak.SaksbehandlerInntektskilde
import no.nav.helse.modell.vedtak.Saksbehandleroppgavetype
import org.junit.jupiter.api.Test
import java.util.*

internal class PersisterVedtaksperiodetypeCommandTest {

    private val HENDELSE_ID = UUID.randomUUID()
    private val vedtakDao = mockk<VedtakDao>(relaxed = true)

    @Test
    fun `Legger til vedtaksperiodetype`() {
        val type = Saksbehandleroppgavetype.FØRSTEGANGSBEHANDLING
        val inntektskilde = SaksbehandlerInntektskilde.EN_ARBEIDSGIVER
        PersisterVedtaksperiodetypeCommand(HENDELSE_ID, type, inntektskilde, vedtakDao)
            .execute(CommandContext(UUID.randomUUID()))
        verify(exactly = 1) { vedtakDao.leggTilVedtaksperiodetype(HENDELSE_ID, type, inntektskilde) }
    }
}
