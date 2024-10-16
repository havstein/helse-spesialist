package no.nav.helse.modell.gosysoppgaver

import com.fasterxml.jackson.databind.JsonNode
import kotliquery.TransactionalSession
import no.nav.helse.db.OppgaveRepository
import no.nav.helse.db.TransactionalOppgaveDao
import no.nav.helse.db.ÅpneGosysOppgaverRepository
import no.nav.helse.mediator.GodkjenningMediator
import no.nav.helse.mediator.Kommandostarter
import no.nav.helse.mediator.asUUID
import no.nav.helse.mediator.meldinger.Personmelding
import no.nav.helse.mediator.oppgave.OppgaveService
import no.nav.helse.modell.automatisering.Automatisering
import no.nav.helse.modell.automatisering.ForsøkÅAutomatisereEksisterendeOppgave
import no.nav.helse.modell.automatisering.SettTidligereAutomatiseringInaktivCommand
import no.nav.helse.modell.kommando.Command
import no.nav.helse.modell.kommando.MacroCommand
import no.nav.helse.modell.oppgave.SjekkAtOppgaveFortsattErÅpenCommand
import no.nav.helse.modell.person.Person
import no.nav.helse.modell.sykefraværstilfelle.Sykefraværstilfelle
import no.nav.helse.modell.utbetaling.Utbetaling
import no.nav.helse.modell.vedtaksperiode.GodkjenningsbehovData
import no.nav.helse.rapids_rivers.JsonMessage
import java.util.UUID

internal class GosysOppgaveEndret private constructor(
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
        id = UUID.fromString(jsonNode["@id"].asText()),
        fødselsnummer = jsonNode["fødselsnummer"].asText(),
        json = jsonNode.toString(),
    )

    override fun behandle(
        person: Person,
        kommandostarter: Kommandostarter,
    ) = throw UnsupportedOperationException()

    override fun skalKjøresTransaksjonelt() = true

    override fun transaksjonellBehandle(
        person: Person,
        kommandostarter: Kommandostarter,
        transactionalSession: TransactionalSession,
    ) {
        kommandostarter {
            val oppgaveDataForAutomatisering =
                finnOppgavedata(
                    fødselsnummer,
                    TransactionalOppgaveDao(transactionalSession),
                ) ?: return@kommandostarter null
            gosysOppgaveEndret(person, oppgaveDataForAutomatisering, transactionalSession)
        }
    }

    override fun fødselsnummer() = fødselsnummer

    override fun toJson(): String = json
}

internal class GosysOppgaveEndretCommand(
    utbetaling: Utbetaling,
    sykefraværstilfelle: Sykefraværstilfelle,
    harTildeltOppgave: Boolean,
    oppgavedataForAutomatisering: OppgaveDataForAutomatisering,
    automatisering: Automatisering,
    åpneGosysOppgaverRepository: ÅpneGosysOppgaverRepository,
    oppgaveRepository: OppgaveRepository,
    oppgaveService: OppgaveService,
    godkjenningMediator: GodkjenningMediator,
    godkjenningsbehov: GodkjenningsbehovData,
) : MacroCommand() {
    override val commands: List<Command> =
        listOf(
            VurderÅpenGosysoppgave(
                aktørId = godkjenningsbehov.aktørId,
                åpneGosysOppgaverRepository = åpneGosysOppgaverRepository,
                vedtaksperiodeId = oppgavedataForAutomatisering.vedtaksperiodeId,
                sykefraværstilfelle = sykefraværstilfelle,
                harTildeltOppgave = harTildeltOppgave,
                oppgaveService = oppgaveService,
            ),
            SjekkAtOppgaveFortsattErÅpenCommand(
                fødselsnummer = godkjenningsbehov.fødselsnummer,
                oppgaveRepository = oppgaveRepository,
            ),
            SettTidligereAutomatiseringInaktivCommand(
                vedtaksperiodeId = oppgavedataForAutomatisering.vedtaksperiodeId,
                hendelseId = oppgavedataForAutomatisering.hendelseId,
                automatisering = automatisering,
            ),
            ForsøkÅAutomatisereEksisterendeOppgave(
                automatisering = automatisering,
                godkjenningMediator = godkjenningMediator,
                oppgaveService = oppgaveService,
                utbetaling = utbetaling,
                sykefraværstilfelle = sykefraværstilfelle,
                godkjenningsbehov = godkjenningsbehov,
            ),
        )
}
