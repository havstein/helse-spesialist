package no.nav.helse.mediator.meldinger

import com.fasterxml.jackson.databind.JsonNode
import kotliquery.TransactionalSession
import no.nav.helse.db.OppgaveRepository
import no.nav.helse.db.PersonRepository
import no.nav.helse.mediator.GodkjenningMediator
import no.nav.helse.mediator.Kommandostarter
import no.nav.helse.mediator.asUUID
import no.nav.helse.modell.kommando.AvvisVedStrengtFortroligAdressebeskyttelseCommand
import no.nav.helse.modell.kommando.Command
import no.nav.helse.modell.kommando.MacroCommand
import no.nav.helse.modell.kommando.OppdaterPersoninfoCommand
import no.nav.helse.modell.person.Person
import no.nav.helse.modell.utbetaling.Utbetaling
import no.nav.helse.modell.vedtaksperiode.GodkjenningsbehovData
import no.nav.helse.rapids_rivers.JsonMessage
import java.util.UUID
import javax.naming.OperationNotSupportedException

internal class AdressebeskyttelseEndret private constructor(
    override val id: UUID,
    private val fødselsnummer: String,
    private val json: String,
) : Personmelding {
    internal constructor(packet: JsonMessage) : this(
        id = packet["@id"].asUUID(),
        fødselsnummer = packet["fødselsnummer"].asText(),
        json = packet.toJson(),
    )

    internal constructor(jsonNode: JsonNode) : this(
        id = jsonNode["@id"].asUUID(),
        fødselsnummer = jsonNode["fødselsnummer"].asText(),
        json = jsonNode.toString(),
    )

    override fun fødselsnummer(): String = fødselsnummer

    override fun toJson(): String = json

    override fun behandle(
        person: Person,
        kommandostarter: Kommandostarter,
    ) {
        throw OperationNotSupportedException()
    }

    override fun skalKjøresTransaksjonelt(): Boolean = true

    override fun transaksjonellBehandle(
        person: Person,
        kommandostarter: Kommandostarter,
        transactionalSession: TransactionalSession,
    ) {
        kommandostarter {
            adressebeskyttelseEndret(
                this@AdressebeskyttelseEndret,
                finnOppgavedata(fødselsnummer),
                transactionalSession,
            )
        }
    }
}

internal class AdressebeskyttelseEndretCommand(
    fødselsnummer: String,
    personRepository: PersonRepository,
    oppgaveRepository: OppgaveRepository,
    godkjenningMediator: GodkjenningMediator,
    godkjenningsbehov: GodkjenningsbehovData?,
    utbetaling: Utbetaling?,
) : MacroCommand() {
    override val commands: List<Command> =
        mutableListOf<Command>(
            OppdaterPersoninfoCommand(fødselsnummer, personRepository, force = true),
        ).apply {
            if (godkjenningsbehov != null) {
                check(utbetaling != null) { "Forventer å finne utbetaling for godkjenningsbehov med id=${godkjenningsbehov.id}" }
                add(
                    AvvisVedStrengtFortroligAdressebeskyttelseCommand(
                        personRepository = personRepository,
                        oppgaveRepository = oppgaveRepository,
                        godkjenningMediator = godkjenningMediator,
                        godkjenningsbehov = godkjenningsbehov,
                        utbetaling = utbetaling,
                    ),
                )
            }
        }
}
