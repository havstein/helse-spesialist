package no.nav.helse.modell.egenansatt

import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.helse.db.EgenAnsattDao
import no.nav.helse.mediator.CommandContextObserver
import no.nav.helse.mediator.meldinger.løsninger.EgenAnsattløsning
import no.nav.helse.modell.kommando.CommandContext
import no.nav.helse.modell.melding.Behov
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

internal class KontrollerEgenAnsattstatusTest {
    private companion object {
        private const val FNR = "12345678911"
    }

    private val dao = mockk<EgenAnsattDao>(relaxed = true)

    private val command = KontrollerEgenAnsattstatus(FNR, dao)
    private lateinit var context: CommandContext

    private val observer =
        object : CommandContextObserver {
            val behov = mutableListOf<Behov>()

            override fun behov(behov: Behov, commandContextId: UUID) {
                this.behov.add(behov)
            }
        }

    @BeforeEach
    fun setup() {
        context = CommandContext(UUID.randomUUID())
        context.nyObserver(observer)
        clearMocks(dao)
    }

    @Test
    fun `ber om informasjon om egen ansatt`() {
        every { dao.erEgenAnsatt(any()) } returns null
        assertFalse(command.execute(context))
        assertEquals(listOf(Behov.EgenAnsatt), observer.behov.toList())
    }

    @Test
    fun `mangler løsning ved resume`() {
        every { dao.erEgenAnsatt(any()) } returns null
        assertFalse(command.resume(context))
        verify(exactly = 0) { dao.lagre(any(), any(), any()) }
    }

    @Test
    fun `lagrer løsning ved resume`() {
        every { dao.erEgenAnsatt(any()) } returns null
        context.add(EgenAnsattløsning(LocalDateTime.now(), FNR, false))
        assertTrue(command.resume(context))
        verify(exactly = 1) { dao.lagre(FNR, false, any()) }
    }

    @Test
    fun `sender ikke behov om informasjonen finnes`() {
        every { dao.erEgenAnsatt(any()) } returns false
        assertTrue(command.resume(context))
        assertEquals(emptyList<Behov>(), observer.behov.toList())

        every { dao.erEgenAnsatt(any()) } returns true
        assertTrue(command.resume(context))
        assertEquals(emptyList<Behov>(), observer.behov.toList())
    }
}
