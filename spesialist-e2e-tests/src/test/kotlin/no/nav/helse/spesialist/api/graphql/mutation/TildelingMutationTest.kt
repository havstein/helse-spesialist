package no.nav.helse.spesialist.api.graphql.mutation

import io.mockk.every
import io.mockk.verify
import no.nav.helse.spesialist.api.AbstractGraphQLApiTest
import no.nav.helse.spesialist.api.feilhåndtering.OppgaveIkkeTildelt
import no.nav.helse.spesialist.api.feilhåndtering.OppgaveTildeltNoenAndre
import no.nav.helse.spesialist.api.saksbehandler.handlinger.AvmeldOppgave
import no.nav.helse.spesialist.api.saksbehandler.handlinger.TildelOppgave
import no.nav.helse.spesialist.api.tildeling.TildelingApiDto
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

internal class TildelingMutationTest : AbstractGraphQLApiTest() {

    @Test
    fun `oppretter tildeling`() {
        val oppgaveId = 1L

        val body = runQuery(
            """
                mutation OpprettTildeling {
                    opprettTildeling(
                        oppgaveId: "$oppgaveId",
                    ) {
                        navn, oid, epost
                    }
                }
            """
        )

        verify(exactly = 1) { saksbehandlerMediator.håndter(TildelOppgave(oppgaveId), any()) }

        assertEquals(SAKSBEHANDLER.oid, UUID.fromString(body["data"]["opprettTildeling"]["oid"].asText()))
    }

    @Test
    fun `kan ikke tildele allerede tildelt oppgave`() {
        val oppgaveId = 1L

        every { saksbehandlerMediator.håndter(any<TildelOppgave>(), any()) } throws OppgaveTildeltNoenAndre(TildelingApiDto("navn", "epost", UUID.randomUUID()))

        val body = runQuery(
            """
                mutation OpprettTildeling {
                    opprettTildeling(
                        oppgaveId: "$oppgaveId",
                    ) {
                        navn, oid, epost
                    }
                }
            """
        )

        assertEquals(409, body["errors"].first()["extensions"]["code"].asInt())
    }

    @Test
    fun `kan fjerne tildeling`() {
        opprettSaksbehandler()
        opprettVedtaksperiode(opprettPerson(), opprettArbeidsgiver())
        val oppgaveId = finnOppgaveIdFor(PERIODE.id)
        tildelOppgave(oppgaveId, SAKSBEHANDLER.oid)

        val body = runQuery(
            """
                mutation FjernTildeling {
                    fjernTildeling(
                        oppgaveId: "$oppgaveId"
                    )
                }
            """
        )

        assertTrue(body["data"]["fjernTildeling"].booleanValue())
    }

    @Test
    fun `returnerer false hvis oppgaven ikke er tildelt`() {
        val oppgaveId = 1L
        every { saksbehandlerMediator.håndter(any<AvmeldOppgave>(), any()) } throws OppgaveIkkeTildelt(oppgaveId)
        val body = runQuery(
            """
                mutation FjernTildeling {
                    fjernTildeling(
                        oppgaveId: "$oppgaveId"
                    )
                }
            """
        )

        assertFalse(body["data"]["fjernTildeling"].booleanValue())
    }

    @Test
    fun `returnerer false hvis oppgaven ikke finnes`() {
        every { saksbehandlerMediator.håndter(any<AvmeldOppgave>(), any()) } throws IllegalStateException()

        val body = runQuery(
            """
                mutation FjernTildeling {
                    fjernTildeling(
                        oppgaveId: "999"
                    )
                }
            """
        )

        assertFalse(body["data"]["fjernTildeling"].booleanValue())
    }
}
