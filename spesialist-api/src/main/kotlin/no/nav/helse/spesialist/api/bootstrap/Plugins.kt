package no.nav.helse.spesialist.api.bootstrap

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.JacksonConverter
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.callid.callIdMdc
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.doublereceive.DoubleReceive
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.plugins.statuspages.StatusPagesConfig
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.request.uri
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.websocket.WebSockets
import no.nav.helse.spesialist.api.feilhåndtering.Modellfeil
import no.nav.helse.spesialist.api.objectMapper
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import java.util.UUID

private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
private val logg = LoggerFactory.getLogger("SpesialistApp")

fun Application.installPlugins() {
    install(CallId) {
        retrieveFromHeader(HttpHeaders.XRequestId)
        generate {
            UUID.randomUUID().toString()
        }
    }
    install(WebSockets)
    install(StatusPages) { configureStatusPages() }
    install(CallLogging) {
        disableDefaultColors()
        logger = sikkerlogg
        level = Level.INFO
        callIdMdc("callId")
        filter { call -> call.request.path().let { it.startsWith("/graphql") || it.startsWith("/ws/") } }
    }
    install(DoubleReceive)
    install(ContentNegotiation) { register(ContentType.Application.Json, JacksonConverter(objectMapper)) }
    requestResponseTracing(sikkerlogg)
}

fun StatusPagesConfig.configureStatusPages() {
    exception<Modellfeil> { call: ApplicationCall, modellfeil: Modellfeil ->
        modellfeil.logger()
        call.respond(status = modellfeil.httpkode, message = modellfeil.tilFeilDto())
    }
    exception<Throwable> { call, cause ->
        val uri = call.request.uri
        val verb = call.request.httpMethod.value
        logg.error("Unhandled: $verb", cause)
        sikkerlogg.error("Unhandled: $verb - $uri", cause)
        call.respondText(
            text = "Det skjedde en uventet feil",
            status = HttpStatusCode.InternalServerError,
        )
        call.respond(HttpStatusCode.InternalServerError, "Det skjedde en uventet feil")
    }
}
