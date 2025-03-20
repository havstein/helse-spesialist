package no.nav.helse.spesialist.api.graphql.mutation

import io.mockk.every
import io.mockk.verify
import no.nav.helse.TestRunner.runQuery
import no.nav.helse.spesialist.api.feilhåndtering.OppgaveIkkeTildelt
import no.nav.helse.spesialist.api.feilhåndtering.OppgaveTildeltNoenAndre
import no.nav.helse.spesialist.api.saksbehandler.handlinger.AvmeldOppgave
import no.nav.helse.spesialist.api.saksbehandler.handlinger.TildelOppgave
import no.nav.helse.spesialist.api.testfixtures.lagSaksbehandlerFraApi
import no.nav.helse.spesialist.api.testfixtures.mutation.fjernTildelingMutation
import no.nav.helse.spesialist.api.testfixtures.mutation.opprettTildelingMutation
import no.nav.helse.spesialist.api.tildeling.TildelingApiDto
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.random.Random.Default.nextLong

class TildelingMutationHandlerTest {
    @Test
    fun `oppretter tildeling`() {
        val oppgaveId = nextLong()
        val saksbehandler = lagSaksbehandlerFraApi()

        runQuery(
            saksbehandlerFraApi = saksbehandler,
            whenever = opprettTildelingMutation(oppgaveId),
            then = { _, body, avhengigheter ->
                verify(exactly = 1) { avhengigheter.saksbehandlerMediator.håndter(TildelOppgave(oppgaveId), saksbehandler) }
                assertEquals(saksbehandler.oid, UUID.fromString(body["data"]["opprettTildeling"]["oid"].asText()))
            }
        )
    }

    @Test
    fun `kan ikke tildele allerede tildelt oppgave`() {
        val oppgaveId = nextLong()

        runQuery(
            given = {
                every { it.saksbehandlerMediator.håndter(any<TildelOppgave>(), any()) } throws
                        OppgaveTildeltNoenAndre(TildelingApiDto("navn", "epost", UUID.randomUUID()))
            },
            whenever = opprettTildelingMutation(oppgaveId),
            then = { _, body, _ ->
                assertEquals(409, body["errors"].first()["extensions"]["code"].asInt())
            }
        )
    }

    @Test
    fun `kan fjerne tildeling`() {
        val oppgaveId = nextLong()

        runQuery(
            whenever = fjernTildelingMutation(oppgaveId),
            then = { _, body, _ ->
                assertTrue(body["data"]["fjernTildeling"].booleanValue())
            }
        )
    }

    @Test
    fun `returnerer false hvis oppgaven ikke er tildelt`() {
        val oppgaveId = nextLong()
        runQuery(
            given = {
                every { it.saksbehandlerMediator.håndter(any<AvmeldOppgave>(), any()) } throws OppgaveIkkeTildelt(oppgaveId)
            },
            whenever = fjernTildelingMutation(oppgaveId),
            then = { _, body, _ ->
                assertFalse(body["data"]["fjernTildeling"].booleanValue())
            }
        )
    }

    @Test
    fun `returnerer false hvis oppgaven ikke finnes`() {
        runQuery(
            given = {
                every { it.saksbehandlerMediator.håndter(any<AvmeldOppgave>(), any()) } throws IllegalStateException()
            },
            whenever = fjernTildelingMutation(nextLong()),
            then = { _, body, _ ->
                assertFalse(body["data"]["fjernTildeling"].booleanValue())
            }
        )
    }
}
