package no.nav.helse.spesialist.api

import com.fasterxml.jackson.databind.JsonNode
import io.ktor.http.HttpMethod
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.plugins.callloging.processingTimeMillis
import io.ktor.server.request.httpMethod
import io.ktor.server.request.receive
import io.prometheus.client.Counter
import io.prometheus.client.Summary
import org.slf4j.LoggerFactory

private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")

val GraphQLMetrikker =
    createRouteScopedPlugin("GraphQLMetrikker") {
        onCallReceive { call ->
            // Vi bryr oss ikke om get requests, fordi graphql kun benytter seg av post
            if (call.request.httpMethod == HttpMethod.Get) return@onCallReceive
            sikkerlogg.trace("GraphQL-kall mottatt, operationName: ${operationName(call)}")
        }

        onCallRespond { call ->
            // Vi bryr oss ikke om get requests, fordi graphql kun benytter seg av post
            if (call.request.httpMethod == HttpMethod.Get) return@onCallRespond
            sikkerlogg.trace("${operationName(call)} tok ${call.processingTimeMillis()}ms")
        }
    }

private suspend fun operationName(call: ApplicationCall) = call.receive<JsonNode>()["operationName"]?.textValue() ?: "ukjent"

private val graphQLResponstider =
    Summary.build("graphql_responstider", "Måler responstider for GraphQL-kall")
        .labelNames("operationName")
        .register()

internal val auditLogTeller = Counter.build("auditlog_total", "Teller antall auditlogginnslag").register()
