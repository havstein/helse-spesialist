package no.nav.helse.mediator.meldinger

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.helse.db.OppgaveDao
import no.nav.helse.medRivers
import no.nav.helse.mediator.MeldingMediator
import no.nav.helse.modell.gosysoppgaver.OppgaveDataForAutomatisering
import no.nav.helse.modell.kommando.TilbakedateringBehandlet
import no.nav.helse.modell.vedtaksperiode.Periodetype
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

internal class TilbakedateringBehandletRiverTest {
    private val mediator = mockk<MeldingMediator>(relaxed = true)
    private val testRapid = TestRapid().medRivers(TilbakedateringBehandletRiver(mediator))
    private val oppgaveDao = mockk<OppgaveDao>(relaxed = true)

    @Test
    fun `Leser tilbakedatering behandlet`() {
        mocks()
        testRapid.sendTestMessage(event())
        verify(exactly = 1) { mediator.mottaMelding(any<TilbakedateringBehandlet>(), any()) }
    }

    private fun mocks(
        oppgaveId: Long = 1L,
        commandData: OppgaveDataForAutomatisering? =
            OppgaveDataForAutomatisering(
                oppgaveId = oppgaveId,
                vedtaksperiodeId = UUID.randomUUID(),
                periodeFom = LocalDate.now(),
                periodeTom = LocalDate.now(),
                utbetalingId = UUID.randomUUID(),
                hendelseId = UUID.randomUUID(),
                godkjenningsbehovJson = "{}",
                periodetype = Periodetype.FØRSTEGANGSBEHANDLING,
                skjæringstidspunkt = LocalDate.now(),
            ),
    ) {
        every { oppgaveDao.finnOppgaveId(any<String>()) }.returns(oppgaveId)
        every { oppgaveDao.oppgaveDataForAutomatisering(any()) }.returns(commandData)
    }

    @Language("JSON")
    private fun event() =
        """
    {
      "@event_name": "tilbakedatering_behandlet",
      "@id": "${UUID.randomUUID()}",
      "@opprettet": "${LocalDateTime.now()}",
      "fødselsnummer": "11111100000",
      "sykmeldingId": "${UUID.randomUUID()}",
      "syketilfelleStartDato": "${LocalDate.now()}",
      "perioder": [
          {
            "fom": "${LocalDate.now()}",
            "tom": "${LocalDate.now().plusDays(30)}"
          }
      ]
    }"""
}
