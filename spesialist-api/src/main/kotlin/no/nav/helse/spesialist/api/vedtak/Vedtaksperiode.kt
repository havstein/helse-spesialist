package no.nav.helse.spesialist.api.vedtak

import no.nav.helse.spesialist.api.varsel.Varsel
import java.time.LocalDate
import java.util.UUID

data class Vedtaksperiode(
    private val vedtaksperiodeId: UUID,
    private val fom: LocalDate,
    private val tom: LocalDate,
    private val skjæringstidspunkt: LocalDate,
    private val varsler: Set<Varsel>,
) {
    fun vedtaksperiodeId() = this.vedtaksperiodeId

    fun tidligereEnnOgSammenhengende(other: Vedtaksperiode): Boolean =
        this.fom <= other.tom && this.skjæringstidspunkt == other.skjæringstidspunkt

    private fun harAktiveVarsler(): Boolean {
        return varsler.any { it.erAktiv() }
    }

    companion object {
        fun Set<Vedtaksperiode>.harAktiveVarsler(): Boolean {
            return any { it.harAktiveVarsler() }
        }

        fun Set<Vedtaksperiode>.godkjennVarsler(
            fødselsnummer: String,
            behandlingId: UUID,
            ident: String,
            godkjenner: (
                fødselsnummer: String,
                behandlingId: UUID,
                vedtaksperiodeId: UUID,
                varselId: UUID,
                varselTittel: String,
                varselkode: String,
                forrigeStatus: Varsel.Varselstatus,
                gjeldendeStatus: Varsel.Varselstatus,
                saksbehandlerIdent: String,
            ) -> Unit,
        ) {
            forEach { vedtaksperiode ->
                vedtaksperiode.varsler.forEach {
                    it.vurder(
                        godkjent = true,
                        fødselsnummer = fødselsnummer,
                        behandlingId = behandlingId,
                        vedtaksperiodeId = vedtaksperiode.vedtaksperiodeId,
                        ident = ident,
                        vurderer = godkjenner,
                    )
                }
            }
        }

        fun Vedtaksperiode.avvisVarsler(
            fødselsnummer: String,
            behandlingId: UUID,
            ident: String,
            godkjenner: (
                fødselsnummer: String,
                behandlingId: UUID,
                vedtaksperiodeId: UUID,
                varselId: UUID,
                varselTittel: String,
                varselkode: String,
                forrigeStatus: Varsel.Varselstatus,
                gjeldendeStatus: Varsel.Varselstatus,
                saksbehandlerIdent: String,
            ) -> Unit,
        ) {
            this.varsler.forEach {
                it.vurder(
                    godkjent = false,
                    fødselsnummer = fødselsnummer,
                    behandlingId = behandlingId,
                    vedtaksperiodeId = this.vedtaksperiodeId,
                    ident = ident,
                    vurderer = godkjenner,
                )
            }
        }
    }
}
