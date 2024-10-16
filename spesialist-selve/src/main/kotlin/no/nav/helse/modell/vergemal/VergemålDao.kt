package no.nav.helse.modell.vergemal

import kotliquery.Session
import kotliquery.queryOf
import no.nav.helse.db.FleksibelDao
import no.nav.helse.db.Flexi
import no.nav.helse.db.VergemålRepository
import org.intellij.lang.annotations.Language
import java.time.LocalDateTime
import javax.sql.DataSource

data class VergemålOgFremtidsfullmakt(
    val harVergemål: Boolean,
    val harFremtidsfullmakter: Boolean,
)

class VergemålDao(val dataSource: DataSource?, val session: Session? = null) :
    VergemålRepository,
    FleksibelDao by Flexi(dataSource, session) {
    override fun lagre(
        fødselsnummer: String,
        vergemålOgFremtidsfullmakt: VergemålOgFremtidsfullmakt,
        fullmakt: Boolean,
    ) = nySessionEllerTransacation { lagre(fødselsnummer, vergemålOgFremtidsfullmakt, fullmakt) }

    private fun Session.lagre(
        fødselsnummer: String,
        vergemålOgFremtidsfullmakt: VergemålOgFremtidsfullmakt,
        fullmakt: Boolean,
    ) {
        @Language("PostgreSQL")
        val statement = """
            INSERT INTO vergemal (person_ref, har_vergemal, har_fremtidsfullmakter, har_fullmakter, vergemål_oppdatert, fullmakt_oppdatert)
            VALUES (
                (SELECT id FROM person WHERE fodselsnummer = :fodselsnummer),
                :har_vergemal,
                :har_fremtidsfullmakter,
                :har_fullmakter,
                :oppdatert,
                :oppdatert
            )
            ON CONFLICT (person_ref)
            DO UPDATE SET
                har_vergemal = :har_vergemal,
                har_fremtidsfullmakter = :har_fremtidsfullmakter,
                har_fullmakter = :har_fullmakter,
                vergemål_oppdatert = :oppdatert,
                fullmakt_oppdatert = :oppdatert
        """
        run(
            queryOf(
                statement,
                mapOf(
                    "fodselsnummer" to fødselsnummer.toLong(),
                    "har_vergemal" to vergemålOgFremtidsfullmakt.harVergemål,
                    "har_fremtidsfullmakter" to vergemålOgFremtidsfullmakt.harFremtidsfullmakter,
                    "har_fullmakter" to fullmakt,
                    "oppdatert" to LocalDateTime.now(),
                ),
            ).asExecute,
        )
    }

    override fun harVergemål(fødselsnummer: String): Boolean? = nySessionEllerTransacation { harVergemål(fødselsnummer) }

    private fun Session.harVergemål(fødselsnummer: String): Boolean? {
        @Language("PostgreSQL")
        val query =
            """
            SELECT har_vergemal
            FROM vergemal v
                INNER JOIN person p on p.id = v.person_ref
            WHERE p.fodselsnummer = :fodselsnummer
            """.trimIndent()
        return this.run(
            queryOf(
                query,
                mapOf("fodselsnummer" to fødselsnummer.toLong()),
            )
                .map { it.boolean("har_vergemal") }
                .asSingle,
        )
    }
}
