package no.nav.helse.spesialist.api.testfixtures

import io.ktor.http.ContentType
import io.ktor.server.application.Application
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import no.nav.helse.spesialist.api.ApiModule
import no.nav.helse.spesialist.api.saksbehandler.SaksbehandlerFraApi
import no.nav.helse.spesialist.application.tilgangskontroll.Tilgangsgrupper
import no.nav.helse.spesialist.domain.tilgangskontroll.Tilgangsgruppe
import no.nav.security.mock.oauth2.MockOAuth2Server
import java.util.UUID

class ApiModuleIntegrationTestFixture(
    private val mockOAuth2Server: MockOAuth2Server = MockOAuth2Server().also(MockOAuth2Server::start),
    private val tilgangsgrupper: Tilgangsgrupper,
) {
    val token: String =
        mockOAuth2Server.issueToken(
            issuerId = ISSUER_ID,
            audience = CLIENT_ID,
            claims =
                mapOf(
                    "preferred_username" to "saksbehandler@nav.no",
                    "oid" to "${UUID.randomUUID()}",
                    "name" to "En Saksbehandler",
                    "NAVident" to "X123456",
                ),
        ).serialize().also {
            println("OAuth2-token:")
            println(it)
        }

    fun token(saksbehandlerFraApi: SaksbehandlerFraApi, tilgangsgrupper: Set<Tilgangsgruppe>): String =
        mockOAuth2Server.issueToken(
            issuerId = ISSUER_ID,
            audience = CLIENT_ID,
            subject = saksbehandlerFraApi.oid.toString(),
            claims = mapOf(
                "NAVident" to saksbehandlerFraApi.ident,
                "preferred_username" to saksbehandlerFraApi.epost,
                "oid" to saksbehandlerFraApi.oid.toString(),
                "name" to saksbehandlerFraApi.navn,
                "groups" to this.tilgangsgrupper.uuiderFor(tilgangsgrupper).map { it.toString() }
            )
    ).serialize()

    val apiModuleConfiguration = ApiModule.Configuration(
        clientId = CLIENT_ID,
        issuerUrl = mockOAuth2Server.issuerUrl(ISSUER_ID).toString(),
        jwkProviderUri = mockOAuth2Server.jwksUrl(ISSUER_ID).toString(),
        tokenEndpoint = mockOAuth2Server.tokenEndpointUrl(ISSUER_ID).toString(),
    )

    fun addAdditionalRoutings(application: Application) {
        with(application) {
            routing {
                get("/local-token") {
                    return@get call.respond<String>(message = token)
                }
                get("playground") {
                    call.respondText(buildPlaygroundHtml(), ContentType.Text.Html)
                }
            }
        }
    }

    private fun buildPlaygroundHtml() = Application::class.java.classLoader
        .getResource("graphql-playground.html")
        ?.readText()
        ?.replace("\${graphQLEndpoint}", "graphql")
        ?.replace("\${subscriptionsEndpoint}", "subscriptions")
        ?: throw IllegalStateException("graphql-playground.html cannot be found in the classpath")

    companion object {
        private const val CLIENT_ID = "en-client-id"
        private const val ISSUER_ID = "LocalTestIssuer"
    }
}
