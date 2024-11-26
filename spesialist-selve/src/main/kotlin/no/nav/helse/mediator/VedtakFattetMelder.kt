package no.nav.helse.mediator

import net.logstash.logback.argument.StructuredArguments.kv
import no.nav.helse.modell.person.PersonObserver
import no.nav.helse.modell.vedtak.Sykepengegrunnlagsfakta.Infotrygd
import no.nav.helse.modell.vedtak.Sykepengegrunnlagsfakta.Spleis
import no.nav.helse.modell.vedtak.Sykepengevedtak
import no.nav.helse.modell.vedtak.VedtakBegrunnelseDto
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import org.slf4j.LoggerFactory

internal class VedtakFattetMelder(
    private val messageContext: MessageContext,
) : PersonObserver {
    private val sykepengevedtak = mutableListOf<Sykepengevedtak>()

    private companion object {
        private val sikkerLogg = LoggerFactory.getLogger("tjenestekall")
        private val logg = LoggerFactory.getLogger(VedtakFattetMelder::class.java)
    }

    internal fun publiserUtgåendeMeldinger() {
        if (sykepengevedtak.isEmpty()) return
        check(sykepengevedtak.size == 1) { "Forventer å publisere kun ett vedtak" }
        val sykepengevedtak = sykepengevedtak.single()
        val json =
            when (sykepengevedtak) {
                is Sykepengevedtak.AuuVedtak -> auuVedtakJson(sykepengevedtak)
                is Sykepengevedtak.Vedtak -> vedtakJson(sykepengevedtak)
            }
        logg.info("Publiserer vedtak_fattet for {}", kv("vedtaksperiodeId", sykepengevedtak.vedtaksperiodeId))
        sikkerLogg.info(
            "Publiserer vedtak_fattet for {}, {}, {}",
            kv("fødselsnummer", sykepengevedtak.fødselsnummer),
            kv("organisasjonsnummer", sykepengevedtak.organisasjonsnummer),
            kv("vedtaksperiodeId", sykepengevedtak.vedtaksperiodeId),
        )
        messageContext.publish(json)
        this.sykepengevedtak.clear()
    }

    override fun vedtakFattet(sykepengevedtak: Sykepengevedtak) {
        this.sykepengevedtak.add(sykepengevedtak)
    }

    private fun vedtakJson(sykepengevedtak: Sykepengevedtak.Vedtak): String {
        val begrunnelser: MutableList<Map<String, Any>> = mutableListOf()
        val message =
            JsonMessage.newMessage(
                "vedtak_fattet",
                mutableMapOf(
                    "fødselsnummer" to sykepengevedtak.fødselsnummer,
                    "aktørId" to sykepengevedtak.aktørId,
                    "vedtaksperiodeId" to "${sykepengevedtak.vedtaksperiodeId}",
                    "behandlingId" to "${sykepengevedtak.spleisBehandlingId}",
                    "organisasjonsnummer" to sykepengevedtak.organisasjonsnummer,
                    "fom" to "${sykepengevedtak.fom}",
                    "tom" to "${sykepengevedtak.tom}",
                    "skjæringstidspunkt" to "${sykepengevedtak.skjæringstidspunkt}",
                    "hendelser" to sykepengevedtak.hendelser,
                    "sykepengegrunnlag" to sykepengevedtak.sykepengegrunnlag,
                    "grunnlagForSykepengegrunnlag" to sykepengevedtak.grunnlagForSykepengegrunnlag,
                    "grunnlagForSykepengegrunnlagPerArbeidsgiver" to sykepengevedtak.grunnlagForSykepengegrunnlagPerArbeidsgiver,
                    "begrensning" to sykepengevedtak.begrensning,
                    "inntekt" to sykepengevedtak.inntekt,
                    "vedtakFattetTidspunkt" to "${sykepengevedtak.vedtakFattetTidspunkt}",
                    "utbetalingId" to "${sykepengevedtak.utbetalingId}",
                    "tags" to sykepengevedtak.tags,
                    "sykepengegrunnlagsfakta" to
                        when (sykepengevedtak.sykepengegrunnlagsfakta) {
                            is Spleis -> {
                                val sykepengegrunnlagsfakta = (sykepengevedtak.sykepengegrunnlagsfakta as Spleis)
                                mutableMapOf(
                                    "omregnetÅrsinntekt" to sykepengegrunnlagsfakta.omregnetÅrsinntekt,
                                    "innrapportertÅrsinntekt" to sykepengegrunnlagsfakta.innrapportertÅrsinntekt,
                                    "avviksprosent" to sykepengegrunnlagsfakta.avviksprosent,
                                    "6G" to sykepengegrunnlagsfakta.seksG,
                                    "tags" to sykepengegrunnlagsfakta.tags,
                                    "arbeidsgivere" to
                                        sykepengegrunnlagsfakta.arbeidsgivere.map {
                                            mutableMapOf(
                                                "arbeidsgiver" to it.organisasjonsnummer,
                                                "omregnetÅrsinntekt" to it.omregnetÅrsinntekt,
                                                "innrapportertÅrsinntekt" to it.innrapportertÅrsinntekt,
                                            ).apply {
                                                if (it is Spleis.Arbeidsgiver.EtterSkjønn) {
                                                    put(
                                                        "skjønnsfastsatt",
                                                        it.skjønnsfastsatt,
                                                    )
                                                }
                                            }
                                        },
                                ).apply {
                                    when (sykepengegrunnlagsfakta) {
                                        is Spleis.EtterHovedregel -> put("fastsatt", "EtterHovedregel")
                                        is Spleis.EtterSkjønn -> {
                                            put("fastsatt", "EtterSkjønn")
                                            put(
                                                "skjønnsfastsettingtype",
                                                checkNotNull(sykepengevedtak.skjønnsfastsettingopplysninger?.skjønnsfastsettingtype),
                                            )
                                            put(
                                                "skjønnsfastsettingårsak",
                                                checkNotNull(sykepengevedtak.skjønnsfastsettingopplysninger?.skjønnsfastsettingsårsak),
                                            )
                                            put(
                                                "skjønnsfastsatt",
                                                (sykepengevedtak.sykepengegrunnlagsfakta as Spleis.EtterSkjønn).skjønnsfastsatt,
                                            )
                                        }
                                    }
                                }
                            }

                            is Infotrygd -> {
                                mapOf(
                                    "fastsatt" to "IInfotrygd",
                                    "omregnetÅrsinntekt" to sykepengevedtak.sykepengegrunnlagsfakta.omregnetÅrsinntekt,
                                )
                            }
                        },
                ).apply {
                    if (sykepengevedtak.sykepengegrunnlagsfakta is Spleis.EtterSkjønn) {
                        val skjønnsfastsettingopplysninger = sykepengevedtak.skjønnsfastsettingopplysninger!!
                        begrunnelser.addAll(
                            listOf(
                                mapOf(
                                    "type" to "SkjønnsfastsattSykepengegrunnlagMal",
                                    "begrunnelse" to skjønnsfastsettingopplysninger.begrunnelseFraMal,
                                    "perioder" to
                                        listOf(
                                            mapOf(
                                                "fom" to "${sykepengevedtak.fom}",
                                                "tom" to "${sykepengevedtak.tom}",
                                            ),
                                        ),
                                ),
                                mapOf(
                                    "type" to "SkjønnsfastsattSykepengegrunnlagFritekst",
                                    "begrunnelse" to skjønnsfastsettingopplysninger.begrunnelseFraFritekst,
                                    "perioder" to
                                        listOf(
                                            mapOf(
                                                "fom" to "${sykepengevedtak.fom}",
                                                "tom" to "${sykepengevedtak.tom}",
                                            ),
                                        ),
                                ),
                                mapOf(
                                    "type" to "SkjønnsfastsattSykepengegrunnlagKonklusjon",
                                    "begrunnelse" to skjønnsfastsettingopplysninger.begrunnelseFraKonklusjon,
                                    "perioder" to
                                        listOf(
                                            mapOf(
                                                "fom" to "${sykepengevedtak.fom}",
                                                "tom" to "${sykepengevedtak.tom}",
                                            ),
                                        ),
                                ),
                            ),
                        )
                    }

                    val vedtakBegrunnelse = sykepengevedtak.vedtakBegrunnelse
                    if (vedtakBegrunnelse != null) {
                        begrunnelser.addLast(
                            mapOf(
                                "type" to
                                    when (vedtakBegrunnelse.utfall) {
                                        VedtakBegrunnelseDto.UtfallDto.AVSLAG -> "Avslag"
                                        VedtakBegrunnelseDto.UtfallDto.DELVIS_INNVILGELSE -> "DelvisInnvilgelse"
                                        VedtakBegrunnelseDto.UtfallDto.INNVILGELSE -> "Innvilgelse"
                                    },
                                "begrunnelse" to (vedtakBegrunnelse.begrunnelse ?: ""),
                                "perioder" to
                                    listOf(
                                        mapOf(
                                            "fom" to "${sykepengevedtak.fom}",
                                            "tom" to "${sykepengevedtak.tom}",
                                        ),
                                    ),
                            ),
                        )
                    }

                    put("begrunnelser", begrunnelser)
                },
            )

        return message.toJson()
    }

    private fun auuVedtakJson(sykepengevedtak: Sykepengevedtak.AuuVedtak): String {
        val message =
            JsonMessage.newMessage(
                "vedtak_fattet",
                mapOf(
                    "fødselsnummer" to sykepengevedtak.fødselsnummer,
                    "aktørId" to sykepengevedtak.aktørId,
                    "vedtaksperiodeId" to "${sykepengevedtak.vedtaksperiodeId}",
                    "behandlingId" to "${sykepengevedtak.spleisBehandlingId}",
                    "organisasjonsnummer" to sykepengevedtak.organisasjonsnummer,
                    "fom" to "${sykepengevedtak.fom}",
                    "tom" to "${sykepengevedtak.tom}",
                    "skjæringstidspunkt" to "${sykepengevedtak.skjæringstidspunkt}",
                    "hendelser" to sykepengevedtak.hendelser,
                    "sykepengegrunnlag" to sykepengevedtak.sykepengegrunnlag,
                    "grunnlagForSykepengegrunnlag" to sykepengevedtak.grunnlagForSykepengegrunnlag,
                    "grunnlagForSykepengegrunnlagPerArbeidsgiver" to sykepengevedtak.grunnlagForSykepengegrunnlagPerArbeidsgiver,
                    "begrensning" to sykepengevedtak.begrensning,
                    "inntekt" to sykepengevedtak.inntekt,
                    "vedtakFattetTidspunkt" to "${sykepengevedtak.vedtakFattetTidspunkt}",
                    "begrunnelser" to emptyList<Map<String, Any>>(),
                    "tags" to sykepengevedtak.tags,
                ),
            )
        return message.toJson()
    }
}
