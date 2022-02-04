package no.nav.helse.mediator.api

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.auth.jwt.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.helse.getGrupper
import no.nav.helse.mediator.HendelseMediator
import no.nav.helse.mediator.api.PersonMediator.SnapshotResponse.SnapshotTilstand.FINNES_IKKE
import no.nav.helse.mediator.api.PersonMediator.SnapshotResponse.SnapshotTilstand.INGEN_TILGANG
import org.slf4j.LoggerFactory
import java.util.*

internal fun Route.personApi(
    personMediator: PersonMediator,
    hendelseMediator: HendelseMediator,
    kode7Saksbehandlergruppe: UUID
) {
    val log = LoggerFactory.getLogger("PersonApi")

    get("/api/person/{vedtaksperiodeId}") {
        val kanSeKode7 = getGrupper().contains(kode7Saksbehandlergruppe)
        val snapshotResponse = withContext(Dispatchers.IO) {
            personMediator
                .byggSpeilSnapshotForVedtaksperiodeId(
                    UUID.fromString(call.parameters["vedtaksperiodeId"]!!),
                    kanSeKode7
                )
        }
        if (snapshotResponse.tilstand == INGEN_TILGANG) {
            call.respond(HttpStatusCode.Forbidden, "Har ikke tilgang til denne vedtaksperioden")
            return@get
        }
        if (snapshotResponse.snapshot == null || snapshotResponse.tilstand == FINNES_IKKE) {
            call.respond(HttpStatusCode.NotFound, "Fant ikke vedtaksperiode")
            return@get
        }
        call.respond(snapshotResponse.snapshot)
    }
    get("/api/person/aktorId/{aktørId}") {
        val kanSeKode7 = getGrupper().contains(kode7Saksbehandlergruppe)
        call.parameters["aktørId"]?.toLongOrNull() ?: run {
            call.respond(status = HttpStatusCode.BadRequest, message = "AktørId må være numerisk")
            return@get
        }
        val snapshotResponse = withContext(Dispatchers.IO) {
            personMediator
                .byggSpeilSnapshotForAktørId(call.parameters["aktørId"]!!, kanSeKode7)
        }
        if (snapshotResponse.tilstand == INGEN_TILGANG) {
            call.respond(HttpStatusCode.Forbidden, "Har ikke tilgang til denne vedtaksperioden")
            return@get
        }
        if (snapshotResponse.snapshot == null || snapshotResponse.tilstand == FINNES_IKKE) {
            call.respond(HttpStatusCode.NotFound, "Fant ikke vedtaksperiode")
            return@get
        }
        call.respond(snapshotResponse.snapshot)
    }
    get("/api/person/fnr/{fødselsnummer}") {
        val kanSeKode7 = getGrupper().contains(kode7Saksbehandlergruppe)
        call.parameters["fødselsnummer"]?.toLongOrNull() ?: run {
            call.respond(status = HttpStatusCode.BadRequest, message = "Fødselsnummer må være numerisk")
            return@get
        }
        val snapshotResponse = withContext(Dispatchers.IO) {
            personMediator
                .byggSpeilSnapshotForFnr(call.parameters["fødselsnummer"]!!, kanSeKode7)
        }
        if (snapshotResponse.tilstand == INGEN_TILGANG) {
            call.respond(HttpStatusCode.Forbidden, "Har ikke tilgang til denne vedtaksperioden")
            return@get
        }
        if (snapshotResponse.snapshot == null || snapshotResponse.tilstand == FINNES_IKKE) {
            call.respond(HttpStatusCode.NotFound, "Fant ikke vedtaksperiode")
            return@get
        }
        call.respond(snapshotResponse.snapshot)
    }
    get("/api/person/aktorId/{aktørId}/sist_oppdatert") {
        call.parameters["aktørId"]?.toLongOrNull() ?: run {
            call.respond(status = HttpStatusCode.BadRequest, message = "AktørId må være numerisk")
            return@get
        }
    }
    get("/api/person/fnr/{fødselsnummer}/sist_oppdatert") {
        call.parameters["fødselsnummer"]?.toLongOrNull() ?: run {
            call.respond(status = HttpStatusCode.BadRequest, message = "Fødselsnummer må være numerisk")
            return@get
        }
    }
    post("/api/vedtak") {
        val godkjenning = call.receive<GodkjenningDTO>()
        log.info("Behandler godkjenning av ${godkjenning.oppgavereferanse}")
        val (oid, epostadresse) = requireNotNull(call.principal<JWTPrincipal>()).payload.let {
            UUID.fromString(it.getClaim("oid").asString()) to it.getClaim("preferred_username").asString()
        }

        val erAktivOppgave =
            withContext(Dispatchers.IO) { personMediator.erAktivOppgave(godkjenning.oppgavereferanse) }
        if (!erAktivOppgave) {
            call.respondText(
                "Dette vedtaket har ingen aktiv saksbehandleroppgave. Dette betyr vanligvis at oppgaven allerede er fullført.",
                status = HttpStatusCode.Conflict
            )
            return@post
        }

        withContext(Dispatchers.IO) { hendelseMediator.håndter(godkjenning, epostadresse, oid) }
        call.respond(HttpStatusCode.Created, mapOf("status" to "OK"))
    }

    post("/api/person/oppdater") {
        val personoppdateringDto = call.receive<OppdaterPersonsnapshotDto>()
        hendelseMediator.håndter(personoppdateringDto)
        call.respond(HttpStatusCode.OK)
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class OppdaterPersonsnapshotDto(
    val fødselsnummer: String
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GodkjenningDTO(
    val oppgavereferanse: Long,
    val godkjent: Boolean,
    val saksbehandlerIdent: String,
    val årsak: String?,
    val begrunnelser: List<String>?,
    val kommentar: String?
) {
    init {
        if (!godkjent) requireNotNull(årsak)
    }
}
