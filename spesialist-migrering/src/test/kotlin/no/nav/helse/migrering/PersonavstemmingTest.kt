package no.nav.helse.migrering

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.migrering.db.SpesialistDao
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class PersonavstemmingTest: AbstractDatabaseTest() {

    private val spesialistDao = SpesialistDao(dataSource)
    private val testRapid = TestRapid().apply {
        Personavstemming.River(this, spesialistDao)
    }

    @BeforeEach
    fun setUp() {
        testRapid.reset()
    }

    @Test
    fun `Oppretter person, arbeidsgiver og vedtaksperiode`() {
        val vedtaksperiodeId = UUID.randomUUID()
        assertPerson("12345678910", 0)
        assertArbeidsgiver("987654321", 0)
        assertVedtaksperiode(vedtaksperiodeId, 0)
        testRapid.sendTestMessage(testevent(vedtaksperiodeId))
        assertPerson("12345678910", 1)
        assertArbeidsgiver("987654321", 1)
        assertVedtaksperiode(vedtaksperiodeId, 1)
    }

    @Test
    fun `Oppretter arbeidsgiver og vedtaksperiode når person finnes fra før av`() {
        spesialistDao.personOpprettet("42", "12345678910")
        val vedtaksperiodeId = UUID.randomUUID()
        assertPerson("12345678910", 1)
        assertArbeidsgiver("987654321", 0)
        assertVedtaksperiode(vedtaksperiodeId, 0)
        testRapid.sendTestMessage(testevent(vedtaksperiodeId))
        assertPerson("12345678910", 1)
        assertArbeidsgiver("987654321", 1)
        assertVedtaksperiode(vedtaksperiodeId, 1)
    }

    @Test
    fun `Oppretter vedtaksperiode når person og arbeidsgiver finnes fra før av`() {
        spesialistDao.personOpprettet("42", "12345678910")
        spesialistDao.arbeidsgiverOpprettet("987654321")
        val vedtaksperiodeId = UUID.randomUUID()
        assertPerson("12345678910", 1)
        assertArbeidsgiver("987654321", 1)
        assertVedtaksperiode(vedtaksperiodeId, 0)
        testRapid.sendTestMessage(testevent(vedtaksperiodeId))
        assertPerson("12345678910", 1)
        assertArbeidsgiver("987654321", 1)
        assertVedtaksperiode(vedtaksperiodeId, 1)
    }

    @Test
    fun `Oppretter vedtaksperiode og person når arbeidsgiver finnes fra før av`() {
        spesialistDao.arbeidsgiverOpprettet("987654321")
        val vedtaksperiodeId = UUID.randomUUID()
        assertPerson("12345678910", 0)
        assertArbeidsgiver("987654321", 1)
        assertVedtaksperiode(vedtaksperiodeId, 0)
        testRapid.sendTestMessage(testevent(vedtaksperiodeId))
        assertPerson("12345678910", 1)
        assertArbeidsgiver("987654321", 1)
        assertVedtaksperiode(vedtaksperiodeId, 1)
    }

    @Test
    fun `Oppretter ikke ny vedtaksperiode når den finnes fra før av`() {
        spesialistDao.personOpprettet("42", "12345678910")
        spesialistDao.arbeidsgiverOpprettet("987654321")
        val vedtaksperiodeId = UUID.randomUUID()
        spesialistDao.vedtaksperiodeOpprettet(vedtaksperiodeId, LocalDateTime.now(), 1.januar, 31.januar, 1.januar, "12345678910", "987654321")
        assertPerson("12345678910", 1)
        assertArbeidsgiver("987654321", 1)
        assertVedtaksperiode(vedtaksperiodeId, 1)
        testRapid.sendTestMessage(testevent(vedtaksperiodeId))
        assertPerson("12345678910", 1)
        assertArbeidsgiver("987654321", 1)
        assertVedtaksperiode(vedtaksperiodeId, 1)
    }

    @Test
    fun `Oppdaterer eksisterende generasjoner når vi oppretter vedtaksperiode`() {
        spesialistDao.personOpprettet("42", "12345678910")
        spesialistDao.arbeidsgiverOpprettet("987654321")
        val vedtaksperiodeId = UUID.randomUUID()
        opprettGenerasjonFor(vedtaksperiodeId)
        assertPerson("12345678910", 1)
        assertArbeidsgiver("987654321", 1)
        assertVedtaksperiode(vedtaksperiodeId, 0)
        assertGenerasjonMed(vedtaksperiodeId, 1)
        testRapid.sendTestMessage(testevent(vedtaksperiodeId))
        assertPerson("12345678910", 1)
        assertArbeidsgiver("987654321", 1)
        assertVedtaksperiode(vedtaksperiodeId, 1)
        assertGenerasjonMed(vedtaksperiodeId, 1.januar, 31.januar, 1.januar, 1)
    }

    @Test
    fun `Oppdaterer eksisterende generasjoner selv om vedtaksperiode allerede eksisterer`() {
        spesialistDao.personOpprettet("42", "12345678910")
        spesialistDao.arbeidsgiverOpprettet("987654321")
        val vedtaksperiodeId = UUID.randomUUID()
        spesialistDao.vedtaksperiodeOpprettet(vedtaksperiodeId, LocalDateTime.now(), 1.januar, 31.januar, 1.januar, "12345678910", "987654321")
        opprettGenerasjonFor(vedtaksperiodeId)
        assertPerson("12345678910", 1)
        assertArbeidsgiver("987654321", 1)
        assertVedtaksperiode(vedtaksperiodeId, 1)
        assertGenerasjonMed(vedtaksperiodeId, 1)
        testRapid.sendTestMessage(testevent(vedtaksperiodeId))
        assertPerson("12345678910", 1)
        assertArbeidsgiver("987654321", 1)
        assertVedtaksperiode(vedtaksperiodeId, 1)
        assertGenerasjonMed(vedtaksperiodeId, 1.januar, 31.januar, 1.januar, 1)
    }

    private fun assertPerson(fødselsnummer: String, forventetAntall: Int) {
        @Language("PostgreSQL")
        val query = "SELECT count(1) FROM person WHERE fodselsnummer = ? "
        val antall = sessionOf(dataSource).use { session ->
            session.run(queryOf(query, fødselsnummer.toLong()).map { it.int(1) }.asSingle)
        }
        assertEquals(forventetAntall, antall)
    }

    private fun assertArbeidsgiver(organisasjonsnummer: String, forventetAntall: Int) {
        @Language("PostgreSQL")
        val query = "SELECT count(1) FROM arbeidsgiver WHERE orgnummer = ? "
        val antall = sessionOf(dataSource).use { session ->
            session.run(queryOf(query, organisasjonsnummer.toLong()).map { it.int(1) }.asSingle)
        }
        assertEquals(forventetAntall, antall)
    }

    private fun assertVedtaksperiode(vedtaksperiodeId: UUID, forventetAntall: Int) {
        @Language("PostgreSQL")
        val query = "SELECT count(1) FROM vedtak WHERE vedtaksperiode_id = ? "
        val antall = sessionOf(dataSource).use { session ->
            session.run(queryOf(query, vedtaksperiodeId).map { it.int(1) }.asSingle)
        }
        assertEquals(forventetAntall, antall)
    }

    private fun opprettGenerasjonFor(vedtaksperiodeId: UUID) {
        @Language("PostgreSQL")
        val query = "INSERT INTO selve_vedtaksperiode_generasjon (vedtaksperiode_id, opprettet_av_hendelse) VALUES (?, gen_random_uuid())"
        sessionOf(dataSource).use { session ->
            session.run(queryOf(query, vedtaksperiodeId).asExecute)
        }
    }

    private fun assertGenerasjonMed(vedtaksperiodeId: UUID, forventetAntall: Int) {
        @Language("PostgreSQL")
        val query = "SELECT count(1) FROM selve_vedtaksperiode_generasjon svg WHERE vedtaksperiode_id = ? AND fom IS NULL AND tom IS NULL AND skjæringstidspunkt is null "
        val antallFunnet = sessionOf(dataSource).use { session ->
            session.run(queryOf(query, vedtaksperiodeId).map { it.int(1) }.asSingle)
        }
        assertEquals(forventetAntall, antallFunnet)
    }
    private fun assertGenerasjonMed(vedtaksperiodeId: UUID, fom: LocalDate, tom: LocalDate, skjæringstidspunkt: LocalDate, forventetAntall: Int) {
        @Language("PostgreSQL")
        val query = "SELECT count(1) FROM selve_vedtaksperiode_generasjon svg WHERE vedtaksperiode_id = ? AND fom = ? AND tom = ? AND skjæringstidspunkt = ? "
        val antallFunnet = sessionOf(dataSource).use { session ->
            session.run(queryOf(query, vedtaksperiodeId, fom, tom, skjæringstidspunkt).map { it.int(1) }.asSingle)
        }
        assertEquals(forventetAntall, antallFunnet)
    }

    @Language("JSON")
    private fun testevent(vedtaksperiodeId: UUID): String {
        return """
            {
                  "@event_name": "person_avstemt",
                  "arbeidsgivere": [
                    {
                      "organisasjonsnummer": "987654321",
                      "vedtaksperioder": [
                        {
                          "id": "$vedtaksperiodeId",
                          "fom": "2018-01-01",
                          "tom": "2018-01-31",
                          "skjæringstidspunkt": "2018-01-01",
                          "opprettet": "2022-11-23T12:52:42.017867",
                          "oppdatert": "2022-11-23T12:52:42.017867",
                          "forkastet": false
                          
                      }
                      ]
                    }
                  ],
                  "@id": "c405a203-264e-4496-99dc-785e76ede254",
                  "@opprettet": "2022-11-23T12:52:42.017867",
                  "fødselsnummer": "12345678910",
                  "aktørId": "42"
            }
            
        """.trimIndent()
    }


}