package no.nav.helse.mediator.meldinger

import java.util.UUID
import no.nav.helse.modell.kommando.Command

internal interface Hendelse : Command {
    val id: UUID

    fun fødselsnummer(): String
    fun vedtaksperiodeId(): UUID? = null
    fun tracinginfo(): Map<String, Any> = emptyMap()
    fun toJson(): String
}

