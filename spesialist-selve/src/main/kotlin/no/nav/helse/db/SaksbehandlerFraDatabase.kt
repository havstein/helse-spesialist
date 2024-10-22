package no.nav.helse.db

import no.nav.helse.modell.saksbehandler.SaksbehandlerDto
import java.util.UUID

data class SaksbehandlerFraDatabase(
    val epostadresse: String,
    val oid: UUID,
    val navn: String,
    val ident: String,
)

fun SaksbehandlerFraDatabase.toDto() = SaksbehandlerDto(epostadresse, oid, navn, ident)
