package no.nav.helse.modell.kommando

import no.nav.helse.bootstrap.Environment
import no.nav.helse.db.PersonRepository
import no.nav.helse.db.SnapshotRepository
import no.nav.helse.spesialist.api.snapshot.ISnapshotClient
import org.slf4j.LoggerFactory

internal class OppdaterSnapshotCommand(
    private val snapshotClient: ISnapshotClient,
    private val snapshotRepository: SnapshotRepository,
    private val fødselsnummer: String,
    private val personRepository: PersonRepository,
) : Command {
    private companion object {
        private val logg = LoggerFactory.getLogger(OppdaterSnapshotCommand::class.java)
        private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
        private val env = Environment()
    }

    override fun execute(context: CommandContext): Boolean {
        if (env.erDev) {
            sikkerlogg.info("Tester å aldri hente snapshot som følge av meldinger fra rapiden :zap: :earth:")
            return true
        }
        // findPersoninfoRef for å se om vi kun har minimal person
        return if (
            personRepository.finnPersonMedFødselsnummer(fødselsnummer) != null &&
            personRepository.finnPersoninfoRef(fødselsnummer) != null
        ) {
            oppdaterSnapshot()
        } else {
            ignorer()
        }
    }

    private fun oppdaterSnapshot(): Boolean {
        logg.info("Oppdaterer snapshot")
        sikkerlogg.info("Oppdaterer snapshot fødselsnummer=$fødselsnummer")
        return snapshotClient.hentSnapshot(fnr = fødselsnummer).data?.person?.let { person ->
            snapshotRepository.lagre(fødselsnummer = fødselsnummer, snapshot = person)
            true
        } ?: run {
            logg.warn("Kunne ikke hente snapshot - dette betyr at kommandokjeden stopper opp")
            false
        }
    }

    private fun ignorer(): Boolean {
        logg.info("Kjenner ikke til person, henter ikke snapshot (se sikkerlogg for detaljer)")
        sikkerlogg.info("Kjenner ikke til person fødselsnummer=$fødselsnummer, henter ikke snapshot")
        return true
    }
}
