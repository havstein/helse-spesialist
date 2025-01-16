package no.nav.helse.mediator.meldinger

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.mockk.mockk
import io.mockk.verify
import no.nav.helse.kafka.AvsluttetUtenVedtakRiver
import no.nav.helse.medRivers
import no.nav.helse.mediator.MeldingMediator
import no.nav.helse.mediator.meldinger.hendelser.AvsluttetUtenVedtakMessage
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test
import java.util.UUID

internal class AvsluttetUtenVedtakRiverTest {
    private val mediator = mockk<MeldingMediator>(relaxed = true)
    private val testRapid = TestRapid().medRivers(AvsluttetUtenVedtakRiver(mediator))

    @Test
    fun `Leser inn utkast_til_vedtak-event`() {
        testRapid.sendTestMessage(avsluttetUtenVedtak())
        verify(exactly = 1) { mediator.mottaMelding(any<AvsluttetUtenVedtakMessage>(), any()) }
    }

    @Language("JSON")
    private fun avsluttetUtenVedtak(): String {
        return """
        {
            "@event_name": "avsluttet_uten_vedtak",
            "aktørId": "1122",
            "fødselsnummer": "2233",
            "organisasjonsnummer": "3344",
            "fom" : "2018-01-01",
            "tom" : "2018-01-16",
            "skjæringstidspunkt": "2018-01-01",
            "hendelser": ["${UUID.randomUUID()}"],
            "vedtaksperiodeId": "${UUID.randomUUID()}",
            "behandlingId": "${UUID.randomUUID()}",
            "avsluttetTidspunkt": "2018-02-01T00:42:00.000",
            "@id": "${UUID.randomUUID()}",
            "@opprettet": "2018-02-01T00:00:00.000"          
        }
    """.trimIndent()
    }
}

