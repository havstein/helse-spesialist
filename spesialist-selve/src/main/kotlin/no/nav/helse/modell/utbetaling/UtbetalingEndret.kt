package no.nav.helse.modell.utbetaling

import com.fasterxml.jackson.databind.JsonNode
import kotliquery.TransactionalSession
import no.nav.helse.db.OppgaveDao
import no.nav.helse.db.OpptegnelseRepository
import no.nav.helse.db.ReservasjonRepository
import no.nav.helse.db.TildelingRepository
import no.nav.helse.db.UtbetalingRepository
import no.nav.helse.mediator.Kommandostarter
import no.nav.helse.mediator.asUUID
import no.nav.helse.mediator.meldinger.Personmelding
import no.nav.helse.mediator.oppgave.OppgaveService
import no.nav.helse.modell.kommando.Command
import no.nav.helse.modell.kommando.MacroCommand
import no.nav.helse.modell.kommando.ReserverPersonHvisTildeltCommand
import no.nav.helse.modell.oppgave.OppdaterOppgavestatusCommand
import no.nav.helse.modell.person.Person
import no.nav.helse.modell.totrinnsvurdering.TotrinnsvurderingService
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.asLocalDateTime
import java.time.LocalDateTime
import java.util.UUID

internal class UtbetalingEndret private constructor(
    override val id: UUID,
    private val fødselsnummer: String,
    val organisasjonsnummer: String,
    val utbetalingId: UUID,
    val type: String,
    val gjeldendeStatus: Utbetalingsstatus,
    val opprettet: LocalDateTime,
    val arbeidsgiverbeløp: Int,
    val personbeløp: Int,
    val arbeidsgiverOppdrag: LagreOppdragCommand.Oppdrag,
    val personOppdrag: LagreOppdragCommand.Oppdrag,
    private val json: String,
) : Personmelding {
    internal constructor(packet: JsonMessage) : this(
        id = packet["@id"].asUUID(),
        fødselsnummer = packet["fødselsnummer"].asText(),
        organisasjonsnummer = packet["organisasjonsnummer"].asText(),
        utbetalingId = packet["utbetalingId"].asUUID(),
        type = packet["type"].asText(),
        gjeldendeStatus = Utbetalingsstatus.valueOf(packet["gjeldendeStatus"].asText()),
        opprettet = packet["@opprettet"].asLocalDateTime(),
        arbeidsgiverbeløp = packet["arbeidsgiverOppdrag"]["nettoBeløp"].asInt(),
        personbeløp = packet["personOppdrag"]["nettoBeløp"].asInt(),
        arbeidsgiverOppdrag = tilOppdrag(packet["arbeidsgiverOppdrag"], packet["organisasjonsnummer"].asText()),
        personOppdrag = tilOppdrag(packet["personOppdrag"], packet["fødselsnummer"].asText()),
        json = packet.toJson(),
    )

    internal constructor(jsonNode: JsonNode) : this(
        id = UUID.fromString(jsonNode["@id"].asText()),
        fødselsnummer = jsonNode["fødselsnummer"].asText(),
        organisasjonsnummer = jsonNode["organisasjonsnummer"].asText(),
        utbetalingId = UUID.fromString(jsonNode["utbetalingId"].asText()),
        type = jsonNode["type"].asText(),
        gjeldendeStatus = Utbetalingsstatus.valueOf(jsonNode["gjeldendeStatus"].asText()),
        opprettet = jsonNode["@opprettet"].asLocalDateTime(),
        arbeidsgiverbeløp = jsonNode["arbeidsgiverOppdrag"]["nettoBeløp"].asInt(),
        personbeløp = jsonNode["personOppdrag"]["nettoBeløp"].asInt(),
        arbeidsgiverOppdrag = tilOppdrag(jsonNode["arbeidsgiverOppdrag"], jsonNode["organisasjonsnummer"].asText()),
        personOppdrag = tilOppdrag(jsonNode["personOppdrag"], jsonNode["fødselsnummer"].asText()),
        json = jsonNode.toString(),
    )

    override fun behandle(
        person: Person,
        kommandostarter: Kommandostarter,
        transactionalSession: TransactionalSession,
    ) {
        if (gjeldendeStatus == Utbetalingsstatus.FORKASTET) person.utbetalingForkastet(utbetalingId)
        this.kommandostarter { utbetalingEndret(this@UtbetalingEndret, transactionalSession) }
    }

    override fun fødselsnummer(): String = fødselsnummer

    override fun toJson(): String = json

    private companion object {
        private fun tilOppdrag(
            jsonNode: JsonNode,
            mottaker: String,
        ) = LagreOppdragCommand.Oppdrag(
            fagsystemId = jsonNode.path("fagsystemId").asText(),
            mottaker = jsonNode.path("mottaker").takeIf(JsonNode::isTextual)?.asText() ?: mottaker,
        )
    }
}

internal class UtbetalingEndretCommand(
    fødselsnummer: String,
    organisasjonsnummer: String,
    utbetalingId: UUID,
    utbetalingstype: String,
    gjeldendeStatus: Utbetalingsstatus,
    opprettet: LocalDateTime,
    arbeidsgiverOppdrag: LagreOppdragCommand.Oppdrag,
    personOppdrag: LagreOppdragCommand.Oppdrag,
    arbeidsgiverbeløp: Int,
    personbeløp: Int,
    utbetalingRepository: UtbetalingRepository,
    opptegnelseRepository: OpptegnelseRepository,
    reservasjonRepository: ReservasjonRepository,
    oppgaveDao: OppgaveDao,
    tildelingRepository: TildelingRepository,
    oppgaveService: OppgaveService,
    totrinnsvurderingService: TotrinnsvurderingService,
    json: String,
) : MacroCommand() {
    override val commands: List<Command> =
        mutableListOf(
            LagreOppdragCommand(
                fødselsnummer = fødselsnummer,
                orgnummer = organisasjonsnummer,
                utbetalingId = utbetalingId,
                type = Utbetalingtype.valueOf(utbetalingstype),
                status = gjeldendeStatus,
                opprettet = opprettet,
                arbeidsgiverOppdrag = arbeidsgiverOppdrag,
                personOppdrag = personOppdrag,
                arbeidsgiverbeløp = arbeidsgiverbeløp,
                personbeløp = personbeløp,
                json = json,
                utbetalingRepository = utbetalingRepository,
                opptegnelseRepository = opptegnelseRepository,
            ),
            ReserverPersonHvisTildeltCommand(
                fødselsnummer = fødselsnummer,
                reservasjonRepository = reservasjonRepository,
                tildelingRepository = tildelingRepository,
                oppgaveDao = oppgaveDao,
                totrinnsvurderingService = totrinnsvurderingService,
            ),
            OppdaterOppgavestatusCommand(utbetalingId, gjeldendeStatus, oppgaveService),
        )
}
