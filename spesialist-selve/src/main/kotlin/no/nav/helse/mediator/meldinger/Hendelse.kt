package no.nav.helse.mediator.meldinger

import java.util.UUID

internal interface Hendelse {
    val id: UUID
    fun toJson(): String
}

internal interface Personhendelse: Hendelse {
    fun fødselsnummer(): String
}

internal interface Vedtaksperiodehendelse: Personhendelse {
    fun vedtaksperiodeId(): UUID
}