package no.nav.helse.db

import no.nav.helse.DatabaseIntegrationTest
import no.nav.helse.modell.varsel.Varseldefinisjon
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Isolated
import java.time.LocalDateTime
import java.util.UUID

@Isolated
internal class PgDefinisjonDaoTest: DatabaseIntegrationTest() {

    private val definisjonDao = PgDefinisjonDao(dataSource)

    @BeforeEach
    fun tømTabeller() {
        dbQuery.execute("truncate selve_varsel, api_varseldefinisjon")
    }

    @Test
    fun `lagre varseldefinisjon`() {
        val definisjonId = UUID.randomUUID()
        definisjonDao.lagreDefinisjon(definisjonId, "EN_KODE", "EN_TITTEL", "EN_FORKLARING", "EN_HANDLING", false, LocalDateTime.now())
        assertNotNull(definisjonDao.definisjonFor(definisjonId))
    }

    @Test
    fun `lagre flere varseldefinisjoner`() {
        val definisjonId1 = UUID.randomUUID()
        val definisjonId2 = UUID.randomUUID()
        definisjonDao.lagreDefinisjon(definisjonId1, "EN_KODE", "EN_TITTEL", "EN_FORKLARING", "EN_HANDLING", false, LocalDateTime.now())
        definisjonDao.lagreDefinisjon(definisjonId2, "EN_ANNEN_KODE", "EN_TITTEL", "EN_FORKLARING", "EN_HANDLING", false, LocalDateTime.now())
        assertNotNull(definisjonDao.definisjonFor(definisjonId1))
        assertNotNull(definisjonDao.definisjonFor(definisjonId2))
    }

    @Test
    fun `finner definisjon basert på unik id`() {
        val definisjonId1 = UUID.randomUUID()
        definisjonDao.lagreDefinisjon(definisjonId1, "EN_KODE", "EN_TITTEL", "EN_FORKLARING", "EN_HANDLING", false, LocalDateTime.now())
        assertEquals(
            Varseldefinisjon(
                id = definisjonId1,
                varselkode = "EN_KODE",
                tittel = "EN_TITTEL",
                forklaring = "EN_FORKLARING",
                handling = "EN_HANDLING",
                avviklet = false,
                opprettet = LocalDateTime.now()
            ),
            definisjonDao.definisjonFor(definisjonId1)
        )
    }

    @Test
    fun `finner definisjon basert på unik id selv når det finnes andre definisjoner for samme varselkode`() {
        val definisjonId1 = UUID.randomUUID()
        definisjonDao.lagreDefinisjon(definisjonId1, "EN_KODE", "EN_TITTEL", "EN_FORKLARING", "EN_HANDLING", false, LocalDateTime.now())
        definisjonDao.lagreDefinisjon(UUID.randomUUID(), "EN_KODE", "EN_TITTEL", "EN_FORKLARING", "EN_HANDLING", false, LocalDateTime.now())
        assertEquals(
            Varseldefinisjon(
                id = definisjonId1,
                varselkode = "EN_KODE",
                tittel = "EN_TITTEL",
                forklaring = "EN_FORKLARING",
                handling = "EN_HANDLING",
                avviklet = false,
                opprettet = LocalDateTime.now()
            ),
            definisjonDao.definisjonFor(definisjonId1)
        )
    }

    @Test
    fun `lagrer ikke definisjon dobbelt opp`() {
        val definisjonsId = UUID.randomUUID()
        definisjonDao.lagreDefinisjon(definisjonsId, "EN_KODE", "EN_TITTEL", "EN_FORKLARING", "EN_HANDLING", false, LocalDateTime.now())
        definisjonDao.lagreDefinisjon(definisjonsId, "EN_KODE", "EN_TITTEL", "EN_FORKLARING", "EN_HANDLING", false, LocalDateTime.now())

        val definisjoner = alleDefinisjoner()
        assertEquals(1, definisjoner.size)
    }

    @Test
    fun `finner siste definisjon for kode`() {
        val definisjonsId = UUID.randomUUID()
        definisjonDao.lagreDefinisjon(UUID.randomUUID(), "EN_KODE", "EN_TITTEL", "EN_FORKLARING", "EN_HANDLING", false, LocalDateTime.now().minusHours(1))
        definisjonDao.lagreDefinisjon(definisjonsId, "EN_KODE", "EN_ANNEN_TITTEL", "EN_ANNEN_FORKLARING", "EN_ANNEN_HANDLING", false, LocalDateTime.now())
        val definisjon = definisjonDao.sisteDefinisjonFor("EN_KODE")

        assertEquals(Varseldefinisjon(definisjonsId, "EN_KODE", "EN_ANNEN_TITTEL", "EN_ANNEN_FORKLARING", "EN_ANNEN_HANDLING", false, LocalDateTime.now()), definisjon)
    }

    @Test
    fun `finner siste definisjon i tid for kode`() {
        val definisjonsId = UUID.randomUUID()
        val nå = LocalDateTime.now()
        val ettÅrSiden = LocalDateTime.now().minusYears(1)
        definisjonDao.lagreDefinisjon(definisjonsId, "EN_KODE", "EN_TITTEL", "EN_FORKLARING", "EN_HANDLING", false, nå)
        definisjonDao.lagreDefinisjon(UUID.randomUUID(), "EN_KODE", "EN_ANNEN_TITTEL", "EN_ANNEN_FORKLARING", "EN_ANNEN_HANDLING", false, ettÅrSiden)
        val definisjon = definisjonDao.sisteDefinisjonFor("EN_KODE")

        assertEquals(Varseldefinisjon(definisjonsId, "EN_KODE", "EN_TITTEL", "EN_FORKLARING", "EN_HANDLING", false, LocalDateTime.now()), definisjon)
    }

    private fun alleDefinisjoner(): List<Varseldefinisjon> =
        dbQuery.list("SELECT * FROM api_varseldefinisjon") {
            Varseldefinisjon(
                id = it.uuid("unik_id"),
                varselkode = it.string("kode"),
                tittel = it.string("tittel"),
                forklaring = it.stringOrNull("forklaring"),
                handling = it.stringOrNull("handling"),
                avviklet = it.boolean("avviklet"),
                opprettet = it.localDateTime("opprettet")
            )
        }

}
