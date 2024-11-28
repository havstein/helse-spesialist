package no.nav.helse.modell.arbeidsforhold

import io.mockk.mockk
import io.mockk.verify
import no.nav.helse.medRivers
import no.nav.helse.mediator.MeldingMediator
import no.nav.helse.mediator.meldinger.Testmeldingfabrikk
import no.nav.helse.kafka.ArbeidsforholdLøsningRiver
import no.nav.helse.modell.KomplettArbeidsforholdDto
import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

internal class ArbeidsforholdløsningTest {
    private companion object {
        private const val ORGNR = "123456789"
        private const val FØDSELSNUMMER = "12345678910"
        private const val AKTØRID = "123456789"
        private val VEDTAKSPERIODE_ID = UUID.randomUUID()
        private const val STILLINGSTITTEL = "Stillingstittel1"
        private const val STILLINGSPROSENT = 100
        private val STARTDATO: LocalDate = LocalDate.now()
        private val SLUTTDATO = null
    }

    private val dao = mockk<ArbeidsforholdDao>(relaxed = true)
    private val mediator = mockk<MeldingMediator>(relaxed = true)
    private val rapid = TestRapid().medRivers(ArbeidsforholdLøsningRiver(mediator))

    @BeforeEach
    internal fun resetTestSetup() {
        rapid.reset()
    }

    @Test
    fun `mottar arbeidsgiverløsning`() {
        rapid.sendTestMessage(
            Testmeldingfabrikk.lagArbeidsforholdløsning(
                aktørId = AKTØRID,
                fødselsnummer = FØDSELSNUMMER,
                organisasjonsnummer = ORGNR,
                løsning = listOf(
                    Arbeidsforholdløsning.Løsning(
                        stillingstittel = STILLINGSTITTEL,
                        stillingsprosent = STILLINGSPROSENT,
                        startdato = STARTDATO,
                        sluttdato = SLUTTDATO
                    )
                ),
                vedtaksperiodeId = VEDTAKSPERIODE_ID
            )
        )
        verify(exactly = 1) { mediator.løsning(any(), any(), any(), any<Arbeidsforholdløsning>(), any()) }
    }

    @Test
    fun `mottar tom arbeidsgiverløsning`() {
        rapid.sendTestMessage(
            Testmeldingfabrikk.lagArbeidsforholdløsning(
                aktørId = AKTØRID,
                fødselsnummer = FØDSELSNUMMER,
                organisasjonsnummer = ORGNR,
                løsning = listOf(),
                vedtaksperiodeId = VEDTAKSPERIODE_ID
            )
        )

        verify(exactly = 1) { mediator.løsning(any(), any(), any(), any<Arbeidsforholdløsning>(), any()) }
    }

    @Test
    fun `oppretter løsning`() {
        val arbeidsforhold = Arbeidsforholdløsning(
            listOf(
                Arbeidsforholdløsning.Løsning(
                    STARTDATO,
                    SLUTTDATO,
                    STILLINGSTITTEL,
                    STILLINGSPROSENT
                )
            )
        )
        arbeidsforhold.upsert(dao, FØDSELSNUMMER, ORGNR)
        verify(exactly = 1) {
            dao.upsertArbeidsforhold(FØDSELSNUMMER, ORGNR,
                listOf(
                    KomplettArbeidsforholdDto(
                        fødselsnummer = FØDSELSNUMMER,
                        organisasjonsnummer = ORGNR,
                        startdato = STARTDATO,
                        sluttdato = SLUTTDATO,
                        stillingstittel = STILLINGSTITTEL,
                        stillingsprosent = STILLINGSPROSENT
                    )
                )
            )
        }
    }

    @Test
    fun `oppdaterer løsning`() {
        val arbeidsforhold = listOf(
            Arbeidsforholdløsning.Løsning(
                STARTDATO,
                SLUTTDATO,
                STILLINGSTITTEL,
                STILLINGSPROSENT
            )
        )
        val arbeidsforholdløsning = Arbeidsforholdløsning(arbeidsforhold)
        arbeidsforholdløsning.upsert(dao, FØDSELSNUMMER, ORGNR)
        verify(exactly = 1) {
            dao.upsertArbeidsforhold(
                fødselsnummer = FØDSELSNUMMER,
                organisasjonsnummer = ORGNR,
                arbeidsforhold = arbeidsforhold.map {
                    KomplettArbeidsforholdDto(
                        fødselsnummer = FØDSELSNUMMER,
                        organisasjonsnummer = ORGNR,
                        startdato = it.startdato,
                        sluttdato = it.sluttdato,
                        stillingstittel = it.stillingstittel,
                        stillingsprosent = it.stillingsprosent
                    )
                }
            )
        }
    }
}
