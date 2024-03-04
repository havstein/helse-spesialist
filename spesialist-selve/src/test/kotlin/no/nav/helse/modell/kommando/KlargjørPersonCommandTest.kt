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

internal class KlargjørPersonCommandTest {
    private companion object {
        private const val FNR = "12345678911"
        private const val AKTØR = "4321098765432"
        private const val FORNAVN = "KARI"
        private const val MELLOMNAVN = "Mellomnavn"
        private const val ETTERNAVN = "Nordmann"
        private const val ENHET_OSLO = "0301"
        private val FØDSELSDATO = LocalDate.EPOCH
        private val KJØNN = Kjønn.Kvinne
        private val ADRESSEBESKYTTELSE = Adressebeskyttelse.Ugradert

        private val objectMapper = jacksonObjectMapper()
    }

    private val personDao = mockk<PersonDao>(relaxed = true)
    private val command = KlargjørPersonCommand(FNR, AKTØR, { LocalDate.now() }, personDao)
    private lateinit var context: CommandContext

    private val observer = object : UtgåendeMeldingerObserver {
        val behov = mutableMapOf<String, Map<String, Any>>()
        val hendelser = mutableListOf<String>()
        override fun behov(behov: String, ekstraKontekst: Map<String, Any>, detaljer: Map<String, Any>) {
            this.behov[behov] = detaljer
        }

        override fun hendelse(hendelse: String) {
            hendelser.add(hendelse)
        }
    }

    @BeforeEach
    fun setup() {
        context = CommandContext(UUID.randomUUID())
        context.nyObserver(observer)
        clearMocks(personDao)
    }

    @Test
    fun `oppretter person`() {
        personFinnesIkke()
        context.add(HentPersoninfoløsning(FNR, FORNAVN, MELLOMNAVN, ETTERNAVN, FØDSELSDATO, KJØNN, ADRESSEBESKYTTELSE))
        context.add(HentEnhetløsning(ENHET_OSLO))
        context.add(HentInfotrygdutbetalingerløsning(objectMapper.createObjectNode()))
        assertTrue(command.execute(context))
        assertTrue(observer.behov.isEmpty())
        verify(exactly = 1) { personDao.insertPerson(FNR, AKTØR, any(), any(), any()) }
    }

    @Test
    fun `person finnes, men personinfo er utdatert`() {
        personFinnes()
        altUtdatert()
        assertFalse(command.execute(context))
        assertHarBehov(listOf("HentPersoninfoV2"))
    }

    @Test
    fun `person finnes, men enhet og utbetalinger utdatert`() {
        personFinnes()
        personinfoOppdatert()
        assertFalse(command.execute(context))
        assertHarBehov(listOf("HentEnhet"))
    }

    @Test
    fun `person finnes, men utbetalinger utdatert`() {
        personFinnes()
        utdatertUtbetalinger()
        assertFalse(command.execute(context))
        assertHarBehov(listOf("HentInfotrygdutbetalinger"))
    }

    @Test
    fun `oppdaterer utdatert person`() {
        personFinnes()
        altUtdatert()
        val personinfo = mockk<HentPersoninfoløsning>(relaxed = true)
        val enhet = mockk<HentEnhetløsning>(relaxed = true)
        val utbetalinger = mockk<HentInfotrygdutbetalingerløsning>(relaxed = true)
        context.add(personinfo)
        context.add(enhet)
        context.add(utbetalinger)
        assertTrue(command.execute(context))
        verify(exactly = 1) { personinfo.oppdater(personDao, FNR) }
        verify(exactly = 1) { enhet.oppdater(personDao, FNR) }
        verify(exactly = 1) { utbetalinger.oppdater(personDao, FNR) }
    }

    @Test
    fun `person finnes, alt er oppdatert`() {
        personFinnes()
        altOppdatert()
        assertTrue(command.execute(context))
    }

    @Test
    fun `sender ikke løsning på godkjenning hvis bruker er utdatert og ikke er tilknyttet utlandsenhet`() {
        personFinnes()
        altUtdatert()
        context.add(HentEnhetløsning(ENHET_OSLO))
        context.add(mockk<HentPersoninfoløsning>(relaxed = true))
        context.add(mockk<HentInfotrygdutbetalingerløsning>(relaxed = true))
        assertTrue(command.execute(context))
        assertEquals(0, observer.hendelser.size)
    }

    @Test
    fun `sender ikke løsning på godkjenning hvis bruker ikke er tilknyttet utlandsenhet`() {
        context.add(HentEnhetløsning(ENHET_OSLO))
        context.add(mockk<HentPersoninfoløsning>(relaxed = true))
        context.add(mockk<HentInfotrygdutbetalingerløsning>(relaxed = true))
        assertTrue(command.execute(context))
        assertEquals(0, observer.hendelser.size)
    }

    private fun assertHarBehov(forventetBehov: List<String>) {
        assertTrue(observer.behov.isNotEmpty())
        assertEquals(forventetBehov, observer.behov.keys.toList())
        verify(exactly = 0) { personDao.insertPerson(FNR, any(), any(), any(), any()) }
    }

    private fun personFinnes() {
        every { personDao.findPersonByFødselsnummer(FNR) } returns 1
    }

    private fun personFinnesIkke() {
        every { personDao.findPersonByFødselsnummer(FNR) } returns null
    }

    private fun altOppdatert() {
        every { personDao.findPersoninfoSistOppdatert(FNR) } returns LocalDate.now()
        every { personDao.findEnhetSistOppdatert(FNR) } returns LocalDate.now()
        every { personDao.findITUtbetalingsperioderSistOppdatert(FNR) } returns LocalDate.now()
    }

    private fun altUtdatert() {
        every { personDao.findPersoninfoSistOppdatert(FNR) } returns LocalDate.now().minusYears(1)
        every { personDao.findEnhetSistOppdatert(FNR) } returns LocalDate.now().minusYears(1)
        every { personDao.findITUtbetalingsperioderSistOppdatert(FNR) } returns LocalDate.now().minusYears(1)
    }

    private fun personinfoOppdatert() {
        every { personDao.findPersoninfoSistOppdatert(FNR) } returns LocalDate.now()
        every { personDao.findEnhetSistOppdatert(FNR) } returns LocalDate.now().minusYears(1)
        every { personDao.findITUtbetalingsperioderSistOppdatert(FNR) } returns LocalDate.now().minusYears(1)
    }

    private fun utdatertUtbetalinger() {
        every { personDao.findPersoninfoSistOppdatert(FNR) } returns LocalDate.now()
        every { personDao.findEnhetSistOppdatert(FNR) } returns LocalDate.now()
        every { personDao.findITUtbetalingsperioderSistOppdatert(FNR) } returns LocalDate.now().minusYears(1)
    }
}
