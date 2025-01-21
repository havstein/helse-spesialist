package no.nav.helse.db

import kotliquery.TransactionalSession
import no.nav.helse.mediator.Kommandostarter
import no.nav.helse.mediator.meldinger.Vedtaksperiodemelding
import no.nav.helse.modell.person.Person
import java.util.UUID

internal class TestMelding(
    override val id: UUID,
    private val vedtaksperiodeId: UUID,
    private val fnr: String,
    private val json: String = "{}"
) : Vedtaksperiodemelding {
    override fun fødselsnummer(): String = fnr

    override fun vedtaksperiodeId(): UUID = vedtaksperiodeId
    override fun behandle(
        person: Person,
        kommandostarter: Kommandostarter,
        transactionalSession: TransactionalSession
    ) {
    }

    override fun toJson() = json
}
