package no.nav.helse.modell.kommando

import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import no.nav.helse.db.PersonRepository
import no.nav.helse.mediator.CommandContextObserver
import no.nav.helse.modell.melding.Behov
import no.nav.helse.modell.person.HentPersoninfoløsning
import no.nav.helse.spesialist.api.person.Adressebeskyttelse
import no.nav.helse.spesialist.typer.Kjønn
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

internal class OppdaterPersoninfoCommandTest {
    private companion object {
        private const val FNR = "12345678911"
        private const val FORNAVN = "LITEN"
        private const val MELLOMNAVN = "STOR"
        private const val ETTERNAVN = "TRANFLASKE"
        private val FØDSELSDATO = LocalDate.EPOCH
        private val KJØNN = Kjønn.Ukjent
        private val ADRESSEBESKYTTELSE = Adressebeskyttelse.StrengtFortrolig
    }
    private lateinit var context: CommandContext

    private val personRepository = mockk<PersonRepository>(relaxed = true)

    private val observer = object : CommandContextObserver {
        val behov = mutableListOf<Behov>()

        override fun behov(behov: Behov, commandContextId: UUID) {
            this.behov.add(behov)
        }
    }

    @BeforeEach
    fun beforeEach() {
        context = CommandContext(UUID.randomUUID())
        context.nyObserver(observer)
    }

    @Test
    fun `trenger personinfo`() {
        val command = OppdaterPersoninfoCommand(FNR, personRepository, force = false)
        utdatertPersoninfo()
        assertFalse(command.execute(context))
        assertTrue(observer.behov.isNotEmpty())
        assertEquals(listOf(Behov.Personinfo), observer.behov.toList())
    }

    @Test
    fun `oppdatere personinfo`() {
        val context = CommandContext(UUID.randomUUID())
        val command = OppdaterPersoninfoCommand(FNR, personRepository, force = false)
        utdatertPersoninfo()
        val løsning = spyk(HentPersoninfoløsning(FNR, FORNAVN, MELLOMNAVN, ETTERNAVN, FØDSELSDATO, KJØNN, ADRESSEBESKYTTELSE))
        context.add(løsning)
        assertTrue(command.execute(context))
        verify(exactly = 1) { løsning.oppdater(personRepository, FNR) }
        verify(exactly = 1) { personRepository.upsertPersoninfo(FNR, FORNAVN, MELLOMNAVN, ETTERNAVN, FØDSELSDATO, KJØNN, ADRESSEBESKYTTELSE) }

    }

    @Test
    fun `oppdaterer ingenting når informasjonen er ny nok`() {
        val context = CommandContext(UUID.randomUUID())
        val command = OppdaterPersoninfoCommand(FNR, personRepository, force = false)
        every { personRepository.finnPersoninfoSistOppdatert(FNR) } returns LocalDate.now()
        assertTrue(command.execute(context))
        verify(exactly = 0) { personRepository.upsertPersoninfo(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `oppdaterer personinfo dersom force er satt til true`() {
        val context = CommandContext(UUID.randomUUID())
        val command = OppdaterPersoninfoCommand(FNR, personRepository, force = true)
        val løsning = spyk(HentPersoninfoløsning(FNR, FORNAVN, MELLOMNAVN, ETTERNAVN, FØDSELSDATO, KJØNN, ADRESSEBESKYTTELSE))
        context.add(løsning)
        every { personRepository.finnPersoninfoSistOppdatert(FNR) } returns LocalDate.now()
        assertTrue(command.execute(context))
        verify(exactly = 1) { løsning.oppdater(personRepository, FNR) }
        verify(exactly = 1) { personRepository.upsertPersoninfo(any(), any(), any(), any(), any(), any(), any()) }
    }


    private fun utdatertPersoninfo() {
        every { personRepository.finnPersoninfoSistOppdatert(FNR) } returns LocalDate.now().minusYears(1)
        every { personRepository.finnEnhetSistOppdatert(FNR) } returns LocalDate.now()
        every { personRepository.finnITUtbetalingsperioderSistOppdatert(FNR) } returns LocalDate.now()
    }
}
