package no.nav.helse.kafka

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.mediator.MeldingMediator
import no.nav.helse.mediator.asUUID
import no.nav.helse.modell.person.HentPersoninfoløsning
import no.nav.helse.modell.person.HentPersoninfoløsninger
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.River
import no.nav.helse.rapids_rivers.asLocalDate
import no.nav.helse.rapids_rivers.isMissingOrNull
import no.nav.helse.spesialist.api.person.Adressebeskyttelse
import no.nav.helse.spesialist.typer.Kjønn
import org.slf4j.LoggerFactory

internal class PersoninfoløsningRiver(
    private val mediator: MeldingMediator,
) : SpesialistRiver {
    private val sikkerLog = LoggerFactory.getLogger("tjenestekall")

    override fun validations() =
        River.PacketValidation {
            it.demandValue("@event_name", "behov")
            it.demandValue("@final", true)
            it.demandAll("@behov", listOf("HentPersoninfoV2"))
            it.demand("@løsning.HentPersoninfoV2") { require(it.isObject) }
            it.requireKey("@id", "contextId", "hendelseId")
            it.requireKey(
                "@løsning.HentPersoninfoV2.fornavn",
                "@løsning.HentPersoninfoV2.etternavn",
                "@løsning.HentPersoninfoV2.fødselsdato",
                "@løsning.HentPersoninfoV2.kjønn",
                "@løsning.HentPersoninfoV2.adressebeskyttelse",
            )
            it.interestedIn("@løsning.HentPersoninfoV2.mellomnavn")
        }

    override fun onError(
        problems: MessageProblems,
        context: MessageContext,
    ) {
        sikkerLog.error("forstod ikke HentPersoninfoV2 (enkel):\n${problems.toExtendedReport()}")
    }

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
    ) {
        val hendelseId = packet["hendelseId"].asUUID()
        val contextId = packet["contextId"].asUUID()
        mediator.løsning(
            hendelseId,
            contextId,
            packet["@id"].asUUID(),
            parsePersoninfo(packet["@løsning.HentPersoninfoV2"]),
            context,
        )
    }
}

internal class FlerePersoninfoRiver(
    private val mediator: MeldingMediator,
) : SpesialistRiver {
    private val sikkerLog = LoggerFactory.getLogger("tjenestekall")

    override fun validations() =
        River.PacketValidation {
            it.demandValue("@event_name", "behov")
            it.demandValue("@final", true)
            it.demandAll("@behov", listOf("HentPersoninfoV2"))
            it.demand("@løsning.HentPersoninfoV2") { require(it.isArray) }
            it.requireKey("@id", "contextId", "hendelseId")
            it.requireArray("@løsning.HentPersoninfoV2") {
                requireKey("ident", "fornavn", "etternavn", "fødselsdato", "kjønn", "adressebeskyttelse")
                interestedIn("mellomnavn")
            }
        }

    override fun onError(
        problems: MessageProblems,
        context: MessageContext,
    ) {
        sikkerLog.error("forstod ikke HentPersoninfoV2 (flere):\n${problems.toExtendedReport()}")
    }

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
    ) {
        val hendelseId = packet["hendelseId"].asUUID()
        val contextId = packet["contextId"].asUUID()
        mediator.løsning(
            hendelseId,
            contextId,
            packet["@id"].asUUID(),
            HentPersoninfoløsninger(
                packet["@løsning.HentPersoninfoV2"].map {
                    parsePersoninfo(
                        it,
                    )
                },
            ),
            context,
        )
    }
}

private fun parsePersoninfo(node: JsonNode): HentPersoninfoløsning {
    val ident = node.path("ident").asText()
    val fornavn = node.path("fornavn").asText()
    val mellomnavn = node.path("mellomnavn").takeUnless(JsonNode::isMissingOrNull)?.asText()
    val etternavn = node.path("etternavn").asText()
    val fødselsdato = node.path("fødselsdato").asLocalDate()
    val kjønn = Kjønn.valueOf(node.path("kjønn").textValue())
    val adressebeskyttelse = Adressebeskyttelse.valueOf(node.path("adressebeskyttelse").textValue())
    return HentPersoninfoløsning(ident, fornavn, mellomnavn, etternavn, fødselsdato, kjønn, adressebeskyttelse)
}
