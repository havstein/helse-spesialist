package no.nav.helse.modell.saksbehandler

import kotliquery.Session
import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.modell.command.invaliderSaksbehandlerOppgaver
import org.intellij.lang.annotations.Language
import java.util.*
import javax.sql.DataSource

internal class SaksbehandlerDao(private val dataSource: DataSource) {
    internal fun opprettSaksbehandler(
        oid: UUID,
        navn: String,
        epost: String
    ) = sessionOf(dataSource).use {
        it.persisterSaksbehandler(oid, navn, epost)
    }

    internal fun finnSaksbehandler(oid: UUID) = sessionOf(dataSource).use {
        it.finnSaksbehandler(oid)
    }

    internal fun invaliderSaksbehandlerOppgaver(fødselsnummer: String, orgnummer: String) =
        sessionOf(dataSource).use { it.invaliderSaksbehandlerOppgaver(fødselsnummer, orgnummer) }
}

fun Session.persisterSaksbehandler(
    oid: UUID,
    navn: String,
    epost: String
) {
    @Language("PostgreSQL")
    val opprettSaksbehandlerQuery = """
        INSERT INTO saksbehandler(oid, navn, epost)
        VALUES (:oid,
                :navn,
                :epost)
        ON CONFLICT DO NOTHING
    """

    run(
        queryOf(
            opprettSaksbehandlerQuery,
            mapOf(
                "oid" to oid,
                "navn" to navn,
                "epost" to epost
            )
        ).asUpdate
    )
}

fun Session.finnSaksbehandler(oid: UUID): List<SaksbehandlerDto> {
    @Language("PostgreSQL")
    val finnSaksbehandlerQuery = """
SELECT *
FROM saksbehandler
WHERE oid = ?
    """
    return this.run(queryOf(finnSaksbehandlerQuery, oid).map { saksbehandlerRow ->
        val oid = saksbehandlerRow.string("oid")

        SaksbehandlerDto(
            oid = UUID.fromString(oid),
            navn = saksbehandlerRow.string("navn"),
            epost = saksbehandlerRow.string("epost")
        )
    }.asList)
}


data class SaksbehandlerDto(
    val oid: UUID,
    val navn: String,
    val epost: String
)
