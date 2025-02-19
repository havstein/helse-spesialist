package no.nav.helse.db

import java.time.LocalDateTime
import java.util.UUID

interface SaksbehandlerDao {
    fun opprettEllerOppdater(
        oid: UUID,
        navn: String,
        epost: String,
        ident: String,
    ): Int

    fun oppdaterSistObservert(
        oid: UUID,
        sisteHandlingUtført: LocalDateTime = LocalDateTime.now(),
    ): Int
}
