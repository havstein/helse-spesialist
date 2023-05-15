package no.nav.helse.spesialist.api.varsel

import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.spesialist.api.graphql.schema.VarselDTO
import no.nav.helse.spesialist.api.graphql.schema.VarselDTO.VarselvurderingDTO
import no.nav.helse.spesialist.api.varsel.Varsel.Varselstatus.AKTIV
import no.nav.helse.spesialist.api.varsel.Varsel.Varselstatus.GODKJENT
import no.nav.helse.spesialist.api.varsel.Varsel.Varselstatus.VURDERT

data class Varsel(
    private val varselId: UUID,
    private val generasjonId: UUID,
    private val definisjonId: UUID,
    private val kode: String,
    private var status: Varselstatus,
    private val tittel: String,
    private val forklaring: String?,
    private val handling: String?,
    private val vurdering: Varselvurdering?,
) {
    internal companion object {
        internal fun Set<Varsel>.toDto(): Set<VarselDTO> {
            return map { it.toDto() }.toSet()
        }
    }

    internal fun toDto() = VarselDTO(
        generasjonId.toString(),
        definisjonId.toString(),
        kode,
        tittel,
        forklaring,
        handling,
        vurdering?.toDto(status)
    )

    internal fun erAktiv(): Boolean {
        return status == AKTIV
    }

    internal fun godkjenn(
        godkjenningsbehovId: UUID,
        vedtaksperiodeIdTilGodkjenning: UUID,
        vedtaksperiodeId: UUID,
        godkjenner: (godkjenningsbehovId: UUID, vedtaksperiodeIdTilGodkjenning: UUID, vedtaksperiodeId: UUID, varselId: UUID, varselTittel: String, varselkode: String) -> Unit,
    ) {
        if (status != VURDERT) return
        status = GODKJENT
        godkjenner(godkjenningsbehovId, vedtaksperiodeIdTilGodkjenning, vedtaksperiodeId, varselId, tittel, kode)
    }

    data class Varselvurdering(
        private val ident: String,
        private val tidsstempel: LocalDateTime,
    ) {

        internal fun toDto(status: Varselstatus): VarselvurderingDTO {
            return VarselvurderingDTO(
                ident,
                tidsstempel.toString(),
                no.nav.helse.spesialist.api.graphql.schema.Varselstatus.valueOf(status.name)
            )
        }
        override fun equals(other: Any?): Boolean =
            this === other || (other is Varselvurdering
                    && javaClass == other.javaClass
                    && ident == other.ident
                    && tidsstempel.withNano(0) == other.tidsstempel.withNano(0))

        override fun hashCode(): Int {
            var result = ident.hashCode()
            result = 31 * result + tidsstempel.withNano(0).hashCode()
            return result
        }
    }

    enum class Varselstatus {
        INAKTIV,
        AKTIV,
        VURDERT,
        GODKJENT,
        AVVIST,
    }
}