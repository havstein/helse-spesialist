package no.nav.helse.mediator.meldinger

import io.mockk.mockk
import io.mockk.verify
import no.nav.helse.medRivers
import no.nav.helse.kafka.DokumentRiver
import no.nav.helse.modell.dokument.DokumentDao
import no.nav.helse.objectMapper
import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

internal class DokumentRiverTest {
    private companion object {
        private const val FNR = "12345678911"
        private val DOKUMENTID = UUID.randomUUID()
    }

    private val dokumentDao = mockk<DokumentDao>(relaxed = true)
    private val testRapid = TestRapid().medRivers(DokumentRiver(dokumentDao))

    @BeforeEach
    fun setup() {
        testRapid.reset()
    }

    @Test
    fun `leser HentDokument-melding`() {
        testRapid.sendTestMessage(Testmeldingfabrikk.lagHentDokumentLøsning(FNR, DOKUMENTID))
        verify(exactly = 1) { dokumentDao.lagre(FNR, DOKUMENTID, objectMapper.createObjectNode()) }
    }
}
