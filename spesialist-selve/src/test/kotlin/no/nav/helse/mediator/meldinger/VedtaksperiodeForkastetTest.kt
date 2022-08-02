package no.nav.helse.mediator.meldinger

import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.UUID
import no.nav.helse.mediator.api.graphql.SnapshotClient
import no.nav.helse.modell.CommandContextDao
import no.nav.helse.modell.SnapshotDao
import no.nav.helse.modell.VedtakDao
import no.nav.helse.modell.WarningDao
import no.nav.helse.modell.kommando.CommandContext
import no.nav.helse.modell.person.PersonDao
import no.nav.helse.oppgave.OppgaveMediator
import no.nav.helse.snapshot
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class VedtaksperiodeForkastetTest {

    private companion object {
        private val HENDELSE = UUID.randomUUID()
        private val VEDTAKSPERIODE = UUID.randomUUID()
        private val CONTEXT = UUID.randomUUID()
        private const val FNR = "fnr"
        private val SNAPSHOT = snapshot(
            vedtaksperiodeId = VEDTAKSPERIODE,
            orgnr = "ORGNR",
            fnr = FNR,
            aktørId = "AKTØR",
        )
    }

    private val testmeldingfabrikk = Testmeldingfabrikk(FNR, "aktørid")
    private val commandContextDao = mockk<CommandContextDao>(relaxed = true)
    private val vedtakDao = mockk<VedtakDao>(relaxed = true)
    private val warningDao = mockk<WarningDao>(relaxed = true)
    private val personDao = mockk<PersonDao>(relaxed = true)
    private val snapshotDao = mockk<SnapshotDao>(relaxed = true)
    private val graphQLClient = mockk<SnapshotClient>(relaxed = true)
    private val oppgaveMediator = mockk<OppgaveMediator>(relaxed = true)
    private val context = CommandContext(CONTEXT)
    private val vedtaksperiodeForkastetMessage = VedtaksperiodeForkastet(
        id = HENDELSE,
        vedtaksperiodeId = VEDTAKSPERIODE,
        fødselsnummer = FNR,
        json = testmeldingfabrikk.lagVedtaksperiodeForkastet(
            HENDELSE,
            VEDTAKSPERIODE
        ),
        commandContextDao = commandContextDao,
        warningDao = warningDao,
        oppgaveMediator = oppgaveMediator,
        snapshotClient = graphQLClient,
        snapshotDao = snapshotDao,
        personDao = personDao
    )

    @BeforeEach
    fun setup() {
        clearMocks(commandContextDao, vedtakDao, snapshotDao, graphQLClient)
    }

    @Test
    fun `avbryter kommandoer og oppdaterer snapshot`() {
        every { graphQLClient.hentSnapshot(FNR) } returns SNAPSHOT
        every { snapshotDao.lagre(FNR, SNAPSHOT.data!!.person!!) } returns 1
        every { personDao.findPersonByFødselsnummer(FNR) } returns 1
        assertTrue(vedtaksperiodeForkastetMessage.execute(context))
        verify(exactly = 1) { commandContextDao.avbryt(VEDTAKSPERIODE, CONTEXT) }
        verify(exactly = 1) { snapshotDao.lagre(FNR, SNAPSHOT.data!!.person!!) }
    }
}
