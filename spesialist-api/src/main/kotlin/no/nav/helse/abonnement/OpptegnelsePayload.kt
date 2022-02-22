package no.nav.helse.abonnement

import java.util.*

sealed class OpptegnelsePayload {
    abstract fun toJson(): String
}

data class UtbetalingPayload(private val utbetalingId: UUID) : OpptegnelsePayload() {
    override fun toJson() = """
        { "utbetalingId": "$utbetalingId" }
    """.trimIndent()
}

data class GodkjenningsbehovPayload(private val hendelseId: UUID) : OpptegnelsePayload() {
    override fun toJson() = """
        { "hendelseId": "$hendelseId" }
    """.trimIndent()
    companion object {
        fun GodkjenningsbehovPayload.lagre(opptegnelseDao: OpptegnelseDao, fødselsnummer: String) {
            opptegnelseDao.opprettOpptegnelse(
                fødselsnummer = fødselsnummer,
                payload = this,
                // Dette er et litt misvisende navn, opprettes både når oppgave opprettes, men også når noe avvises/betales automatisk
                type = OpptegnelseType.NY_SAKSBEHANDLEROPPGAVE
            )
        }
    }
}

data class RevurderingAvvistPayload(private val hendelseId: UUID, private val errors: List<String>) :
    OpptegnelsePayload() {
    override fun toJson() = """
        { "hendelseId": "$hendelseId", "errors": ${errors.map{ "\"$it\""}} }
    """.trimIndent()
}
