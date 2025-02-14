package no.nav.helse.modell.automatisering

import io.mockk.mockk
import io.mockk.verify
import no.nav.helse.modell.kommando.CommandContext
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

internal class SettTidligereAutomatiseringInaktivCommandTest {

    private companion object {
        private val vedtaksperiodeId = UUID.randomUUID()
        private val hendelseId = UUID.randomUUID()
    }

    private val automatisering = mockk<Automatisering>(relaxed = true)
    private val command =
        SettTidligereAutomatiseringInaktivCommand(
            vedtaksperiodeId,
            hendelseId,
            automatisering,
        )

    private lateinit var context: CommandContext

    @BeforeEach
    fun setup() {
        context = CommandContext(UUID.randomUUID())
    }

    @Test
    fun `kaller utfør og returnerer true`() {
        Assertions.assertTrue(command.execute(context))

        verify {
            automatisering.settInaktiv(vedtaksperiodeId, hendelseId)
        }
    }
}
