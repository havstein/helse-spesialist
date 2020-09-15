package no.nav.helse.api

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.auth.jwt.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import no.nav.helse.mediator.kafka.HendelseMediator
import no.nav.helse.mediator.kafka.meldinger.AnnulleringMessage
import no.nav.helse.mediator.kafka.meldinger.OverstyringMessage
import no.nav.helse.modell.command.findNåværendeOppgave
import no.nav.helse.modell.command.finnOppgaveId
import no.nav.helse.modell.vedtak.SaksbehandlerLøsning
import no.nav.helse.vedtaksperiode.VedtaksperiodeMediator
import java.time.LocalDate
import java.util.*

internal fun Route.vedtaksperiodeApi(
    vedtaksperiodeMediator: VedtaksperiodeMediator,
    spleisbehovMediator: HendelseMediator
) {
    get("/api/person/{vedtaksperiodeId}") {
        val speilSnapshot = vedtaksperiodeMediator
            .byggSpeilSnapshotForVedtaksperiodeId(UUID.fromString(call.parameters["vedtaksperiodeId"]!!))
        if (speilSnapshot == null) {
            call.respond(HttpStatusCode.NotFound, "Fant ikke vedtaksperiode")
            return@get
        }
        call.respond(speilSnapshot)
    }
    get("/api/person/aktorId/{aktørId}") {
        call.parameters["aktørId"]?.toLongOrNull() ?: run {
            call.respond(status = HttpStatusCode.BadRequest, message = "AktørId må være numerisk")
            return@get
        }
        val speilSnapshot = vedtaksperiodeMediator
            .byggSpeilSnapshotForAktørId(call.parameters["aktørId"]!!)
        if (speilSnapshot == null) {
            call.respond(HttpStatusCode.NotFound, "Fant ikke vedtaksperiode")
            return@get
        }
        call.respond(speilSnapshot)
    }
    get("/api/person/fnr/{fødselsnummer}") {
        call.parameters["fødselsnummer"]?.toLongOrNull() ?: run {
            call.respond(status = HttpStatusCode.BadRequest, message = "Fødselsnummer må være numerisk")
            return@get
        }
        val speilSnapshot = vedtaksperiodeMediator
            .byggSpeilSnapshotForFnr(call.parameters["fødselsnummer"]!!)
        if (speilSnapshot == null) {
            call.respond(HttpStatusCode.NotFound, "Fant ikke vedtaksperiode")
            return@get
        }
        call.respond(speilSnapshot)
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
        val (oid, epostadresse) = requireNotNull(call.principal<JWTPrincipal>()).payload.let {
            UUID.fromString(it.getClaim("oid").asString()) to it.getClaim("preferred_username").asString()
        }

        if (!vedtaksperiodeMediator.harAktivOppgave(godkjenning.oppgavereferanse)) {
            call.respondText(
                "Dette vedtaket har ingen aktiv saksbehandleroppgave. Dette betyr vanligvis at oppgaven allerede er fullført.",
                status = HttpStatusCode.Conflict
            )
            return@post
        }

        spleisbehovMediator.håndter(godkjenning, epostadresse, oid)
        call.respond(HttpStatusCode.Created, mapOf("status" to "OK"))
    }
    post("/api/annullering") {
        val annullering = call.receive<Annullering>()
        val accessToken = requireNotNull(call.principal<JWTPrincipal>())
        val epostadresse = accessToken.payload.getClaim("preferred_username").asString()

        val message = AnnulleringMessage(
            aktørId = annullering.aktørId,
            fødselsnummer = annullering.fødselsnummer,
            organisasjonsnummer = annullering.organisasjonsnummer,
            fagsystemId = annullering.fagsystemId,
            saksbehandler = annullering.saksbehandlerIdent,
            saksbehandlerEpost = epostadresse
        )

        spleisbehovMediator.håndter(message)
        call.respond(HttpStatusCode.OK, mapOf("status" to "OK"))
    }

    post("/api/overstyr/dager") {
        val overstyring = call.receive<Overstyring>()

        val accessToken = requireNotNull(call.principal<JWTPrincipal>())
        val oid = UUID.fromString(accessToken.payload.getClaim("oid").asString())
        val epostadresse = accessToken.payload.getClaim("preferred_username").asString()
        val saksbehandlerNavn = accessToken.payload.getClaim("name").asString()

        val message = OverstyringRestDto(
            saksbehandlerEpost = epostadresse,
            saksbehandlerOid = oid,
            saksbehandlerNavn = saksbehandlerNavn,
            organisasjonsnummer = overstyring.organisasjonsnummer,
            fødselsnummer = overstyring.fødselsnummer,
            aktørId = overstyring.aktørId,
            begrunnelse = overstyring.begrunnelse,
            dager = overstyring.dager.map {
                OverstyringRestDto.Dag(
                    dato = it.dato,
                    type = enumValueOf(it.type),
                    grad = it.grad
                )
            }
        )
        spleisbehovMediator.håndter(message)
        call.respond(HttpStatusCode.OK, mapOf("status" to "OK"))
    }
}

data class OverstyringRestDto(
    val saksbehandlerEpost: String,
    val saksbehandlerOid: UUID,
    val saksbehandlerNavn: String,
    val organisasjonsnummer: String,
    val fødselsnummer: String,
    val aktørId: String,
    val begrunnelse: String,
    val dager: List<Dag>
) {
    data class Dag(
        val dato: LocalDate,
        val type: Type,
        val grad: Int?
    ) {
        enum class Type { Sykedag, Feriedag, Egenmeldingsdag }
    }
}

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

@JsonIgnoreProperties
data class Annullering(
    val aktørId: String,
    val fødselsnummer: String,
    val organisasjonsnummer: String,
    val fagsystemId: String,
    val saksbehandlerIdent: String,
    val vedtaksperiodeId: String
)

@JsonIgnoreProperties
class Overstyring(
    val organisasjonsnummer: String,
    val fødselsnummer: String,
    val aktørId: String,
    val begrunnelse: String,
    val dager: List<Overstyringdag>
) {
    class Overstyringdag(
        val dato: LocalDate,
        val type: String,
        val grad: Int?
    )
}
