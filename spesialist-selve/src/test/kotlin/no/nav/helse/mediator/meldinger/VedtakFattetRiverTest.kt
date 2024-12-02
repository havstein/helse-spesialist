package no.nav.helse.mediator.meldinger

import io.mockk.mockk
import io.mockk.verify
import no.nav.helse.kafka.VedtakFattetRiver
import no.nav.helse.medRivers
import no.nav.helse.mediator.MeldingMediator
import no.nav.helse.modell.vedtaksperiode.vedtak.VedtakFattet
import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

internal class VedtakFattetRiverTest {

    private val mediator = mockk<MeldingMediator>(relaxed = true)
    private val testRapid = TestRapid().medRivers(VedtakFattetRiver(mediator))

    @Test
    fun `Leser inn vedtak_fattet-event`() {
        testRapid.sendTestMessage(event())
        verify(exactly = 1) { mediator.mottaMelding(any<VedtakFattet>(), any()) }
    }

    @Language("JSON")
    private fun event() = """
    {
      "@event_name": "vedtak_fattet",
      "@id": "${UUID.randomUUID()}",
      "@opprettet": "${LocalDateTime.now()}",
      "aktørId": "1111100000000",
      "fødselsnummer": "11111100000",
      "organisasjonsnummer": "987654321",
      "vedtaksperiodeId": "${UUID.randomUUID()}",
      "behandlingId": "${UUID.randomUUID()}"
    }
"""
}
