package no.nav.helse.modell.risiko

import no.nav.helse.db.RisikovurderingRepository
import no.nav.helse.mediator.meldinger.løsninger.Risikovurderingløsning
import no.nav.helse.modell.behov.Behov
import no.nav.helse.modell.kommando.Command
import no.nav.helse.modell.kommando.CommandContext
import no.nav.helse.modell.sykefraværstilfelle.Sykefraværstilfelle
import no.nav.helse.modell.utbetaling.Utbetaling
import no.nav.helse.modell.varsel.Varselkode.SB_RV_1
import org.slf4j.LoggerFactory
import java.util.UUID

internal class VurderVurderingsmomenter(
    private val vedtaksperiodeId: UUID,
    private val risikovurderingRepository: RisikovurderingRepository,
    private val organisasjonsnummer: String,
    private val førstegangsbehandling: Boolean,
    private val sykefraværstilfelle: Sykefraværstilfelle,
    private val utbetaling: Utbetaling,
) : Command {
    override fun execute(context: CommandContext) = behandle(context)

    override fun resume(context: CommandContext) = behandle(context)

    private fun behandle(context: CommandContext): Boolean {
        if (risikovurderingAlleredeGjort()) return true

        val løsning = context.get<Risikovurderingløsning>()
        if (løsning == null || !løsning.gjelderVedtaksperiode(vedtaksperiodeId)) {
            logg.info("Trenger risikovurdering av vedtaksperiode $vedtaksperiodeId")
            context.behov(
                Behov.Risikovurdering(
                    vedtaksperiodeId,
                    organisasjonsnummer,
                    førstegangsbehandling,
                    !utbetaling.harEndringIUtbetalingTilSykmeldt(),
                ),
            )
            return false
        }

        løsning.lagre(risikovurderingRepository)
        løsning.leggTilVarsler()
        return true
    }

    private fun risikovurderingAlleredeGjort() = risikovurderingRepository.hentRisikovurdering(vedtaksperiodeId) != null

    private fun Risikovurderingløsning.leggTilVarsler() {
        if (harArbeidsuførhetFunn()) {
            sykefraværstilfelle.håndter(varselkode().nyttVarsel(vedtaksperiodeId))
        }
        if (harFaresignalerFunn()) {
            sykefraværstilfelle.håndter(SB_RV_1.nyttVarsel(vedtaksperiodeId))
        }
    }

    private companion object {
        private val logg = LoggerFactory.getLogger(VurderVurderingsmomenter::class.java)
    }
}
