package no.nav.helse.modell.kommando

import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.util.UUID
import no.nav.helse.mediator.oppgave.OppgaveMediator
import no.nav.helse.modell.automatisering.Automatisering
import no.nav.helse.modell.oppgave.Oppgave
import no.nav.helse.modell.person.PersonDao
import no.nav.helse.modell.risiko.RisikovurderingDao
import no.nav.helse.modell.utbetaling.Utbetalingtype
import no.nav.helse.modell.utbetaling.Utbetalingtype.REVURDERING
import no.nav.helse.spesialist.api.oppgave.Oppgavetype
import no.nav.helse.spesialist.api.oppgave.Oppgavetype.DELVIS_REFUSJON
import no.nav.helse.spesialist.api.oppgave.Oppgavetype.FORTROLIG_ADRESSE
import no.nav.helse.spesialist.api.oppgave.Oppgavetype.RISK_QA
import no.nav.helse.spesialist.api.oppgave.Oppgavetype.STIKKPRØVE
import no.nav.helse.spesialist.api.oppgave.Oppgavetype.SØKNAD
import no.nav.helse.spesialist.api.oppgave.Oppgavetype.UTBETALING_TIL_SYKMELDT
import no.nav.helse.spesialist.api.person.Adressebeskyttelse
import no.nav.helse.spesialist.api.snapshot.SnapshotMediator
import no.nav.helse.spleis.graphql.enums.GraphQLUtbetalingstatus
import no.nav.helse.spleis.graphql.hentsnapshot.GraphQLUtbetaling
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import no.nav.helse.spleis.graphql.enums.Utbetalingtype as GraphQLUtbetalingtype

internal class OpprettSaksbehandleroppgaveCommandTest {
    private companion object {
        private val VEDTAKSPERIODE_ID = UUID.randomUUID()
        private val UTBETALING_ID = UUID.randomUUID()
        private const val FNR = "12345678910"
        private val hendelseId = UUID.randomUUID()
    }

    private val oppgaveMediator = mockk<OppgaveMediator>(relaxed = true)
    private val automatisering = mockk<Automatisering>(relaxed = true)
    private val personDao = mockk<PersonDao>(relaxed = true)
    private val snapshotMediator = mockk<SnapshotMediator>(relaxed = true)
    private val risikovurderingDao = mockk<RisikovurderingDao>(relaxed = true)
    private lateinit var context: CommandContext
    private lateinit var contextId: UUID
    private lateinit var utbetalingstype: Utbetalingtype
    private val command get() = OpprettSaksbehandleroppgaveCommand(
        fødselsnummer = FNR,
        vedtaksperiodeId = VEDTAKSPERIODE_ID,
        oppgaveMediator = oppgaveMediator,
        automatisering = automatisering,
        hendelseId = hendelseId,
        personDao = personDao,
        risikovurderingDao = risikovurderingDao,
        utbetalingId = UTBETALING_ID,
        utbetalingtype = utbetalingstype,
        snapshotMediator = snapshotMediator,
    )

    @BeforeEach
    fun setup() {
        contextId = UUID.randomUUID()
        context = CommandContext(contextId)
        utbetalingstype = Utbetalingtype.UTBETALING
        clearMocks(oppgaveMediator)
    }

    @Test
    fun `oppretter oppgave`() {
        val slot = slot<((Long) -> Oppgave)>()
        assertTrue(command.execute(context))
        verify(exactly = 1) { oppgaveMediator.nyOppgave(FNR, contextId, capture(slot)) }
        val oppgave = slot.captured.invoke(1L)
        assertEquals(enOppgave(SØKNAD), oppgave)
    }

    @Test
    fun `oppretter stikkprøve`() {
        every { automatisering.erStikkprøve(VEDTAKSPERIODE_ID, any()) } returns true
        val slot = slot<((Long) -> Oppgave)>()
        assertTrue(command.execute(context))
        verify(exactly = 1) { oppgaveMediator.nyOppgave(FNR, contextId, capture(slot)) }

        val oppgave = slot.captured.invoke(1L)
        assertEquals(enOppgave(STIKKPRØVE), oppgave)
    }

