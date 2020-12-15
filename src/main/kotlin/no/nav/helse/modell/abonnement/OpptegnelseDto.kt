package no.nav.helse.modell.abonnement

import java.util.*

data class OpptegnelseDto(
    val aktørId: Long,
    val sekvensnummer: Int,
    val type: OpptegnelseType,
    val payload: String
)

enum class OpptegnelseType {
    UTBETALING_ANNULLERING_FEILET,
    ANNULLERING_FEILET,
    ANNULLERING_OK
}

sealed class PayloadToSpeil {
    abstract fun toJson(): String
}

data class UtbetalingPayload(val utbetalingId: UUID): PayloadToSpeil() {
    override fun toJson() = """{ "utbetalingId": "$utbetalingId" }"""
}
