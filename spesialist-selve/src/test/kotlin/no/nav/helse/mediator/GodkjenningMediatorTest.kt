package no.nav.helse.mediator

import io.mockk.clearMocks
import io.mockk.mockk
import io.mockk.verify
import no.nav.helse.abonnement.OpptegnelseDao
import no.nav.helse.abonnement.OpptegnelseType
import no.nav.helse.modell.UtbetalingsgodkjenningMessage
import no.nav.helse.modell.kommando.CommandContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.*

internal class GodkjenningMediatorTest {
    private lateinit var context: CommandContext
    private val opptegnelseDao = mockk<OpptegnelseDao>(relaxed = true)
    private val mediator = GodkjenningMediator(
        warningDao = mockk(relaxed = true),
        vedtakDao = mockk(relaxed = true),
        opptegnelseDao = opptegnelseDao
    )

    @BeforeEach
    fun setup() {
        context = CommandContext(UUID.randomUUID())
        clearMocks(opptegnelseDao)
    }

    @Test
    fun `automatisk avvisning skal opprette opptegnelse`() {
        mediator.automatiskAvvisning(context, UtbetalingsgodkjenningMessage("{}"), UUID.randomUUID(), fnr, listOf("foo"), UUID.randomUUID())
        assertOpptegnelseOpprettet()
    }

    @Test
    fun `automatisk utbetaling skal opprette opptegnelse`() {
        mediator.automatiskUtbetaling(context, UtbetalingsgodkjenningMessage("{}"), UUID.randomUUID(), fnr, UUID.randomUUID())
        assertOpptegnelseOpprettet()
    }

    @Test
    fun `saksbehandler utbetaling skal ikke opprette opptegnelse`() {
        mediator.saksbehandlerUtbetaling(context, UtbetalingsgodkjenningMessage("{}"), UUID.randomUUID(), fnr, "1",  "2@nav.no", LocalDateTime.now())
        assertOpptegnelseIkkeOpprettet()
    }

    @Test
    fun `saksbehandler avvisning skal ikke opprette opptegnelse`() {
        mediator.saksbehandlerAvvisning(context, UtbetalingsgodkjenningMessage("{}"), UUID.randomUUID(), fnr, "1", "2@nav.no", LocalDateTime.now(), null, null, null)
        assertOpptegnelseIkkeOpprettet()
    }

    private fun assertOpptegnelseOpprettet() = verify(exactly = 1) { opptegnelseDao.opprettOpptegnelse(eq(fnr), any(), eq(OpptegnelseType.NY_SAKSBEHANDLEROPPGAVE)) }

    private fun assertOpptegnelseIkkeOpprettet() = verify(exactly = 0) { opptegnelseDao.opprettOpptegnelse(eq(fnr), any(), eq(OpptegnelseType.NY_SAKSBEHANDLEROPPGAVE)) }

    private companion object {
        val fnr = "12341231221"
    }
}
