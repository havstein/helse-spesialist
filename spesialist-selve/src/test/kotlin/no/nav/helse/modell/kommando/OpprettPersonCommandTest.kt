package no.nav.helse.modell.kommando

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.LocalDate
import java.util.UUID
import no.nav.helse.mediator.UtgåendeMeldingerObserver
import no.nav.helse.modell.person.HentEnhetløsning
import no.nav.helse.modell.person.HentInfotrygdutbetalingerløsning
import no.nav.helse.modell.person.HentPersoninfoløsning
import no.nav.helse.modell.person.PersonDao
import no.nav.helse.spesialist.api.person.Adressebeskyttelse
import no.nav.helse.spesialist.api.person.Kjønn
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class OpprettPersonCommandTest {
    private companion object {
        private const val FNR = "12345678911"
        private const val AKTØR = "4321098765432"
        private const val FORNAVN = "KARI"
        private const val MELLOMNAVN = "Mellomnavn"
        private const val ETTERNAVN = "Nordmann"
        private const val ENHET_OSLO = "0301"
        private val FØDSELSDATO = LocalDate.EPOCH
        private val KJØNN = Kjønn.Kvinne
        private val ADRESSEBESKYTTELSE = Adressebeskyttelse.Fortrolig

        private val objectMapper = jacksonObjectMapper()
    }

    private val personDao = mockk<PersonDao>(relaxed = true)
    private val command = OpprettPersonCommand(FNR, AKTØR, { LocalDate.now() }, personDao)
    private lateinit var context: CommandContext

    private val observer = object : UtgåendeMeldingerObserver {
        val behov = mutableListOf<String>()
        override fun behov(behov: String, ekstraKontekst: Map<String, Any>, detaljer: Map<String, Any>) {
            this.behov.add(behov)
        }

        override fun hendelse(hendelse: String) {}
    }

    @BeforeEach
    fun setup() {
        context = CommandContext(UUID.randomUUID())
        context.nyObserver(observer)
        clearMocks(personDao)
    }

    @Test
    fun `oppretter ikke person når person finnes fra før`() {
        personFinnes()
        assertTrue(command.execute(context))
        verify(exactly = 0) { personDao.insertPerson(FNR, any(), any(), any(), any()) }
    }

    @Test
    fun `oppretter ikke person når person er opprettet under påvente av informasjonsbehov`() {
        personFinnesIkke()
        context.add(HentPersoninfoløsning(FNR, FORNAVN, MELLOMNAVN, ETTERNAVN, FØDSELSDATO, KJØNN, ADRESSEBESKYTTELSE))
        context.add(HentEnhetløsning(ENHET_OSLO))
        assertFalse(command.execute(context))
        verify(exactly = 0) { personDao.insertPerson(FNR, any(), any(), any(), any()) }
        personFinnes()
        context.add(HentInfotrygdutbetalingerløsning(objectMapper.createObjectNode()))
        assertTrue(command.resume(context))
        verify(exactly = 0) { personDao.insertPerson(FNR, any(), any(), any(), any()) }
    }

    @Test
    fun `oppretter person`() {
        val personinfoId = 4691337L
        every { personDao.insertPersoninfo(any(), any(), any(), any(), any(), any()) } returns personinfoId

        personFinnesIkke()
        context.add(HentPersoninfoløsning(FNR, FORNAVN, MELLOMNAVN, ETTERNAVN, FØDSELSDATO, KJØNN, ADRESSEBESKYTTELSE))
        context.add(HentEnhetløsning(ENHET_OSLO))
        context.add(HentInfotrygdutbetalingerløsning(objectMapper.createObjectNode()))
        assertTrue(command.execute(context))
        assertTrue(observer.behov.isEmpty())

        verify(exactly = 1) { personDao.insertPersoninfo(FORNAVN, MELLOMNAVN, ETTERNAVN, FØDSELSDATO, KJØNN, ADRESSEBESKYTTELSE) }
        verify(exactly = 1) { personDao.insertPerson(FNR, AKTØR, personinfoId, any(), any()) }
    }

    @Test
    fun `ber om manglende informasjon`() {
        personFinnesIkke()
        assertFalse(command.execute(context))
        assertHarBehov()
    }

    @Test
    fun `kan ikke opprette person med bare personinfo`() {
        personFinnesIkke()
        context.add(HentPersoninfoløsning(FNR, FORNAVN, MELLOMNAVN, ETTERNAVN, FØDSELSDATO, Kjønn.Kvinne, Adressebeskyttelse.StrengtFortroligUtland))
        assertFalse(command.execute(context))
        assertHarBehov()
    }

    @Test
    fun `kan ikke opprette person med bare enhet`() {
        personFinnesIkke()
        context.add(HentEnhetløsning(ENHET_OSLO))
        assertFalse(command.execute(context))
        assertHarBehov()
    }

    @Test
    fun `kan ikke opprette person med bare infotrygdutbetalinger`() {
        personFinnesIkke()
        context.add(HentInfotrygdutbetalingerløsning(objectMapper.createObjectNode()))
        assertFalse(command.execute(context))
        assertHarBehov()
    }

    private fun assertHarBehov() {
        assertTrue(observer.behov.isNotEmpty())
        assertEquals(listOf("HentPersoninfoV2", "HentEnhet", "HentInfotrygdutbetalinger"), observer.behov)
        verify(exactly = 0) { personDao.insertPerson(FNR, any(), any(), any(), any()) }
    }

    private fun personFinnes() {
        every { personDao.findPersonByFødselsnummer(FNR) } returns 1
    }
    private fun personFinnesIkke() {
        every { personDao.findPersonByFødselsnummer(FNR) } returns null
    }

}
