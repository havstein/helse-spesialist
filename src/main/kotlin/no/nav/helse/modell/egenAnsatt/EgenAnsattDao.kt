package no.nav.helse.modell.egenAnsatt

import kotliquery.queryOf
import kotliquery.sessionOf
import org.intellij.lang.annotations.Language
import javax.sql.DataSource

internal class EgenAnsattDao(private val dataSource: DataSource) {
    internal fun persisterEgenAnsatt(egenAnsattDto: EgenAnsattDto) {
        @Language("PostgreSQL")
        val query = """
            INSERT INTO egen_ansatt(person_ref, er_egen_ansatt, opprettet)
            VALUES ((SELECT id FROM person WHERE fodselsnummer = :fodselsnummer), :er_egen_ansatt, :opprettet)
                ON CONFLICT (person_ref) DO UPDATE SET er_egen_ansatt = :er_egen_ansatt, opprettet = :opprettet
            """
        sessionOf(dataSource).use { session ->
            session.run(
                queryOf(
                    query,
                    mapOf(
                        "fodselsnummer" to egenAnsattDto.fødselsnummer.toLong(),
                        "er_egen_ansatt" to egenAnsattDto.erEgenAnsatt,
                        "opprettet" to egenAnsattDto.opprettet
                    )
                ).asExecute
            )
        }
    }

    internal fun erEgenAnsatt(fødselsnummer: String): Boolean? {
        @Language("PostgreSQL")
        val query = """
            SELECT er_egen_ansatt
                FROM egen_ansatt ea
                    INNER JOIN person p on p.id = ea.person_ref
                WHERE p.fodselsnummer = :fodselsnummer
            """
        return sessionOf(dataSource).use { session ->
            session.run(
                queryOf(
                    query,
                    mapOf(
                        "fodselsnummer" to fødselsnummer.toLong()
                    )
                )
                    .map { it.boolean("er_egen_ansatt") }
                    .asSingle
            )
        }
    }
}
