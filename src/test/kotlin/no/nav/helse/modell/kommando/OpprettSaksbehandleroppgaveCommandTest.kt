package no.nav.helse.modell.kommando

import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.helse.mediator.OppgaveMediator
import no.nav.helse.modell.Oppgave
import no.nav.helse.modell.automatisering.Automatisering
import no.nav.helse.modell.egenansatt.EgenAnsattDao
import no.nav.helse.modell.person.PersonDao
import no.nav.helse.modell.tildeling.ReservasjonDao
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.*

internal class OpprettSaksbehandleroppgaveCommandTest {
    private companion object {
        private val VEDTAKSPERIODE_ID = UUID.randomUUID()
        private const val FNR = "12345678910"
        private val hendelseId = UUID.randomUUID()
    }

    private val oppgaveMediator = mockk<OppgaveMediator>(relaxed = true)
    private val automatisering = mockk<Automatisering>(relaxed = true)
    private val egenAnsattDao = mockk<EgenAnsattDao>(relaxed = true)
    private val reservasjonDao = mockk<ReservasjonDao>(relaxed = true)
    private val personDao = mockk<PersonDao>(relaxed = true)
    private lateinit var context: CommandContext
    private val command = OpprettSaksbehandleroppgaveCommand(
        fødselsnummer = FNR,
        vedtaksperiodeId = VEDTAKSPERIODE_ID,
        reservasjonDao = reservasjonDao,
        oppgaveMediator = oppgaveMediator,
        automatisering = automatisering,
        egenAnsattDao = egenAnsattDao,
        hendelseId = hendelseId,
        personDao = personDao
    )

    @BeforeEach
    fun setup() {
        context = CommandContext(UUID.randomUUID())
        clearMocks(oppgaveMediator)
    }

    @Test
    fun `oppretter oppgave`() {
        every { reservasjonDao.hentReservasjonFor(FNR) } returns null
        assertTrue(command.execute(context))
        verify(exactly = 1) { oppgaveMediator.opprett(Oppgave.søknad(VEDTAKSPERIODE_ID)) }
    }

    @Test
    fun `oppretter stikkprøve`() {
        every { reservasjonDao.hentReservasjonFor(FNR) } returns null
        every { automatisering.erStikkprøve(VEDTAKSPERIODE_ID, any()) } returns true
        assertTrue(command.execute(context))
        verify(exactly = 1) { oppgaveMediator.opprett(Oppgave.stikkprøve(VEDTAKSPERIODE_ID)) }
    }

    @Test
    fun `oppretter oppgave med reservasjon`() {
        val reservasjon = Pair(UUID.randomUUID(), LocalDateTime.now())
        every { reservasjonDao.hentReservasjonFor(FNR) } returns reservasjon
        assertTrue(command.execute(context))
        verify(exactly = 1) { oppgaveMediator.opprettOgTildel(
            Oppgave.søknad(VEDTAKSPERIODE_ID),
            reservasjon.first,
            reservasjon.second
        ) }
    }
}
