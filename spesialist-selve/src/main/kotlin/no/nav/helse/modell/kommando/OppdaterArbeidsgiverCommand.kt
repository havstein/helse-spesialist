package no.nav.helse.modell.kommando

import no.nav.helse.db.AvviksvurderingDao
import no.nav.helse.modell.arbeidsgiver.ArbeidsgiverDao
import no.nav.helse.modell.arbeidsgiver.Arbeidsgiverinformasjonløsning
import no.nav.helse.modell.person.HentPersoninfoløsninger
import org.slf4j.LoggerFactory
import java.time.LocalDate

internal class OppdaterArbeidsgiverCommand(
    val fødselsnummer: String,
    orgnummere: List<String>,
    val skjæringstidspunkt: LocalDate,
    private val arbeidsgiverDao: ArbeidsgiverDao,
    val avviksvurderingDao: AvviksvurderingDao,
) : Command {
    private companion object {
        private val log = LoggerFactory.getLogger(OppdaterArbeidsgiverCommand::class.java)
    }

    private val orgnummere = orgnummere.filter { it.length == 9 }
    private val personidenter = orgnummere.filter { it.length > 9 }

    private val andreArbeidsgivereISammenligningsgrunnlaget: Set<String> by lazy {
        val fødselsnummer = fødselsnummer
        val avviksvurderinger =
            avviksvurderingDao.finnAvviksvurderinger(fødselsnummer).find { it.skjæringstidspunkt == skjæringstidspunkt }
                ?: return@lazy emptySet()
        val alleOrgnumre =
            avviksvurderinger
                .sammenligningsgrunnlag
                .innrapporterteInntekter
                .map { it.arbeidsgiverreferanse }
                .toSet()
        alleOrgnumre - orgnummere.toSet()
    }

    override fun execute(context: CommandContext) =
        when {
            (ikkeOppdaterteBransjer() + ikkeOppdaterteNavn() + ikkeOppdaterteNavnForPersonidenter()).isEmpty() -> true
            else -> behandle(context)
        }

    override fun resume(context: CommandContext): Boolean {
        return behandle(context)
    }

    private fun ikkeOppdaterteNavn() =
        (orgnummere + andreArbeidsgivereISammenligningsgrunnlaget).filterNot { orgnummer ->
            arbeidsgiverDao.findNavnSistOppdatert(orgnummer)?.innenforSisteFjortenDager() ?: false
        }

    private fun ikkeOppdaterteBransjer() =
        orgnummere.filterNot { orgnummer ->
            arbeidsgiverDao.findBransjerSistOppdatert(orgnummer)?.innenforSisteFjortenDager() ?: false
        }

    private fun ikkeOppdaterteNavnForPersonidenter() =
        personidenter.filterNot { personlignr ->
            arbeidsgiverDao.findNavnSistOppdatert(personlignr)?.innenforSisteFjortenDager() ?: false
        }

    private fun behandle(context: CommandContext): Boolean {
        val løsning = context.get<Arbeidsgiverinformasjonløsning>()
        context.get<HentPersoninfoløsninger>()?.also { personinfo ->
            log.info("oppretter arbeidsgiver fra personer")
            personinfo.opprett(arbeidsgiverDao)
        }

        if (
            løsning == null ||
            !løsning.harSvarForAlle(ikkeOppdaterteBransjer() + ikkeOppdaterteNavn()) ||
            ikkeOppdaterteNavnForPersonidenter().isNotEmpty()
        ) {
            return trengerMerInformasjon(context)
        }

        løsning.oppdater(arbeidsgiverDao)

        return true
    }

    private fun trengerMerInformasjon(context: CommandContext): Boolean {
        val ikkeOppdaterteArbeidsgivere = (ikkeOppdaterteBransjer() + ikkeOppdaterteNavn()).distinct()
        val ikkeOppdatertePersonArbeidsgivere = ikkeOppdaterteNavnForPersonidenter().distinct()

        if (ikkeOppdaterteArbeidsgivere.isNotEmpty()) {
            context.behov(
                "Arbeidsgiverinformasjon",
                mapOf("organisasjonsnummer" to ikkeOppdaterteArbeidsgivere),
            )
        }
        if (ikkeOppdatertePersonArbeidsgivere.isNotEmpty()) {
            context.behov("HentPersoninfoV2", mapOf("ident" to ikkeOppdatertePersonArbeidsgivere))
        }

        return false
    }

    private fun LocalDate.innenforSisteFjortenDager() = this > LocalDate.now().minusDays(14)
}
