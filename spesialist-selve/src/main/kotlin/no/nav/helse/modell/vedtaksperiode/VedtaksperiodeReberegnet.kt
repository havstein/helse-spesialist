package no.nav.helse.modell.vedtaksperiode

import com.fasterxml.jackson.databind.JsonNode
import kotliquery.TransactionalSession
import no.nav.helse.db.CommandContextRepository
import no.nav.helse.db.OppgaveRepository
import no.nav.helse.db.PeriodehistorikkRepository
import no.nav.helse.db.ReservasjonRepository
import no.nav.helse.db.TildelingRepository
import no.nav.helse.db.UtbetalingRepository
import no.nav.helse.mediator.Kommandostarter
import no.nav.helse.mediator.asUUID
import no.nav.helse.mediator.meldinger.Vedtaksperiodemelding
import no.nav.helse.mediator.oppgave.OppgaveService
import no.nav.helse.modell.kommando.AvbrytCommand
import no.nav.helse.modell.kommando.Command
import no.nav.helse.modell.kommando.MacroCommand
import no.nav.helse.modell.kommando.ReserverPersonHvisTildeltCommand
import no.nav.helse.modell.kommando.VedtaksperiodeReberegnetPeriodehistorikk
import no.nav.helse.modell.person.Person
import no.nav.helse.modell.totrinnsvurdering.TotrinnsvurderingMediator
import no.nav.helse.rapids_rivers.JsonMessage
import java.util.UUID

internal class VedtaksperiodeReberegnet private constructor(
    override val id: UUID,
    private val fødselsnummer: String,
    private val vedtaksperiodeId: UUID,
    private val json: String,
) : Vedtaksperiodemelding {
    internal constructor(packet: JsonMessage) : this(
        id = packet["@id"].asUUID(),
        fødselsnummer = packet["fødselsnummer"].asText(),
        vedtaksperiodeId = packet["vedtaksperiodeId"].asUUID(),
        json = packet.toJson(),
    )

    internal constructor(jsonNode: JsonNode) : this(
        id = UUID.fromString(jsonNode["@id"].asText()),
        fødselsnummer = jsonNode["fødselsnummer"].asText(),
        vedtaksperiodeId = UUID.fromString(jsonNode["vedtaksperiodeId"].asText()),
        json = jsonNode.toString(),
    )

    override fun fødselsnummer() = fødselsnummer

    override fun toJson(): String = json

    override fun vedtaksperiodeId(): UUID = vedtaksperiodeId

    override fun skalKjøresTransaksjonelt(): Boolean = true

    override fun transaksjonellBehandle(
        person: Person,
        kommandostarter: Kommandostarter,
        transactionalSession: TransactionalSession,
    ) {
        kommandostarter { vedtaksperiodeReberegnet(this@VedtaksperiodeReberegnet, transactionalSession) }
    }

    override fun behandle(
        person: Person,
        kommandostarter: Kommandostarter,
    ) {
        throw UnsupportedOperationException()
    }
}

internal class VedtaksperiodeReberegnetCommand(
    vedtaksperiodeId: UUID,
    fødselsnummer: String,
    utbetalingRepository: UtbetalingRepository,
    periodehistorikkRepository: PeriodehistorikkRepository,
    commandContextRepository: CommandContextRepository,
    oppgaveService: OppgaveService,
    reservasjonRepository: ReservasjonRepository,
    tildelingRepository: TildelingRepository,
    oppgaveRepository: OppgaveRepository,
    totrinnsvurderingMediator: TotrinnsvurderingMediator,
) : MacroCommand() {
    override val commands: List<Command> =
        listOf(
            VedtaksperiodeReberegnetPeriodehistorikk(
                vedtaksperiodeId = vedtaksperiodeId,
                utbetalingRepository = utbetalingRepository,
                periodehistorikkRepository = periodehistorikkRepository,
            ),
            ReserverPersonHvisTildeltCommand(
                fødselsnummer = fødselsnummer,
                reservasjonRepository = reservasjonRepository,
                tildelingRepository = tildelingRepository,
                oppgaveRepository = oppgaveRepository,
                totrinnsvurderingMediator = totrinnsvurderingMediator,
            ),
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
