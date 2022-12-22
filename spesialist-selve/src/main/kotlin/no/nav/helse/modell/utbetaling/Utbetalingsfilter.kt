package no.nav.helse.modell.utbetaling

import no.nav.helse.mediator.Toggle
import no.nav.helse.modell.utbetaling.Utbetalingtype.REVURDERING
import no.nav.helse.modell.vedtak.Warning
import no.nav.helse.modell.vedtaksperiode.Inntektskilde
import no.nav.helse.modell.vedtaksperiode.Inntektskilde.EN_ARBEIDSGIVER
import no.nav.helse.modell.vedtaksperiode.Periodetype
import no.nav.helse.modell.vedtaksperiode.Periodetype.FORLENGELSE
import no.nav.helse.modell.vedtaksperiode.Periodetype.FØRSTEGANGSBEHANDLING
import org.slf4j.LoggerFactory

internal class Utbetalingsfilter(
    private val fødselsnummer: String,
    private val delvisRefusjon: Boolean,
    private val erUtbetaltFør: Boolean,
    private val harUtbetalingTilSykmeldt: Boolean,
    private val periodetype: Periodetype,
    private val inntektskilde: Inntektskilde,
    private val warnings: List<Warning>,
    private val utbetalingtype: Utbetalingtype,
    private val harVedtaksperiodePågåendeOverstyring: Boolean,
) {
    private val årsaker = mutableListOf<String>()
    private fun nyÅrsak(årsak: String) = årsaker.add("Brukerutbetalingsfilter: $årsak")

    private fun evaluer(): Boolean{
        if (!harUtbetalingTilSykmeldt) return true // Full refusjon / ingen utbetaling kan alltid utbetales
        if (!harVedtaksperiodePågåendeOverstyring) {
            if (delvisRefusjon) nyÅrsak("Utbetalingen består av delvis refusjon")
            if (!fødselsnummer.startsWith("31")) nyÅrsak("Velges ikke ut som 'to om dagen'") // Kvoteregulering
        }
        if (periodetype !in tillatePeriodetyper) nyÅrsak("Perioden er ikke førstegangsbehandling eller forlengelse")
        if (inntektskilde != EN_ARBEIDSGIVER) nyÅrsak("Inntektskilden er ikke for en arbeidsgiver")
        // Unngå ping-pong om en av de utvalgte utbetalingene til sykmeldt revurderes og får warning
        if (warnings.isNotEmpty() && utbetalingtype != REVURDERING) {
            if (årsaker.isEmpty()) sikkerLogg.info("Utbetalingsfilter warnings som eneste årsak til at det ikke kan utbetales:\n${Warning.formater(warnings).joinToString(separator = "\n")}")
            else sikkerLogg.info("Utbetalingsfilter warnings som en av flere årsaker til at det ikke kan utbetales:\n${Warning.formater(warnings).joinToString(separator = "\n")}")
            nyÅrsak("Vedtaksperioden har warnings")
        }
        if (årsaker.isNotEmpty() && erUtbetaltFør) {
            if (Toggle.BeholdForlengelseMedOvergangTilUTS.enabled) {
                return true
            }
            sikkerLogg.info("Kandidat for å beholde hos oss. Avvisningsgrunner: ${årsaker.joinToString()}")
        }
        return årsaker.isEmpty()
    }

    internal val kanUtbetales by lazy { evaluer() }
    internal val kanIkkeUtbetales get() = !kanUtbetales
    internal val plukketUtForUtbetalingTilSykmeldt get() = kanUtbetales && harUtbetalingTilSykmeldt && (utbetalingtype != REVURDERING || harVedtaksperiodePågåendeOverstyring)

    internal fun årsaker(): List<String> {
        require(kanIkkeUtbetales) { "Årsaker skal kun brukes for vedtaksperioder vi ikke kan utbetale" }
        require(årsaker.isNotEmpty()) { "Må være minst en årsak til at vi ikke kan utbetale en vedtaksperiode" }
        return årsaker
    }

    private companion object {
        private val sikkerLogg = LoggerFactory.getLogger("tjenestekall")
        private val tillatePeriodetyper = setOf(FØRSTEGANGSBEHANDLING, FORLENGELSE)
    }
}
