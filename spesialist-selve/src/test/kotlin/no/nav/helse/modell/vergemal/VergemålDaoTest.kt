package no.nav.helse.modell.vergemal

import DatabaseIntegrationTest
import kotliquery.queryOf
import kotliquery.sessionOf
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class VergemålDaoTest : DatabaseIntegrationTest() {

    @BeforeEach
    fun setup() {
        opprettPerson()
    }

    @Test
    fun `lagre og les ut vergemål`() {
        vergemålDao.lagre(FNR, VergemålOgFremtidsfullmakt(harVergemål = true, harFremtidsfullmakter = false), false)
        assertEquals(true, vergemålDao.harVergemål(FNR))
        vergemålDao.lagre(FNR, VergemålOgFremtidsfullmakt(harVergemål = false, harFremtidsfullmakter = false), false)
        assertEquals(false, vergemålDao.harVergemål(FNR))
    }

    @Test
    fun `lagre og les ut fullmakter`() {
        vergemålDao.lagre(FNR, VergemålOgFremtidsfullmakt(harVergemål = false, harFremtidsfullmakter = false), false)
        assertEquals(false, harFullmakt(FNR))
        vergemålDao.lagre(FNR, VergemålOgFremtidsfullmakt(harVergemål = false, harFremtidsfullmakter = false), true)
        assertEquals(true, harFullmakt(FNR))
        vergemålDao.lagre(FNR, VergemålOgFremtidsfullmakt(harVergemål = false, harFremtidsfullmakter = true), false)
        assertEquals(true, harFullmakt(FNR))
        vergemålDao.lagre(FNR, VergemålOgFremtidsfullmakt(harVergemål = false, harFremtidsfullmakter = true), true)
        assertEquals(true, harFullmakt(FNR))
    }

    @Test
    fun `ikke vergemål om vi ikke har gjort noe oppslag`() {
        assertNull(vergemålDao.harVergemål(FNR))
    }

    private fun harFullmakt(fødselsnummer: String): Boolean? {
        @Language("PostgreSQL")
        val query = """
            SELECT har_fremtidsfullmakter, har_fullmakter
                FROM vergemal v
                    INNER JOIN person p on p.id = v.person_ref
                WHERE p.fodselsnummer = :fodselsnummer
            """
        return sessionOf(dataSource).use { session ->
            session.run(
                queryOf(
                    query,
                    mapOf("fodselsnummer" to fødselsnummer.toLong()),
                ).map { row ->
                    row.boolean("har_fremtidsfullmakter") || row.boolean("har_fullmakter")
                }.asSingle,
            )
        }
    }
}
