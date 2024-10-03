package no.nav.helse.mediator.meldinger

import net.logstash.logback.argument.StructuredArguments
import no.nav.helse.mediator.MeldingMediator
import no.nav.helse.mediator.SpesialistRiver
import no.nav.helse.modell.person.OppdaterPersondata
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.River
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.UUID

internal class OppdaterPersonsnapshotRiver(
    private val mediator: MeldingMediator,
) : SpesialistRiver {
    private val sikkerlogg: Logger = LoggerFactory.getLogger("tjenestekall")

    override fun validations() =
        River.PacketValidation {
            it.demandValue("@event_name", "oppdater_persondata")
            it.requireKey("@id", "fødselsnummer")
        }

    override fun onError(
        problems: MessageProblems,
        context: MessageContext,
    ) {
        sikkerlogg.error("Forstod ikke oppdater_persondata:\n${problems.toExtendedReport()}")
    }

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
    ) {
        val id = UUID.fromString(packet["@id"].asText())
        val fødselsnummer = packet["fødselsnummer"].asText()
        sikkerlogg.info(
            "Mottok forespørsel om å oppdatere persondata på {}, {}",
            StructuredArguments.keyValue("fødselsnummer", fødselsnummer),
            StructuredArguments.keyValue("eventId", id),
        )
        mediator.mottaMelding(OppdaterPersondata(packet), context)
    }
}
