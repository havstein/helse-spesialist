package no.nav.helse.mediator.meldinger

import kotliquery.TransactionalSession
import no.nav.helse.mediator.Kommandostarter
import no.nav.helse.modell.person.Person
import java.util.UUID

interface Melding {
    val id: UUID

    fun toJson(): String
}

interface Personmelding : Melding {
    fun behandle(
        person: Person,
        kommandostarter: Kommandostarter,
        transactionalSession: TransactionalSession,
    )

    fun fødselsnummer(): String
}

internal interface Vedtaksperiodemelding : Personmelding {
    fun vedtaksperiodeId(): UUID
}
