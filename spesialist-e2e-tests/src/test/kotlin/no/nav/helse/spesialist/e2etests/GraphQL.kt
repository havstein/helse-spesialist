package no.nav.helse.spesialist.e2etests

import com.fasterxml.jackson.databind.JsonNode
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.accept
import io.ktor.client.request.bearerAuth
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
import org.junit.jupiter.api.assertNull

object GraphQL {
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

    fun call(
        operationName: String,
        saksbehandler: Saksbehandler,
        tilgangsgrupper: Set<Tilgangsgruppe>,
        variables: Map<String, Any>
    ): JsonNode {
        val (status, bodyAsText) = runBlocking {
            httpClient.post("http://localhost:${E2ETestApplikasjon.port}/graphql") {
                contentType(ContentType.Application.Json)
                accept(ContentType.Application.Json)
                bearerAuth(E2ETestApplikasjon.apiModuleIntegrationTestFixture.token(saksbehandler, tilgangsgrupper))
                setBody(
                    mapOf(
                        "query" to (this::class.java.getResourceAsStream("/graphql/$operationName.graphql")
                            ?.use { it.reader().readText() }
                            ?: error("Fant ikke $operationName.graphql")),
                        "operationName" to operationName,
                        "variables" to variables))
            }.let { it.status to it.bodyAsText() }
        }
        logg.info("Respons fra GraphQL: $bodyAsText")
        assertTrue(status.isSuccess()) { "Fikk HTTP-feilkode ${status.value} fra GraphQL" }
        return objectMapper.readTree(bodyAsText).also {
            assertNull(it["errors"]) {
                "Fikk feil i GraphQL-response"
            }
        }
    }
}
