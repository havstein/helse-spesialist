package no.nav.helse.mediator.api

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import no.nav.helse.spesialist.api.feilhåndtering.FeilDto
import no.nav.helse.spesialist.api.feilhåndtering.OppgaveIkkeTildelt
import no.nav.helse.modell.leggpåvent.LeggPåVentService
import no.nav.helse.objectMapper
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import java.util.*
import no.nav.helse.spesialist.api.graphql.schema.NotatType
import no.nav.helse.spesialist.api.notat.NotatMediator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import kotlin.random.Random.Default.nextLong


@TestInstance(PER_CLASS)
internal class LeggPåVentApiTest : AbstractApiTest() {

    private val SAKSBEHANDLER_OID = UUID.randomUUID()

    private val leggPåVentService: LeggPåVentService = mockk(relaxed = true)
    private val notatMediator: NotatMediator = mockk(relaxed = true)

    @BeforeAll
    fun setupTildeling() {
        setupServer {
            leggPåVentApi(leggPåVentService, notatMediator)
        }
    }

    @AfterEach
    fun tearDownEach() {
        clearMocks(leggPåVentService, notatMediator)
    }

    @Test
    fun `kan legge en oppgave på vent med notat`() {
        val oppgavereferanse = nextLong()
        val notattekst = "en-tekst"
        val response = runBlocking {
            client.preparePost("/api/leggpaavent/${oppgavereferanse}") {
                contentType(ContentType.Application.Json)
                accept(ContentType.Application.Json)
                setBody(mapOf(
                    "tekst" to notattekst,
                    "type" to NotatType.PaaVent.name
                ))
                authentication(SAKSBEHANDLER_OID)
            }.execute()
        }

        assertTrue(response.status.isSuccess(), "HTTP response burde returnere en OK verdi, fikk ${response.status}")
        verify(exactly = 1) {
            leggPåVentService.leggOppgavePåVent(oppgavereferanse)
        }
        verify(exactly = 1) {
            notatMediator.lagreForOppgaveId(oppgavereferanse, notattekst, SAKSBEHANDLER_OID, NotatType.PaaVent)
        }
    }

    @Test
    fun `kan fjerne på vent`() {
        val oppgavereferanse = nextLong()
        val response = runBlocking {
            client.delete("/api/leggpaavent/${oppgavereferanse}") {
                contentType(ContentType.Application.Json)
                accept(ContentType.Application.Json)
                setBody(objectMapper.createObjectNode())
                authentication(SAKSBEHANDLER_OID)
            }
        }

        assertTrue(response.status.isSuccess(), "HTTP response burde returnere en OK verdi, fikk ${response.status}")
        verify(exactly = 1) {
            leggPåVentService.fjernPåVent(oppgavereferanse)
        }
    }

    @Test
    fun `gir feil hvis bruker forsøker å legge en oppgave på vent før den er tildelt bruker`() {
        every { leggPåVentService.leggOppgavePåVent(any()) } throws OppgaveIkkeTildelt(1L)
        val oppgavereferanse = nextLong()
        val response = runBlocking {
            client.preparePost("/api/leggpaavent/${oppgavereferanse}") {
                contentType(ContentType.Application.Json)
                accept(ContentType.Application.Json)
                setBody(mapOf(
                    "tekst" to "notattekst",
                    "type" to NotatType.PaaVent.name
                ))
                authentication(SAKSBEHANDLER_OID)
            }.execute()
        }

        assertEquals(HttpStatusCode.FailedDependency, response.status)
        val feil = runBlocking { response.body<FeilDto>() }
        assertEquals("oppgave_er_ikke_tildelt", feil.feilkode)
    }

    @Test
    fun `manglende oppgavereferanse POST gir Bad Request`() {
        val response = runBlocking {
            client.preparePost("/api/leggpaavent/null") {
                contentType(ContentType.Application.Json)
                accept(ContentType.Application.Json)
                setBody(objectMapper.createObjectNode())
                authentication(SAKSBEHANDLER_OID)
            }.execute()
        }

        assertEquals(response.status, HttpStatusCode.BadRequest, "HTTP response burde returnere en Bad request, fikk ${response.status}")
    }

    @Test
    fun `manglende oppgavereferanse DELETE gir Bad Request`() {
        val response = runBlocking {
            client.prepareDelete("/api/leggpaavent/null") {
                contentType(ContentType.Application.Json)
                accept(ContentType.Application.Json)
                setBody(objectMapper.createObjectNode())
                authentication(SAKSBEHANDLER_OID)
            }.execute()
        }
        assertEquals(response.status, HttpStatusCode.BadRequest, "HTTP response burde returnere en Bad request, fikk ${response.status}")
    }
}
