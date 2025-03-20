package no.nav.helse.mediator.meldinger

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.mockk.mockk
import io.mockk.verify
import no.nav.helse.kafka.ArbeidsgiverinformasjonLøsningRiver
import no.nav.helse.medRivers
import no.nav.helse.mediator.MeldingMediator
import no.nav.helse.modell.arbeidsgiver.Arbeidsgiverinformasjonløsning
import no.nav.helse.spesialist.kafka.testfixtures.Testmeldingfabrikk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

internal class ArbeidsgiverinformasjonløsningTest {
    private companion object {
        private const val ORGNR = "123456789"
        private const val FØDSELSNUMMER = "12345678910"
        private const val AKTØRID = "123456789"
        private val VEDTAKSPERIODE_ID = UUID.randomUUID()
    }

    private val mediator = mockk<MeldingMediator>(relaxed = true)
    private val rapid = TestRapid().medRivers(ArbeidsgiverinformasjonLøsningRiver(mediator))

    @BeforeEach
    internal fun resetTestSetup() {
        rapid.reset()
    }

    @Test
    fun `mottar arbeidsgiverløsning`() {
        rapid.sendTestMessage(
            Testmeldingfabrikk.lagArbeidsgiverinformasjonløsning(
                aktørId = AKTØRID,
                fødselsnummer = FØDSELSNUMMER,
                organisasjonsnummer = ORGNR,
                vedtaksperiodeId = VEDTAKSPERIODE_ID
            )
        )
        verify(exactly = 1) { mediator.løsning(any(), any(), any(), any<Arbeidsgiverinformasjonløsning>(), any()) }
    }
}
