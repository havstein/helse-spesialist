package no.nav.helse.mediator.kafka.meldinger

import no.nav.helse.mediator.kafka.HendelseMediator
import no.nav.helse.modell.gosysoppgaver.ÅpneGosysOppgaverDao
import no.nav.helse.modell.gosysoppgaver.ÅpneGosysOppgaverDto
import no.nav.helse.rapids_rivers.*
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.*

internal class ÅpneGosysOppgaverLøsning(
    private val opprettet: LocalDateTime,
    private val fødselsnummer: String,
    private val antall: Int?,
    private val oppslagFeilet: Boolean
) {
    internal fun lagre(åpneGosysOppgaverDao: ÅpneGosysOppgaverDao) {
        åpneGosysOppgaverDao.persisterÅpneGosysOppgaver(
            ÅpneGosysOppgaverDto(
                fødselsnummer = fødselsnummer,
                antall = antall,
                oppslagFeilet = oppslagFeilet,
                opprettet = opprettet
            )
        )
    }

    internal class ÅpneGosysOppgaverRiver(
        rapidsConnection: RapidsConnection,
        private val hendelseMediator: HendelseMediator
    ) : River.PacketListener {
        private val sikkerLogg = LoggerFactory.getLogger("tjenestekall")

        init {
            River(rapidsConnection).apply {
                validate {
                    it.demandValue("@event_name", "behov")
                    it.demandValue("@final", true)
                    it.demandAll("@behov", listOf("ÅpneOppgaver"))
                    it.require("@opprettet") { message -> message.asLocalDateTime() }
                    it.requireKey("@id", "contextId", "hendelseId", "fødselsnummer")
                    it.requireKey("@løsning.ÅpneOppgaver.antall")
                    it.requireKey("@løsning.ÅpneOppgaver.oppslagFeilet")
                }
            }.register(this)
        }

        override fun onPacket(packet: JsonMessage, context: RapidsConnection.MessageContext) {
            sikkerLogg.info("Mottok melding ÅpneOppgaverMessage: ", packet.toJson())
            val opprettet = packet["@opprettet"].asLocalDateTime()
            val contextId = UUID.fromString(packet["contextId"].asText())
            val hendelseId = UUID.fromString(packet["hendelseId"].asText())
            val fødselsnummer = packet["fødselsnummer"].asText()

            val antall = packet["@løsning.ÅpneOppgaver.antall"].takeUnless { it.isMissingOrNull() }?.asInt()
            val oppslagFeilet = packet["@løsning.ÅpneOppgaver.oppslagFeilet"].asBoolean()

            val åpneGosysOppgaver = ÅpneGosysOppgaverLøsning(
                opprettet = opprettet,
                fødselsnummer = fødselsnummer,
                antall = antall,
                oppslagFeilet = oppslagFeilet
            )

            hendelseMediator.løsning(
                hendelseId = hendelseId,
                contextId = contextId,
                behovId = UUID.fromString(packet["@id"].asText()),
                løsning = åpneGosysOppgaver,
                context = context
            )
        }
    }
}
