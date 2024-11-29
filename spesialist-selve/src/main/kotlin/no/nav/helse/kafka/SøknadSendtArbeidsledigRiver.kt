package no.nav.helse.kafka

import net.logstash.logback.argument.StructuredArguments.keyValue
import no.nav.helse.mediator.MeldingMediator
import no.nav.helse.mediator.asUUID
import no.nav.helse.modell.person.SøknadSendt
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.River
import org.slf4j.Logger
import org.slf4j.LoggerFactory

internal class SøknadSendtArbeidsledigRiver(
    private val mediator: MeldingMediator,
) : SpesialistRiver {
    private val logg = LoggerFactory.getLogger(this::class.java)
    private val sikkerLogg: Logger = LoggerFactory.getLogger("tjenestekall")

    override fun validations() =
        River.PacketValidation {
            it.demandAny("@event_name", listOf("sendt_søknad_arbeidsledig"))
            it.demandKey("tidligereArbeidsgiverOrgnummer")
            it.forbid("arbeidsgiver.orgnummer")
            it.requireKey(
                "@id",
                "fnr",
                "aktorId",
            )
        }

    override fun onError(
        problems: MessageProblems,
        context: MessageContext,
    ) {
        sikkerLogg.error("Forstod ikke SøknadSendt:\n${problems.toExtendedReport()}")
    }

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
    ) {
        logg.info(
            "Mottok SøknadSendt med {}",
            keyValue("hendelseId", packet["@id"].asUUID()),
        )
        sikkerLogg.info(
            "Mottok SøknadSendt med {}, {}",
            keyValue("hendelseId", packet["@id"].asUUID()),
            keyValue("hendelse", packet.toJson()),
        )
        mediator.mottaSøknadSendt(SøknadSendt.søknadSendtArbeidsledig(packet), context)
    }
}
