package no.nav.helse.modell.vedtaksperiode

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.db.CommandContextRepository
import no.nav.helse.db.OppgaveRepository
import no.nav.helse.db.ReservasjonRepository
import no.nav.helse.db.TildelingRepository
import no.nav.helse.mediator.Kommandostarter
import no.nav.helse.mediator.asUUID
import no.nav.helse.mediator.meldinger.Vedtaksperiodemelding
import no.nav.helse.mediator.oppgave.OppgaveService
import no.nav.helse.modell.kommando.AvbrytCommand
import no.nav.helse.modell.kommando.Command
import no.nav.helse.modell.kommando.MacroCommand
import no.nav.helse.modell.person.Person
import no.nav.helse.modell.totrinnsvurdering.TotrinnsvurderingMediator
import no.nav.helse.rapids_rivers.JsonMessage
import java.util.UUID

internal class VedtaksperiodeForkastet private constructor(
    override val id: UUID,
    private val vedtaksperiodeId: UUID,
    private val fødselsnummer: String,
    private val json: String,
) : Vedtaksperiodemelding {
    internal constructor(packet: JsonMessage) : this(
        packet["@id"].asUUID(),
        packet["vedtaksperiodeId"].asUUID(),
        packet["fødselsnummer"].asText(),
        json = packet.toJson(),
    )
    internal constructor(jsonNode: JsonNode) : this(
        UUID.fromString(jsonNode["@id"].asText()),
        UUID.fromString(jsonNode["vedtaksperiodeId"].asText()),
        jsonNode["fødselsnummer"].asText(),
        json = jsonNode.toString(),
    )

    override fun fødselsnummer() = fødselsnummer

    override fun vedtaksperiodeId() = vedtaksperiodeId

    override fun behandle(
        person: Person,
        kommandostarter: Kommandostarter,
    ) {
        person.vedtaksperiodeForkastet(vedtaksperiodeId)
        kommandostarter { vedtaksperiodeForkastet(this@VedtaksperiodeForkastet) }
    }

    override fun toJson() = json
}

internal class VedtaksperiodeForkastetCommand(
    val fødselsnummer: String,
    val vedtaksperiodeId: UUID,
    val id: UUID,
    commandContextRepository: CommandContextRepository,
    oppgaveService: OppgaveService,
    reservasjonRepository: ReservasjonRepository,
    tildelingRepository: TildelingRepository,
    oppgaveRepository: OppgaveRepository,
    totrinnsvurderingMediator: TotrinnsvurderingMediator,
) : MacroCommand() {
    override val commands: List<Command> =
        listOf(
            AvbrytCommand(
                fødselsnummer = fødselsnummer,
                vedtaksperiodeId = vedtaksperiodeId,
                commandContextRepository = commandContextRepository,
                oppgaveService = oppgaveService,
                reservasjonRepository = reservasjonRepository,
                tildelingRepository = tildelingRepository,
                oppgaveRepository = oppgaveRepository,
                totrinnsvurderingMediator = totrinnsvurderingMediator,
            ),
        )
}
