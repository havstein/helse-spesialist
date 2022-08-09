package no.nav.helse.mediator.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.ContentType
import java.util.UUID
import no.nav.helse.AccessTokenClient
import no.nav.helse.mediator.Toggle
import no.nav.helse.mediator.api.graphql.schema.Reservasjon
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class ReservasjonClient(
    private val httpClient: HttpClient,
    private val apiUrl: String,
    private val scope: String,
    private val accessTokenClient: AccessTokenClient,
) {
    private val sikkerLogg: Logger = LoggerFactory.getLogger("tjenestekall")

    internal suspend fun hentReservasjonsstatus(fnr: String): Reservasjon? {
        if (!Toggle.Reservasjonsregisteret.enabled) {
            return null
        }
        try {
            val accessToken = accessTokenClient.hentAccessToken(scope)
            val callId = UUID.randomUUID().toString()

            return httpClient.get("$apiUrl/rest/v1/person") {
                header("Authorization", "Bearer $accessToken")
                header("Nav-Personident", fnr)
                header("Nav-Call-Id", callId)
                accept(ContentType.Application.Json)
            }.body()
        } catch (e: Exception) {
            sikkerLogg.error("Feil under kall til Kontakt- og reservasjonsregisteret:", e)
        }

        return null
    }

}