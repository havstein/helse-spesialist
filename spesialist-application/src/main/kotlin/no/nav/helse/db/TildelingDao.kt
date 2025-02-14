package no.nav.helse.db

import java.util.UUID

interface TildelingDao {
    fun tildelingForPerson(fødselsnummer: String): TildelingDto?

    fun tildelingForOppgave(oppgaveId: Long): TildelingDto?

    fun tildel(
        oppgaveId: Long,
        saksbehandlerOid: UUID,
    )

    fun avmeld(oppgaveId: Long)
}
