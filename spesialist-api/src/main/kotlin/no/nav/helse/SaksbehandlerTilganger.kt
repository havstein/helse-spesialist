package no.nav.helse

import java.util.*

class SaksbehandlerTilganger(
    private val gruppetilganger: List<UUID>,
    private val kode7Saksbehandlergruppe: UUID,
    private val riskSaksbehandlergruppe: UUID,
    private val NAVident: String = ""
) {

    fun harTilgangTilKode7Oppgaver() = kode7Saksbehandlergruppe in gruppetilganger

    fun harTilgangTilRiskOppgaver() = riskSaksbehandlergruppe in gruppetilganger

    fun kanSeAlleOppgaver() = NAVident == "N115007"
}
