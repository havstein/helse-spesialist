package no.nav.helse.db

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.readValue
import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.DatabaseIntegrationTest
import no.nav.helse.mediator.meldinger.løsninger.Inntekter
import no.nav.helse.modell.kommando.MinimalPersonDto
import no.nav.helse.spesialist.api.person.Adressebeskyttelse
import no.nav.helse.spesialist.typer.Kjønn
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Isolated
import java.time.LocalDate
import java.time.LocalDateTime.now
import java.time.YearMonth

@Isolated
internal class PgPersonDaoTest : DatabaseIntegrationTest() {
    @BeforeEach
    fun tømTabeller() {
        sessionOf(dataSource).use {
            it.run(queryOf("truncate person_info, inntekt, infotrygdutbetalinger restart identity cascade").asExecute)
        }
    }

    @Test
    fun `oppretter minimal person`() {
        personDao.lagreMinimalPerson(MinimalPersonDto(FNR, AKTØR))
        val minimalPerson = personDao.finnMinimalPerson(FNR)
        assertNotNull(minimalPerson)
        assertEquals(FNR, minimalPerson?.fødselsnummer)
        assertEquals(AKTØR, minimalPerson?.aktørId)
    }

    @Test
    fun `kan fjerne en person fra klargjøringstabellen`() {
        leggInnPersonIKlargjøringstabellen(FNR)
        assertTrue(personFinnesIKlargjøringstabellen(FNR))
        personDao.personKlargjort(FNR)
        assertFalse(personFinnesIKlargjøringstabellen(FNR))
    }

    @Test
    fun `lagre personinfo`() {
        personDao.upsertPersoninfo(FNR, FORNAVN, MELLOMNAVN, ETTERNAVN, FØDSELSDATO, KJØNN, ADRESSEBESKYTTELSE)
        assertPersoninfo(FORNAVN, MELLOMNAVN, ETTERNAVN, FØDSELSDATO, KJØNN, ADRESSEBESKYTTELSE)
    }

    @Test
    fun `ingen mellomnavn`() {
        personDao.upsertPersoninfo(FNR, FORNAVN, null, ETTERNAVN, FØDSELSDATO, KJØNN, ADRESSEBESKYTTELSE)
        assertPersoninfo(FORNAVN, null, ETTERNAVN, FØDSELSDATO, KJØNN, ADRESSEBESKYTTELSE)
    }

    @Test
    fun `lagre infotrygdutbetalinger`() {
        personDao.upsertInfotrygdutbetalinger(FNR, objectMapper.createObjectNode())
        assertEquals(1, infotrygdUtbetalinger().size)
    }

    @Test
    fun `oppretter person`() {
        val (_, personinfoId, enhetId, infotrygdutbetalingerId) = opprettPerson()
        assertNotNull(personDao.finnPersonMedFødselsnummer(FNR))
        assertEquals(1, infotrygdUtbetalinger().size)
        assertEquals(LocalDate.now(), personDao.finnEnhetSistOppdatert(FNR))
        assertEquals(LocalDate.now(), personDao.finnITUtbetalingsperioderSistOppdatert(FNR))
        assertEquals(LocalDate.now(), personDao.finnPersoninfoSistOppdatert(FNR))
        assertEquals(1, person().size)
        person().first().assertEquals(FNR, AKTØR, personinfoId, enhetId, infotrygdutbetalingerId)
    }

    @Test
    fun `oppdaterer personinfo`() {
        opprettPerson()
        val nyttFornavn = "OLE"
        val nyttMellomnavn = "PETTER"
        val nyttEtternavn = "SVENSKE"
        val nyFødselsdato = LocalDate.of(1990, 12, 31)
        val nyttKjønn = Kjønn.Mann
        val nyAdressebeskyttelse = Adressebeskyttelse.Fortrolig
        personDao.upsertPersoninfo(
            FNR,
            nyttFornavn,
            nyttMellomnavn,
            nyttEtternavn,
            nyFødselsdato,
            nyttKjønn,
            nyAdressebeskyttelse,
        )
        assertPersoninfo(nyttFornavn, nyttMellomnavn, nyttEtternavn, nyFødselsdato, nyttKjønn, nyAdressebeskyttelse)
    }

    @Test
    fun `finner aktørId`() {
        opprettPerson()
        assertEquals(AKTØR, personDao.finnAktørId(FNR))
    }

