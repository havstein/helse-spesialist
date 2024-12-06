package no.nav.helse.spesialist.api.periodehistorikk

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.time.LocalDateTime

@JsonIgnoreProperties
data class PeriodehistorikkDto(
    val id: Int,
    val type: PeriodehistorikkType,
    val timestamp: LocalDateTime,
    val saksbehandlerIdent: String?,
    val dialogRef: Int?,
    val json: String,
)

enum class PeriodehistorikkType {
    TOTRINNSVURDERING_TIL_GODKJENNING,
    TOTRINNSVURDERING_RETUR,
    TOTRINNSVURDERING_ATTESTERT,
    VEDTAKSPERIODE_REBEREGNET,
    LEGG_PA_VENT,
    ENDRE_PA_VENT,
    FJERN_FRA_PA_VENT,
    STANS_AUTOMATISK_BEHANDLING,
}
