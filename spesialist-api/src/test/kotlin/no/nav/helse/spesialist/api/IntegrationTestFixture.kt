package no.nav.helse.spesialist.api

import com.fasterxml.jackson.databind.JsonNode
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.accept
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.jackson.JacksonConverter
import io.ktor.server.testing.testApplication
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.helse.spesialist.api.testfixtures.ApiModuleIntegrationTestFixture
import no.nav.helse.spesialist.api.testfixtures.lagSaksbehandler
import no.nav.helse.spesialist.application.InMemoryMeldingPubliserer
import no.nav.helse.spesialist.application.InMemoryRepositoriesAndDaos
import no.nav.helse.spesialist.application.logg.logg
import no.nav.helse.spesialist.application.tilgangskontroll.randomTilgangsgruppeUuider
import no.nav.helse.spesialist.domain.Saksbehandler
import no.nav.helse.spesialist.domain.tilgangskontroll.Tilgangsgruppe
import no.nav.security.mock.oauth2.MockOAuth2Server
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals

class IntegrationTestFixture() {
    private val inMemoryRepositoriesAndDaos = InMemoryRepositoriesAndDaos()
    val daos = inMemoryRepositoriesAndDaos.daos
    val sessionFactory = inMemoryRepositoriesAndDaos.sessionFactory
    val meldingPubliserer = InMemoryMeldingPubliserer()

    companion object {
        val mockOAuth2Server = MockOAuth2Server().also(MockOAuth2Server::start)
        val tilgangsgruppeUuider = randomTilgangsgruppeUuider()
        val apiModuleIntegrationTestFixture = ApiModuleIntegrationTestFixture(
            mockOAuth2Server = mockOAuth2Server,
            tilgangsgruppeUuider = tilgangsgruppeUuider
        )
    }

    private val apiModule = ApiModule(
        configuration = apiModuleIntegrationTestFixture.apiModuleConfiguration,
        tilgangsgruppeUuider = tilgangsgruppeUuider,
        daos = daos,
        meldingPubliserer = meldingPubliserer,
        tilgangsgruppehenter = { emptySet() },
        sessionFactory = sessionFactory,
        versjonAvKode = "0.0.0",
        environmentToggles = mockk(relaxed = true),
        snapshothenter = mockk(relaxed = true),
        reservasjonshenter = mockk(relaxed = true),
    )

    fun executeQuery(
        @Language("GraphQL") query: String,
        saksbehandler: Saksbehandler = lagSaksbehandler(),
        tilgangsgrupper: Set<Tilgangsgruppe> = emptySet(),
    ): JsonNode {
        lateinit var responseJson: JsonNode

        testApplication {
            application {
                apiModule.setUpApi(this)
            }

            client = createClient {
                install(ContentNegotiation) {
                    register(ContentType.Application.Json, JacksonConverter(objectMapper))
                }
            }

            runBlocking {
                logg.info("Posting query: $query")
                responseJson = client.post("/graphql") {
                    contentType(ContentType.Application.Json)
                    accept(ContentType.Application.Json)
                    bearerAuth(apiModuleIntegrationTestFixture.token(saksbehandler, tilgangsgrupper))
                    setBody(mapOf("query" to query))
                }.body<JsonNode>()
                logg.info("Got response: $responseJson")
            }
        }

        return responseJson
    }

    class Response(
        val status: Int,
        val bodyAsText: String
    ) {
        val bodyAsJsonNode = bodyAsText.takeUnless(String::isEmpty)?.let(objectMapper::readTree)
    }

    fun post(
        url: String,
        body: String,
        saksbehandler: Saksbehandler = lagSaksbehandler(),
        tilgangsgrupper: Set<Tilgangsgruppe> = emptySet(),
    ): Response {
        lateinit var response: Response

        testApplication {
            application {
                apiModule.setUpApi(this)
            }

            client = createClient {
                install(ContentNegotiation) {
                    register(ContentType.Application.Json, JacksonConverter(objectMapper))
                }
            }

            runBlocking {
                logg.info("Sender POST $url med data $body")
                val httpResponse = client.post(url) {
                    contentType(ContentType.Application.Json)
                    accept(ContentType.Application.Json)
                    bearerAuth(apiModuleIntegrationTestFixture.token(saksbehandler, tilgangsgrupper))
                    setBody(body)
                }
                val bodyAsText = httpResponse.bodyAsText()
                logg.info("Fikk respons: $bodyAsText")
                response = Response(status = httpResponse.status.value, bodyAsText = bodyAsText)
            }
        }

        return response
    }

    fun assertPubliserteBehovLister(
        vararg publiserteBehovLister: InMemoryMeldingPubliserer.PublisertBehovListe
    ) {
        assertEquals(
            publiserteBehovLister.toList(),
            meldingPubliserer.publiserteBehovLister
        )
    }

    fun assertPubliserteKommandokjedeEndretEvents(
        vararg publiserteKommandokjedeEndretEvents: InMemoryMeldingPubliserer.PublisertKommandokjedeEndretEvent
    ) {
        assertEquals(
            publiserteKommandokjedeEndretEvents.toList(),
            meldingPubliserer.publiserteKommandokjedeEndretEvents
        )
    }

    fun assertPubliserteSubsumsjoner(
        vararg publiserteSubsumsjoner: InMemoryMeldingPubliserer.PublisertSubsumsjon
    ) {
        assertEquals(
            publiserteSubsumsjoner.toList(),
            meldingPubliserer.publiserteSubsumsjoner
        )
    }

    fun assertPubliserteUtgåendeHendelser(
        vararg publiserteUtgåendeHendelse: InMemoryMeldingPubliserer.PublisertUtgåendeHendelse
    ) {
        assertEquals(
            publiserteUtgåendeHendelse.toList(),
            meldingPubliserer.publiserteUtgåendeHendelser
        )
    }
}
