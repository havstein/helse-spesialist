package no.nav.helse.modell.saksbehandler.handlinger

import no.nav.helse.modell.oppgave.Oppgave
import no.nav.helse.modell.saksbehandler.Saksbehandler
import java.util.UUID

interface Handling {
    fun utførAv(saksbehandler: Saksbehandler)

    fun loggnavn(): String
}

interface Personhandling : Handling {
    fun gjelderFødselsnummer(): String

    fun begrunnelse(): String
}

interface Overstyring : Handling {
    val id: UUID

    fun gjelderFødselsnummer(): String
}

abstract class Oppgavehandling(private val oppgaveId: Long) : Handling {
    protected lateinit var oppgave: Oppgave

    fun oppgaveId(): Long = oppgaveId

    fun oppgave(oppgave: Oppgave) {
        this.oppgave = oppgave
    }
}
