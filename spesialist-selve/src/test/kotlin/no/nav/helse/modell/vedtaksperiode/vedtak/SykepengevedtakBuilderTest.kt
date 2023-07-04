package no.nav.helse.modell.vedtaksperiode.vedtak

import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.januar
import no.nav.helse.modell.vedtaksperiode.vedtak.Faktatype.ETTER_HOVEDREGEL
import no.nav.helse.modell.vedtaksperiode.vedtak.Sykepengegrunnlagsfakta.Infotrygd
import no.nav.helse.modell.vedtaksperiode.vedtak.Sykepengegrunnlagsfakta.Spleis
import no.nav.helse.modell.vedtaksperiode.vedtak.Sykepengegrunnlagsfakta.Spleis.Arbeidsgiver
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SykepengevedtakBuilderTest {

    private companion object {
        private const val fødselsnummer = "12345678910"
        private const val aktørId = "1234567891011"
        private const val organisasjonsnummer = "123456789"
        private val vedtaksperiodeId = UUID.randomUUID()
        private val utbetalingId = UUID.randomUUID()
        private val fom = 1.januar
        private val tom = 31.januar
        private val skjæringstidspunkt = 1.januar
        private val hendelser = listOf(UUID.randomUUID(), UUID.randomUUID())
        private const val sykepengegrunnlag = 600000.00
        private const val grunnlagForSykepengegrunnlag = 600000.00
        private val grunnlagForSykepengegrunnlagPerArbeidsgiver = mapOf(
            organisasjonsnummer to 300000.00,
            "987654321" to 300000.00
        )
        private const val begrensning = "ER_6G_BEGRENSET"
        private const val inntekt = 25000.00
        private val vedtakFattetTidspunkt = LocalDateTime.now()
        private const val omregnetÅrsinntekt = 300000.00
        private const val innrapportertÅrsinntekt = 300000.00
        private const val avviksprosent = 0.0
        private const val seksG2023 = 6 * 118620
    }

    @Test
    fun `Bygg vanlig vedtak`() {
        val builder = SykepengevedtakBuilder()
        builder
            .fødselsnummer(fødselsnummer)
            .aktørId(aktørId)
            .organisasjonsnummer(organisasjonsnummer)
            .vedtaksperiodeId(vedtaksperiodeId)
            .utbetalingId(utbetalingId)
            .fom(fom)
            .tom(tom)
            .skjæringstidspunkt(skjæringstidspunkt)
            .hendelser(hendelser)
            .sykepengegrunnlag(sykepengegrunnlag)
            .grunnlagForSykepengegrunnlag(grunnlagForSykepengegrunnlag)
            .grunnlagForSykepengegrunnlagPerArbeidsgiver(grunnlagForSykepengegrunnlagPerArbeidsgiver)
            .begrensning(begrensning)
            .inntekt(inntekt)
            .vedtakFattetTidspunkt(vedtakFattetTidspunkt)
            .sykepengegrunnlagsfakta(sykepengegrunnlagsfakta(ETTER_HOVEDREGEL))

        val utkast = builder.build()
        assertTrue(utkast is Sykepengevedtak.Vedtak)
    }

    @Test
    fun `Bygg AUU-vedtak`() {
        val builder = SykepengevedtakBuilder()
        builder
            .fødselsnummer(fødselsnummer)
            .aktørId(aktørId)
            .organisasjonsnummer(organisasjonsnummer)
            .vedtaksperiodeId(vedtaksperiodeId)
            .fom(fom)
            .tom(tom)
            .skjæringstidspunkt(skjæringstidspunkt)
            .hendelser(hendelser)
            .sykepengegrunnlag(sykepengegrunnlag)
            .grunnlagForSykepengegrunnlag(grunnlagForSykepengegrunnlag)
            .grunnlagForSykepengegrunnlagPerArbeidsgiver(grunnlagForSykepengegrunnlagPerArbeidsgiver)
            .begrensning(begrensning)
            .inntekt(inntekt)
            .vedtakFattetTidspunkt(vedtakFattetTidspunkt)

        val utkast = builder.build()
        assertTrue(utkast is Sykepengevedtak.AuuVedtak)
    }

    @Test
    fun `Krever sykepengegrunnlagsfakta for å bygge vanlig vedtak`() {
        val builder = SykepengevedtakBuilder()
        builder
            .fødselsnummer(fødselsnummer)
            .aktørId(aktørId)
            .organisasjonsnummer(organisasjonsnummer)
            .vedtaksperiodeId(vedtaksperiodeId)
            .utbetalingId(utbetalingId)
            .fom(fom)
            .tom(tom)
            .skjæringstidspunkt(skjæringstidspunkt)
            .hendelser(hendelser)
            .sykepengegrunnlag(sykepengegrunnlag)
            .grunnlagForSykepengegrunnlag(grunnlagForSykepengegrunnlag)
            .grunnlagForSykepengegrunnlagPerArbeidsgiver(grunnlagForSykepengegrunnlagPerArbeidsgiver)
            .begrensning(begrensning)
            .inntekt(inntekt)
            .vedtakFattetTidspunkt(vedtakFattetTidspunkt)

        assertThrows<IllegalArgumentException> {
            builder.build()
        }
    }

    @Test
    fun `Sykepengegrunnlagsfakta kan ikke være satt ved bygging av AUU-vedtak`() {
        val builder = SykepengevedtakBuilder()
        builder
            .fødselsnummer(fødselsnummer)
            .aktørId(aktørId)
            .organisasjonsnummer(organisasjonsnummer)
            .vedtaksperiodeId(vedtaksperiodeId)
            .fom(fom)
            .tom(tom)
            .skjæringstidspunkt(skjæringstidspunkt)
            .hendelser(hendelser)
            .sykepengegrunnlag(sykepengegrunnlag)
            .grunnlagForSykepengegrunnlag(grunnlagForSykepengegrunnlag)
            .grunnlagForSykepengegrunnlagPerArbeidsgiver(grunnlagForSykepengegrunnlagPerArbeidsgiver)
            .begrensning(begrensning)
            .inntekt(inntekt)
            .vedtakFattetTidspunkt(vedtakFattetTidspunkt)
            .sykepengegrunnlagsfakta(sykepengegrunnlagsfakta(ETTER_HOVEDREGEL))

        assertThrows<IllegalArgumentException> {
            builder.build()
        }
    }

    private fun sykepengegrunnlagsfakta(faktatype: Faktatype): Sykepengegrunnlagsfakta {
        return when(faktatype) {
            Faktatype.ETTER_SKJØNN -> Spleis.EtterSkjønn(
                omregnetÅrsinntekt = omregnetÅrsinntekt,
                innrapportertÅrsinntekt = innrapportertÅrsinntekt,
                avviksprosent = avviksprosent,
                seksG = seksG2023,
                skjønnsfastsatt = 650000.0,
                tags = emptyList(),
                arbeidsgivere = listOf(
                    Arbeidsgiver.EtterSkjønn(organisasjonsnummer, 300000.0, 325000.0),
                    Arbeidsgiver.EtterSkjønn("987654321", 300000.0, 325000.0)
                )
            )
            ETTER_HOVEDREGEL -> Spleis.EtterHovedregel(
                omregnetÅrsinntekt = omregnetÅrsinntekt,
                innrapportertÅrsinntekt = innrapportertÅrsinntekt,
                avviksprosent = avviksprosent,
                seksG = seksG2023,
                tags = emptyList(),
                arbeidsgivere = listOf(
                    Arbeidsgiver.EtterHovedregel(organisasjonsnummer, 300000.0),
                    Arbeidsgiver.EtterHovedregel("987654321", 300000.0)
                )
            )
            Faktatype.I_INFOTRYGD -> Infotrygd(
                omregnetÅrsinntekt = omregnetÅrsinntekt,
            )
        }
    }
}