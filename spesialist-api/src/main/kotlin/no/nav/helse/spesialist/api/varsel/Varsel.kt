package no.nav.helse.spesialist.api.varsel

import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.spesialist.api.graphql.schema.VarselDTO
import no.nav.helse.spesialist.api.graphql.schema.VarselDTO.VarselvurderingDTO

data class Varsel(
    private val generasjonId: UUID,
    private val definisjonId: UUID,
    private val kode: String,
    private val tittel: String,
    private val forklaring: String?,
    private val handling: String?,
    private val vurdering: Varselvurdering?,
) {
    internal companion object {
        internal fun List<Varsel>.toDto(): List<VarselDTO> {
            return map { it.toDto() }
        }
    }

    internal fun toDto() = VarselDTO(generasjonId.toString(), definisjonId.toString(), kode, tittel, forklaring, handling, vurdering?.toDto())

    data class Varselvurdering(
        private val ident: String,
        private val tidsstempel: LocalDateTime,
        private val status: Varselstatus,
    ) {
        internal fun toDto() = VarselvurderingDTO(ident, tidsstempel.toString(), status)
        override fun equals(other: Any?): Boolean =
            this === other || (other is Varselvurdering
            && javaClass == other.javaClass
            && ident == other.ident
            && status == other.status)

        override fun hashCode(): Int {
            var result = ident.hashCode()
            result = 31 * result + tidsstempel.hashCode()
            result = 31 * result + status.hashCode()
            return result
        }
    }

    enum class Varselstatus {
        AKTIV,
        VURDERT,
        GODKJENT,
        AVVIST,
    }
}