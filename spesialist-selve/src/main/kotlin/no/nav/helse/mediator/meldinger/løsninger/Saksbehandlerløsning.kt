package no.nav.helse.mediator.meldinger.løsninger

import com.fasterxml.jackson.databind.JsonNode
import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.mediator.GodkjenningMediator
import no.nav.helse.mediator.HendelseMediator
import no.nav.helse.mediator.meldinger.Hendelse
import no.nav.helse.modell.HendelseDao
import no.nav.helse.modell.kommando.MacroCommand
import no.nav.helse.modell.kommando.UtbetalingsgodkjenningCommand
import no.nav.helse.modell.oppgave.OppgaveDao
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helse.rapids_rivers.asLocalDateTime
import no.nav.helse.rapids_rivers.isMissingOrNull
import org.slf4j.LoggerFactory

/**
 * Behandler input til godkjenningsbehov fra saksbehandler som har blitt lagt på rapid-en av API-biten av spesialist.
 */
internal class Saksbehandlerløsning(
    override val id: UUID,
    private val fødselsnummer: String,
    private val json: String,
    godkjent: Boolean,
    saksbehandlerIdent: String,
    epostadresse: String,
    godkjenttidspunkt: LocalDateTime,
    årsak: String?,
    begrunnelser: List<String>?,
    kommentar: String?,
    private val oppgaveId: Long,
    godkjenningsbehovhendelseId: UUID,
    hendelseDao: HendelseDao,
    private val oppgaveDao: OppgaveDao,
    godkjenningMediator: GodkjenningMediator,
) : Hendelse, MacroCommand() {

    override val commands = listOf(
        UtbetalingsgodkjenningCommand(
            godkjent,
            saksbehandlerIdent,
            epostadresse,
            godkjenttidspunkt,
            årsak,
            begrunnelser,
            kommentar,
            godkjenningsbehovhendelseId,
            hendelseDao,
            godkjenningMediator,
            vedtaksperiodeId(),
            fødselsnummer
        ),
    )

    override fun fødselsnummer() = fødselsnummer
    override fun vedtaksperiodeId() = oppgaveDao.finnVedtaksperiodeId(oppgaveId)
    override fun toJson() = json

    internal class SaksbehandlerløsningRiver(
        rapidsConnection: RapidsConnection,
        private val mediator: HendelseMediator,
    ) : River.PacketListener {
        private val sikkerLog = LoggerFactory.getLogger("tjenestekall")

        init {
            River(rapidsConnection)
                .apply {
                    validate {
                        it.demandValue("@event_name", "saksbehandler_løsning")
                        it.requireKey("@id", "fødselsnummer", "oppgaveId", "hendelseId")
                        it.requireKey("godkjent", "saksbehandlerident", "saksbehandlerepost")
                        it.require("godkjenttidspunkt", JsonNode::asLocalDateTime)
                        it.interestedIn("årsak", "begrunnelser", "kommentar")
                    }
                }.register(this)
        }

        override fun onError(problems: MessageProblems, context: MessageContext) {
            sikkerLog.error("forstod ikke saksbehandlerløsning:\n${problems.toExtendedReport()}")
        }

        override fun onPacket(packet: JsonMessage, context: MessageContext) {
            val hendelseId = UUID.fromString(packet["hendelseId"].asText())
            val id = UUID.fromString(packet["@id"].asText())
            if (id == UUID.fromString("74b22005-db93-4f52-ac55-d0571a1bcf69")) return
            mediator.saksbehandlerløsning(
                packet,
                id,
                hendelseId,
                packet["fødselsnummer"].asText(),
                packet["godkjent"].asBoolean(),
                packet["saksbehandlerident"].asText(),
                packet["saksbehandlerepost"].asText(),
                packet["godkjenttidspunkt"].asLocalDateTime(),
                packet["årsak"].takeUnless(JsonNode::isMissingOrNull)?.asText(),
                packet["begrunnelser"].takeUnless(JsonNode::isMissingOrNull)?.map(JsonNode::asText),
                packet["kommentar"].takeUnless(JsonNode::isMissingOrNull)?.asText(),
                packet["oppgaveId"].asLong(),
                context
            )
        }
    }
}
