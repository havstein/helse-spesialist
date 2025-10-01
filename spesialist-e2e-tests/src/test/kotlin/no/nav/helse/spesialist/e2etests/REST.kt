package no.nav.helse.spesialist.e2etests

import com.fasterxml.jackson.databind.JsonNode
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.accept
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.jackson.JacksonConverter
import kotlinx.coroutines.runBlocking
import no.nav.helse.spesialist.application.logg.logg
import no.nav.helse.spesialist.domain.Saksbehandler
import no.nav.helse.spesialist.domain.tilgangskontroll.Tilgangsgruppe
import org.junit.jupiter.api.Assertions.assertTrue

object REST {
    private val httpClient: HttpClient =
        HttpClient(Apache) {
            install(ContentNegotiation) {
                register(ContentType.Application.Json, JacksonConverter())
            }
            engine {
                socketTimeout = 0
                connectTimeout = 1_000
                connectionRequestTimeout = 1_000
            }
        }

    fun get(
        relativeUrl: String,
        saksbehandler: Saksbehandler,
        tilgangsgrupper: Set<Tilgangsgruppe>,
    ): JsonNode {
        val url = "http://localhost:${E2ETestApplikasjon.port}/$relativeUrl"
        logg.info("Gjør HTTP GET $url")
        val (status, bodyAsText) = runBlocking {
            httpClient.get(url) {
                accept(ContentType.Application.Json)
                bearerAuth(E2ETestApplikasjon.apiModuleIntegrationTestFixture.token(saksbehandler, tilgangsgrupper))
            }.let { it.status to it.bodyAsText() }
        }
        logg.info("Respons fra HTTP GET: $bodyAsText")
        assertTrue(status.isSuccess()) { "Fikk HTTP-feilkode ${status.value} fra HTTP GET" }
        return objectMapper.readTree(bodyAsText)
    }

    fun post(
        relativeUrl: String,
        saksbehandler: Saksbehandler,
        tilgangsgrupper: Set<Tilgangsgruppe>,
        request: Any
    ): JsonNode {
        val url = "http://localhost:${E2ETestApplikasjon.port}/$relativeUrl"
        logg.info("Gjør HTTP POST $url med body: $request")
        val (status, bodyAsText) = runBlocking {
            httpClient.post(url) {
                bearerAuth(E2ETestApplikasjon.apiModuleIntegrationTestFixture.token(saksbehandler, tilgangsgrupper))
                accept(ContentType.Application.Json)
                contentType(ContentType.Application.Json)
                setBody(request)
            }.let { it.status to it.bodyAsText() }
        }
        logg.info("Respons fra HTTP POST: $bodyAsText")
        assertTrue(status.isSuccess()) { "Fikk HTTP-feilkode ${status.value} fra HTTP POST" }
        return objectMapper.readTree(bodyAsText)
    }
}
