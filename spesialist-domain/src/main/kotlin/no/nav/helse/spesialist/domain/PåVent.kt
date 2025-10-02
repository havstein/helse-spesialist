package no.nav.helse.spesialist.domain

import no.nav.helse.spesialist.domain.ddd.AggregateRoot
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@JvmInline
value class PåVentId(
    val value: Int,
)

class PåVent private constructor(
    id: PåVentId?,
    val vedtaksperiodeId: UUID,
    val saksbehandlerOid: SaksbehandlerOid,
    val frist: LocalDate,
    val opprettetTidspunkt: Instant,
    val dialogRef: DialogId?,
    val årsaker: List<String>,
    val notattekst: String?,
) : AggregateRoot<PåVentId>(id) {
    object Factory {
        fun fraLagring(
            id: PåVentId?,
            vedtaksperiodeId: UUID,
            saksbehandlerOid: SaksbehandlerOid,
            frist: LocalDate,
            opprettetTidspunkt: Instant,
            dialogRef: DialogId?,
            årsaker: List<String>,
            notattekst: String?,
        ) = PåVent(
            id = id,
            vedtaksperiodeId = vedtaksperiodeId,
            saksbehandlerOid = saksbehandlerOid,
            frist = frist,
            opprettetTidspunkt = opprettetTidspunkt,
            dialogRef = dialogRef,
            årsaker = årsaker,
            notattekst = notattekst,
        )
    }
}