    @Test
    fun `finner ikke aktørId om vi ikke har person`() {
        assertEquals(null, personDao.finnAktørId(FNR))
    }

    @Test
    fun `finner enhet`() {
        opprettPerson()
        assertEquals(ENHET.toInt(), personDao.finnEnhetId(FNR).toInt())
    }

    @Test
    fun `oppdaterer enhet`() {
        opprettPerson()
        val nyEnhet = "2100".toInt()
        personDao.oppdaterEnhet(FNR, nyEnhet)
        person().first().assertEnhet(nyEnhet)
    }

    @Test
    fun `oppdaterer infotrygdutbetalinger`() {
        opprettPerson()
        assertEquals("{}", infotrygdUtbetalinger().first())
        val utbetalinger = objectMapper.createObjectNode().set<JsonNode>("test", objectMapper.createArrayNode())
        personDao.upsertInfotrygdutbetalinger(FNR, utbetalinger)
        assertEquals("{\"test\":[]}", infotrygdUtbetalinger().first())
    }

    @Test
    fun `finner adressebeskyttelse for person`() {
        opprettPerson(fødselsnummer = FNR, adressebeskyttelse = Adressebeskyttelse.Fortrolig)
        assertEquals(Adressebeskyttelse.Fortrolig, personDao.finnAdressebeskyttelse(FNR))
    }

    @Test
    fun `Lagrer inntekt`() {
        opprettPerson()
        val sekvensnummer =
            personDao.lagreInntekter(
                fødselsnummer = FNR,
                skjæringstidspunkt = LocalDate.parse("2022-11-11"),
                inntekter =
                    listOf(
                        Inntekter(
                            YearMonth.parse("2022-11"),
                            listOf(Inntekter.Inntekt(20000.0, ORGNUMMER)),
                        ),
                        Inntekter(
                            YearMonth.parse("2022-10"),
                            listOf(Inntekter.Inntekt(20000.0, ORGNUMMER)),
                        ),
                        Inntekter(
                            YearMonth.parse("2022-09"),
                            listOf(Inntekter.Inntekt(20000.0, ORGNUMMER)),
                        ),
                    ),
            )
        assertEquals(1, sekvensnummer)

        val inntekter = inntekter()
        assertEquals(3, inntekter.size)
        assertEquals(YearMonth.parse("2022-11"), inntekter.first().årMåned)
    }

    @Test
    fun `Finner riktig inntekt`() {
        opprettPerson()
        personDao.lagreInntekter(
            fødselsnummer = FNR,
            skjæringstidspunkt = LocalDate.parse("2022-11-11"),
            inntekter = listOf(Inntekter(YearMonth.parse("2022-11"), listOf(Inntekter.Inntekt(20000.0, ORGNUMMER)))),
        )
        personDao.lagreInntekter(
            fødselsnummer = FNR,
            skjæringstidspunkt = LocalDate.parse("2022-03-03"),
            inntekter = listOf(Inntekter(YearMonth.parse("2022-03"), listOf(Inntekter.Inntekt(20000.0, ORGNUMMER)))),
        )
        personDao.lagreInntekter(
            fødselsnummer = FNR,
            skjæringstidspunkt = LocalDate.parse("2020-01-01"),
            inntekter = listOf(Inntekter(YearMonth.parse("2021-01"), listOf(Inntekter.Inntekt(20000.0, ORGNUMMER)))),
        )

        assertEquals(
            YearMonth.parse("2022-11"),
            personDao.finnInntekter(FNR, LocalDate.parse("2022-11-11"))!!.first().årMåned,
        )
        assertEquals(
            YearMonth.parse("2022-03"),
            personDao.finnInntekter(FNR, LocalDate.parse("2022-03-03"))!!.first().årMåned,
        )
        assertEquals(
            YearMonth.parse("2021-01"),
            personDao.finnInntekter(FNR, LocalDate.parse("2020-01-01"))!!.first().årMåned,
        )
    }

    private fun assertPersoninfo(
        forventetNavn: String,
        forventetMellomnavn: String?,
        forventetEtternavn: String?,
        forventetFødselsdato: LocalDate,
        forventetKjønn: Kjønn,
        forventetAdressebeskyttelse: Adressebeskyttelse,
    ) {
        val personinfo = personinfo()
        assertEquals(1, personinfo.size)
        personinfo
            .first()
            .assertEquals(
                forventetNavn,
                forventetMellomnavn,
                forventetEtternavn,
                forventetFødselsdato,
                forventetKjønn,
                forventetAdressebeskyttelse,
            )
    }

