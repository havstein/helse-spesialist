package no.nav.helse.e2e

import AbstractE2ETest
import io.mockk.every
import no.nav.helse.modell.SnapshotDao
import no.nav.helse.modell.VedtakDao
import no.nav.helse.modell.arbeidsgiver.ArbeidsgiverDao
import no.nav.helse.modell.command.OppgaveDao
import no.nav.helse.modell.overstyring.Dagtype
import no.nav.helse.modell.overstyring.OverstyringDagDto
import no.nav.helse.modell.overstyring.OverstyringDao
import no.nav.helse.modell.person.PersonDao
import no.nav.helse.modell.vedtak.snapshot.ArbeidsgiverFraSpleisDto
import no.nav.helse.modell.vedtak.snapshot.PersonFraSpleisDto
import no.nav.helse.tildeling.TildelingDao
import no.nav.helse.vedtaksperiode.VedtaksperiodeMediator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.LocalDate
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class OverstyringE2ETest : AbstractE2ETest() {
    private companion object {
        private val VEDTAKSPERIODE_ID = UUID.randomUUID()
        private const val FØDSELSNUMMER = "12020052345"
        private const val AKTØR = "999999999"
        private const val ORGNR = "222222222"
        private const val SAKSBEHANDLER_EPOST = "saksbehandler@nav.no"
        private const val SNAPSHOTV1 = "{}"
    }

    private val vedtakDao = VedtakDao(dataSource)
    private val personDao = PersonDao(dataSource)
    private val arbeidsgiverDao = ArbeidsgiverDao(dataSource)
    private val snapshotDao = SnapshotDao(dataSource)
    private val overstyringDao = OverstyringDao(dataSource)
    private val oppgaveDao = OppgaveDao(dataSource)
    private val tildelingDao = TildelingDao(dataSource)
    private val vedtaksperiodeMediator = VedtaksperiodeMediator(vedtakDao, personDao, arbeidsgiverDao, snapshotDao, overstyringDao, oppgaveDao, tildelingDao)

    @Test
    fun `saksbehandler overstyrer sykdomstidslinje`() {
        every { restClient.hentSpeilSpapshot(FØDSELSNUMMER) } returns SNAPSHOTV1
        val hendelseId = sendGodkjenningsbehov(
            ORGNR,
            VEDTAKSPERIODE_ID,
            LocalDate.of(2018, 1, 1),
            LocalDate.of(2018, 1, 31)
        )
        sendPersoninfoløsning(hendelseId, ORGNR, VEDTAKSPERIODE_ID)
        sendRisikovurderingløsning(hendelseId)
        assertSaksbehandlerOppgaveOpprettet(hendelseId)
        sendOverstyrteDager(ORGNR, SAKSBEHANDLER_EPOST, listOf(
            OverstyringDagDto(
                dato = LocalDate.of(2018, 1, 20),
                type = Dagtype.Feriedag,
                grad = null
            )
        ))

        assertTrue(overstyringDao.finnOverstyring(FØDSELSNUMMER, ORGNR).isNotEmpty())
        assertTrue(oppgaveDao.finnOppgaver().none { it.oppgavereferanse == testRapid.inspektør.oppgaveId(hendelseId) })

        sendGodkjenningsbehov(
            ORGNR,
            VEDTAKSPERIODE_ID,
            LocalDate.of(2018, 1, 1),
            LocalDate.of(2018, 1, 31)
        )
        sendRisikovurderingløsning(hendelseId)
        val oppgave = oppgaveDao.finnOppgaver().find { it.fødselsnummer == FØDSELSNUMMER }
        assertNotNull(oppgave)
        assertEquals(SAKSBEHANDLER_EPOST, oppgave.saksbehandlerepost)
    }

    @Test
    fun `legger ved overstyringer i speil snapshot`() {
        val hendelseId = sendGodkjenningsbehov(
            ORGNR,
            VEDTAKSPERIODE_ID,
            LocalDate.of(2018, 1, 1),
            LocalDate.of(2018, 1, 31)
        )
        every { restClient.hentSpeilSpapshot(FØDSELSNUMMER) } returns objectMapper.writeValueAsString(
            PersonFraSpleisDto(
                aktørId = AKTØR,
                fødselsnummer = FØDSELSNUMMER,
                arbeidsgivere = listOf(
                    ArbeidsgiverFraSpleisDto(
                        organisasjonsnummer = ORGNR,
                        id = hendelseId,
                        vedtaksperioder = emptyList()
                    )
                )
            )
        )
        sendPersoninfoløsning(hendelseId, ORGNR, VEDTAKSPERIODE_ID)
        sendRisikovurderingløsning(hendelseId)
        sendOverstyrteDager(
            ORGNR, SAKSBEHANDLER_EPOST, listOf(
                OverstyringDagDto(
                    dato = LocalDate.of(2018, 1, 20),
                    type = Dagtype.Feriedag,
                    grad = null
                )
            )
        )

        sendGodkjenningsbehov(
            ORGNR,
            VEDTAKSPERIODE_ID,
            LocalDate.of(2018, 1, 1),
            LocalDate.of(2018, 1, 31)
        )
        sendRisikovurderingløsning(hendelseId)

        // TODO: bør ikke koble seg på daoer i E2E
        assertTrue(oppgaveDao.finnOppgaver().any { it.fødselsnummer == FØDSELSNUMMER })

        val snapshot = vedtaksperiodeMediator.byggSpeilSnapshotForFnr(FØDSELSNUMMER)
        assertNotNull(snapshot)
        val overstyringer = snapshot.arbeidsgivere.first().overstyringer
        assertEquals(1, overstyringer.size)
        assertEquals(1, overstyringer.first().overstyrteDager.size)
    }

    private fun assertSaksbehandlerOppgaveOpprettet(hendelseId: UUID) {
        val saksbehandlerOppgaver = oppgaveDao.finnOppgaver()
        assertEquals(1, saksbehandlerOppgaver.filter { it.oppgavereferanse == testRapid.inspektør.oppgaveId(hendelseId) }.size)
        assertTrue(saksbehandlerOppgaver.any { it.oppgavereferanse == testRapid.inspektør.oppgaveId(hendelseId) })
    }
}
