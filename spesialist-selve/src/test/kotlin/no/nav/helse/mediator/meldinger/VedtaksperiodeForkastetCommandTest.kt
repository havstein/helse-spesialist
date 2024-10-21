package no.nav.helse.mediator.meldinger

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.helse.Testdata.snapshot
import no.nav.helse.db.CommandContextRepository
import no.nav.helse.db.PersonRepository
import no.nav.helse.mediator.oppgave.OppgaveService
import no.nav.helse.modell.kommando.CommandContext
import no.nav.helse.modell.vedtaksperiode.VedtaksperiodeForkastetCommand
import no.nav.helse.spesialist.api.snapshot.SnapshotClient
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

internal class VedtaksperiodeForkastetCommandTest {
    private companion object {
        private val HENDELSE = UUID.randomUUID()
        private val VEDTAKSPERIODE = UUID.randomUUID()
        private val CONTEXT = UUID.randomUUID()
        private const val FNR = "fnr"
    }

    private val commandContextRepository = mockk<CommandContextRepository>(relaxed = true)
    private val personRepository = mockk<PersonRepository>(relaxed = true)
    private val graphQLClient = mockk<SnapshotClient>(relaxed = true)
    private val oppgaveService = mockk<OppgaveService>(relaxed = true)
    private val context = CommandContext(CONTEXT)
    private val vedtaksperiodeForkastetCommand =
        VedtaksperiodeForkastetCommand(
            fødselsnummer = FNR,
            vedtaksperiodeId = VEDTAKSPERIODE,
            id = HENDELSE,
            commandContextRepository = commandContextRepository,
            oppgaveService = oppgaveService,
            reservasjonRepository = mockk(relaxed = true),
            tildelingRepository = mockk(relaxed = true),
            oppgaveRepository = mockk(relaxed = true),
            totrinnsvurderingService = mockk(relaxed = true),
        )

    @Test
    fun `avbryter kommandoer og markerer vedtaksperiode som forkastet`() {
        val snapshot = snapshot(fødselsnummer = FNR)
        every { graphQLClient.hentSnapshot(FNR) } returns snapshot
        every { personRepository.finnPersonMedFødselsnummer(FNR) } returns 1
        assertTrue(vedtaksperiodeForkastetCommand.execute(context))
        verify(exactly = 1) { commandContextRepository.avbryt(VEDTAKSPERIODE, CONTEXT) }
    }
}