    private fun infotrygdUtbetalinger() =
        sessionOf(dataSource).use { session ->
            session.run(
                queryOf("SELECT data FROM infotrygdutbetalinger")
                    .map { row -> row.string("data") }
                    .asList,
            )
        }

    private fun person() =
        sessionOf(dataSource).use { session ->
            session.run(
                queryOf("SELECT fødselsnummer, aktør_id, info_ref, enhet_ref, infotrygdutbetalinger_ref FROM person")
                    .map { row ->
                        Person(
                            row.string("fødselsnummer"),
                            row.string("aktør_id"),
                            row.longOrNull("info_ref"),
                            row.intOrNull("enhet_ref"),
                            row.longOrNull("infotrygdutbetalinger_ref"),
                        )
                    }.asList,
            )
        }

    private fun inntekter(): List<Inntekter> =
        sessionOf(dataSource).use { session ->
            session.run(
                queryOf("SELECT inntekter from inntekt")
                    .map { row ->
                        objectMapper.readValue<List<Inntekter>>(row.string("inntekter"))
                    }.asSingle,
            ) ?: emptyList()
        }

    private fun personinfo() =
        sessionOf(dataSource).use { session ->
            session.run(
                queryOf("SELECT fornavn, mellomnavn, etternavn, fodselsdato, kjonn, adressebeskyttelse FROM person_info")
                    .map { row ->
                        Personinfo(
                            row.string("fornavn"),
                            row.stringOrNull("mellomnavn"),
                            row.string("etternavn"),
                            row.localDate("fodselsdato"),
                            enumValueOf(row.string("kjonn")),
                            enumValueOf(row.string("adressebeskyttelse")),
                        )
                    }.asList,
            )
        }

    private fun leggInnPersonIKlargjøringstabellen(fødselsnummer: String) {
        dbQuery.update(
            "insert into person_klargjores (fødselsnummer, opprettet) values (:foedselsnummer, :opprettet)",
            "foedselsnummer" to fødselsnummer,
            "opprettet" to now()
        )
    }

    private fun personFinnesIKlargjøringstabellen(fødselsnummer: String) = dbQuery.single(
        "select 1 from person_klargjores where fødselsnummer = :foedselsnummer",
        "foedselsnummer" to fødselsnummer
    ) { true } ?: false

    private class Person(
        private val fødselsnummer: String,
        private val aktørId: String,
        private val infoRef: Long?,
        private val enhetRef: Int?,
        private val infotrygdutbetalingerRef: Long?,
    ) {
        fun assertEquals(
            forventetFødselsnummer: String,
            forventetAktørId: String,
            forventetInfoRef: Long?,
            forventetEnhetRef: Int?,
            forventetInfotrygdutbetalingerRef: Long?,
        ) {
            assertEquals(forventetFødselsnummer, fødselsnummer)
            assertEquals(forventetAktørId, aktørId)
            assertEquals(forventetInfoRef, infoRef)
            assertEquals(forventetEnhetRef, enhetRef)
            assertEquals(forventetInfotrygdutbetalingerRef, infotrygdutbetalingerRef)
        }

        fun assertEnhet(forventetEnhet: Int) {
            assertEquals(forventetEnhet, enhetRef)
        }
    }

    private class Personinfo(
        private val fornavn: String,
        private val mellomnavn: String?,
        private val etternavn: String,
        private val fødselsdato: LocalDate,
        private val kjønn: Kjønn,
        private val adressebeskyttelse: Adressebeskyttelse,
    ) {
        fun assertEquals(
            forventetNavn: String,
            forventetMellomnavn: String?,
            forventetEtternavn: String?,
            forventetFødselsdato: LocalDate,
            forventetKjønn: Kjønn,
            forventetAdressebeskyttelse: Adressebeskyttelse,
        ) {
            assertEquals(forventetNavn, fornavn)
            assertEquals(forventetMellomnavn, mellomnavn)
            assertEquals(forventetEtternavn, etternavn)
            assertEquals(forventetFødselsdato, fødselsdato)
            assertEquals(forventetKjønn, kjønn)
            assertEquals(forventetAdressebeskyttelse, adressebeskyttelse)
        }
    }
}
