package no.nav.helse.spesialist.api.vedtak

import java.time.LocalDate
import java.util.UUID
import no.nav.helse.spesialist.api.varsel.Varsel

internal data class Vedtaksperiode(
    private val vedtaksperiodeId: UUID,
    private val fom: LocalDate,
    private val tom: LocalDate,
    private val skjæringstidspunkt: LocalDate,
    private val varsler: Set<Varsel>
) {
    internal fun vedtaksperiodeId() = this.vedtaksperiodeId

    internal fun tidligereEnnOgSammenhengende(other: Vedtaksperiode): Boolean = this.fom <= other.tom && this.skjæringstidspunkt == other.skjæringstidspunkt

    private fun harAktiveVarsler(): Boolean {
        return varsler.any { it.erAktiv() }
    }

    companion object {
        fun Set<Vedtaksperiode>.harAktiveVarsler(): Boolean {
            return any { it.harAktiveVarsler() }
        }

        fun Set<Vedtaksperiode>.godkjennVurderteVarsler(
            godkjenningsbehovId: UUID,
            vedtaksperiodeIdTilGodkjenning: UUID,
            godkjenner: (godkjenningsbehovId: UUID, vedtaksperiodeIdTilGodkjenning: UUID, vedtaksperiodeId: UUID, varselId: UUID, varselTittel: String, varselkode: String) -> Unit
        ) {
            forEach { vedtaksperiode ->
                vedtaksperiode.varsler
                    .filter { it.erVurdert() }
                    .forEach {
                        it.godkjenn(
                            godkjenningsbehovId,
                            vedtaksperiodeIdTilGodkjenning,
                            vedtaksperiode.vedtaksperiodeId,
                            godkjenner
                        )
                    }
            }
        }
    }
}