    @Test
    fun `oppretter risk QA`() {
        every { risikovurderingDao.kreverSupersaksbehandler(VEDTAKSPERIODE_ID) } returns true
        val slot = slot<((Long) -> Oppgave)>()
        assertTrue(command.execute(context))
        verify(exactly = 1) { oppgaveMediator.nyOppgave(FNR, contextId, capture(slot)) }

        val oppgave = slot.captured.invoke(1L)
        assertEquals(enOppgave(RISK_QA), oppgave)
    }

    @Test
    fun `oppretter revurdering`() {
        utbetalingstype = REVURDERING
        val slot = slot<((Long) -> Oppgave)>()
        assertTrue(command.execute(context))
        verify(exactly = 1) { oppgaveMediator.nyOppgave(FNR, contextId, capture(slot)) }

        val oppgave = slot.captured.invoke(1L)
        assertEquals(enOppgave(Oppgavetype.REVURDERING), oppgave)
    }

    @Test
    fun `oppretter oppgave med egen oppgavetype for fortrlig adressebeskyttelse`() {
        every { personDao.findAdressebeskyttelse(FNR) } returns Adressebeskyttelse.Fortrolig
        val slot = slot<((Long) -> Oppgave)>()
        assertTrue(command.execute(context))
        verify(exactly = 1) { oppgaveMediator.nyOppgave(FNR, contextId, capture(slot)) }

        val oppgave = slot.captured.invoke(1L)
        assertEquals(enOppgave(FORTROLIG_ADRESSE), oppgave)
    }

    @Test
    fun `oppretter oppgave med egen oppgavetype for utbetaling til sykmeldt`() {
        every { snapshotMediator.finnUtbetaling(FNR, UTBETALING_ID) } returns enUtbetaling(personbeløp = 500)
        val slot = slot<((Long) -> Oppgave)>()
        assertTrue(command.execute(context))
        verify(exactly = 1) { oppgaveMediator.nyOppgave(FNR, contextId, capture(slot)) }

        val oppgave = slot.captured.invoke(1L)
        assertEquals(enOppgave(UTBETALING_TIL_SYKMELDT), oppgave)
    }

    @Test
    fun `oppretter oppgave med egen oppgavetype for delvis refusjon`() {
        every { snapshotMediator.finnUtbetaling(FNR, UTBETALING_ID) } returns enUtbetaling(
            personbeløp = 500,
            arbeidsgiverbeløp = 500
        )
        val slot = slot<((Long) -> Oppgave)>()
        assertTrue(command.execute(context))
        verify(exactly = 1) { oppgaveMediator.nyOppgave(FNR, contextId, capture(slot)) }

        val oppgave = slot.captured.invoke(1L)
        assertEquals(enOppgave(DELVIS_REFUSJON), oppgave)
    }

    private fun enOppgave(vararg oppgavetype: Oppgavetype) =
        Oppgave.nyOppgave(1L, VEDTAKSPERIODE_ID, UTBETALING_ID, UUID.randomUUID(), oppgavetype.toList())


    private fun enUtbetaling(personbeløp: Int = 0, arbeidsgiverbeløp: Int = 0): GraphQLUtbetaling =
        GraphQLUtbetaling(
            id = UTBETALING_ID.toString(),
            arbeidsgiverFagsystemId = "EN_FAGSYSTEMID",
            arbeidsgiverNettoBelop = arbeidsgiverbeløp,
            personFagsystemId = "EN_FAGSYSTEMID",
            personNettoBelop = personbeløp,
            statusEnum = GraphQLUtbetalingstatus.GODKJENT,
            typeEnum = GraphQLUtbetalingtype.UTBETALING,
            vurdering = null,
            personoppdrag = null,
            arbeidsgiveroppdrag = null,
        )
}
