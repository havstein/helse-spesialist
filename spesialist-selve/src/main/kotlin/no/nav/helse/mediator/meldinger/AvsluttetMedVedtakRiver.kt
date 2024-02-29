package no.nav.helse.mediator.meldinger

import no.nav.helse.db.AvviksvurderingDao
import no.nav.helse.mediator.HendelseMediator
import no.nav.helse.mediator.meldinger.hendelser.AvsluttetMedVedtakMessage
import no.nav.helse.objectMapper
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import org.slf4j.Logger
import org.slf4j.LoggerFactory

internal class AvsluttetMedVedtakRiver(
    rapidsConnection: RapidsConnection,
    private val mediator: HendelseMediator,
    private val avviksvurderingDao: AvviksvurderingDao,
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandValue("@event_name", "avsluttet_med_vedtak")
                it.requireKey("@id", "fødselsnummer", "aktørId", "vedtaksperiodeId", "organisasjonsnummer")
                it.requireKey("fom", "tom", "skjæringstidspunkt")
                it.requireArray("hendelser")
                it.requireKey("sykepengegrunnlag", "grunnlagForSykepengegrunnlag", "grunnlagForSykepengegrunnlagPerArbeidsgiver")
                it.requireKey("begrensning", "inntekt", "vedtakFattetTidspunkt", "tags")
                it.requireKey("utbetalingId")

                it.requireAny("sykepengegrunnlagsfakta.fastsatt", listOf("EtterHovedregel", "IInfotrygd", "EtterSkjønn"))
                it.requireKey("sykepengegrunnlagsfakta.omregnetÅrsinntekt")
                it.require("sykepengegrunnlagsfakta.fastsatt") { fastsattNode ->
                    when (fastsattNode.asText()) {
                        "EtterHovedregel" -> {
                            it.requireKey(
                                "sykepengegrunnlagsfakta.6G",
                                "sykepengegrunnlagsfakta.tags",
                                "sykepengegrunnlagsfakta.arbeidsgivere",
                            )
                        }
                        "EtterSkjønn" -> {
                            it.requireKey(
                                "sykepengegrunnlagsfakta.6G",
                                "sykepengegrunnlagsfakta.tags",
                                "sykepengegrunnlagsfakta.arbeidsgivere",
                                "sykepengegrunnlagsfakta.skjønnsfastsatt",
                            )
                        }
                        else -> {}
                    }
                }
            }
        }.register(this)
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        sikkerlogg.error("Forstod ikke avsluttet_med_vedtak:\n${problems.toExtendedReport()}")
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        sikkerlogg.info("Mottok melding avsluttet_med_vedtak:\n${packet.toJson()}")
        mediator.håndter(AvsluttetMedVedtakMessage(objectMapper.readTree(packet.toJson()), avviksvurderingDao))
    }

    private companion object {
        private val sikkerlogg: Logger = LoggerFactory.getLogger("tjenestekall")
    }
}
