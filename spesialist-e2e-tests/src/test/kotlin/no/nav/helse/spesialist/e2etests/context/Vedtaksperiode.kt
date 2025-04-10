package no.nav.helse.spesialist.e2etests.context

import no.nav.helse.spesialist.domain.testfixtures.jan
import java.time.LocalDate
import java.util.UUID

data class Vedtaksperiode(
    val vedtaksperiodeId: UUID = UUID.randomUUID(),
    var spleisBehandlingId: UUID? = null,
    var utbetalingId: UUID? = null,
    var fom: LocalDate = 1 jan 2018,
    var tom: LocalDate = 31 jan 2018,
) {
    fun spleisBehandlingIdForÅByggeMelding(meldingsnavn: String): UUID =
        spleisBehandlingId
            ?: error("Feil i testoppsett: Forsøkte å lage en $meldingsnavn-melding før spleisBehandlingId var satt")

    fun utbetalingIdForÅByggeMelding(meldingsnavn: String): UUID =
        utbetalingId
            ?: error("Feil i testoppsett: Forsøkte å lage en $meldingsnavn-melding før utbetalingId var satt")

    fun nyUtbetaling() { utbetalingId = UUID.randomUUID() }
}
