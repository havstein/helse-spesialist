package no.nav.helse.mediator.api

import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.client.*
import io.ktor.client.features.*
import io.ktor.client.features.json.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.jackson.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.helse.AzureAdAppConfig
import no.nav.helse.azureAdAppAuthentication
import no.nav.helse.mediator.api.AbstractApiTest.Companion.authentication
import no.nav.helse.mediator.api.AbstractApiTest.Companion.clientId
import no.nav.helse.mediator.api.AbstractApiTest.Companion.issuer
import no.nav.helse.mediator.api.AbstractApiTest.Companion.jwtStub
import no.nav.helse.objectMapper
import no.nav.helse.oppgave.OppgaveDto
import no.nav.helse.oppgave.OppgaveMediator
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle
import org.junit.jupiter.api.assertThrows
import java.net.ServerSocket
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@TestInstance(Lifecycle.PER_CLASS)
internal class OppgaveApiTest {
    private val httpPort = ServerSocket(0).use { it.localPort }
    private val oppgaveMediator = mockk<OppgaveMediator>(relaxed = true)
    private val KODE7_SAKSBEHANDLER_GROUP = "c21e58b6-d838-4cb0-919a-398cd40117e3"

    @BeforeAll
    fun setup() {
        embeddedServer(Netty, port = httpPort) {
            install(ContentNegotiation) { register(ContentType.Application.Json, JacksonConverter(objectMapper)) }
            val jwkProvider = jwtStub.getJwkProviderMock()
            val azureConfig = AzureAdAppConfig(
                clientId = clientId,
                issuer = issuer,
                jwkProvider = jwkProvider
            )
            azureAdAppAuthentication(azureConfig)
            routing {
                authenticate("oidc") {
                    oppgaveApi(
                        oppgaveMediator = oppgaveMediator,
                        riskSupersaksbehandlergruppe = UUID.randomUUID().toString(),
                        kode7Saksbehandlergruppe = UUID.randomUUID().toString()
                    )
                }
            }
        }.also {
            it.start(wait = false)
        }
    }


    private val client = HttpClient {
        defaultRequest {
            host = "localhost"
            port = httpPort
        }
        install(JsonFeature) {
            serializer = JacksonSerializer(jackson = objectMapper)
        }
    }

    @Test
    fun `returnerer 400 bad request hvis fødselsnummer ikke er satt eller er null`() {
        assertThrows<ClientRequestException> {
            runBlocking {
                client.get<HttpStatement>("/api/v1/oppgave") { header("fodselsnummer", null) }.execute()
            }
        }
    }

    @Test
    fun `får 404 not found hvis oppgaven ikke finnes`() {
        every { oppgaveMediator.hentOppgaveId(any()) } returns null
        assertThrows<ClientRequestException> {
            runBlocking {
                client.get<HttpStatement>("/api/v1/oppgave") { header("fodselsnummer", "42069") }.execute()
            }
        }
    }

    @Test
    fun `returnerer 200 og tom liste hvis den ikke finner noen tilgjengelige oppgaver`() {
        every { oppgaveMediator.hentOppgaver(any()) } returns emptyList()
        val response = runBlocking {
            client.get<HttpResponse>("/api/oppgaver") {
                contentType(ContentType.Application.Json)
                authentication(oid = UUID.randomUUID(), group = KODE7_SAKSBEHANDLER_GROUP)
            }
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }
}
