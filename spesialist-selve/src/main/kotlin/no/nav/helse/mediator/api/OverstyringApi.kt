package no.nav.helse.mediator.api

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.JsonNode
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.helse.mediator.HendelseMediator
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.asLocalDate
import no.nav.helse.spesialist.api.overstyring.OverstyrTidslinjeDto
import no.nav.helse.spesialist.api.overstyring.OverstyrTidslinjeKafkaDto
import no.nav.helse.spesialist.api.overstyring.RefusjonselementDto
import no.nav.helse.spesialist.api.overstyring.SubsumsjonDto
import no.nav.helse.spesialist.api.saksbehandler.Saksbehandler
import no.nav.helse.spesialist.api.saksbehandler.SaksbehandlerDto

internal fun Route.overstyringApi(hendelseMediator: HendelseMediator) {
    post("/api/overstyr/dager") {
        val overstyring = call.receive<OverstyrTidslinjeDto>()

        val accessToken = requireNotNull(call.principal<JWTPrincipal>())
        val oid = UUID.fromString(accessToken.payload.getClaim("oid").asString())
        val epostadresse = accessToken.payload.getClaim("preferred_username").asString()
        val saksbehandlerNavn = accessToken.payload.getClaim("name").asString()
        val saksbehandlerIdent = accessToken.payload.getClaim("NAVident").asString()

        val message = OverstyrTidslinjeKafkaDto(
            saksbehandlerEpost = epostadresse,
            saksbehandlerOid = oid,
            saksbehandlerNavn = saksbehandlerNavn,
            saksbehandlerIdent = saksbehandlerIdent,
            organisasjonsnummer = overstyring.organisasjonsnummer,
            fødselsnummer = overstyring.fødselsnummer,
            aktørId = overstyring.aktørId,
            begrunnelse = overstyring.begrunnelse,
            dager = overstyring.dager.map {
                OverstyrTidslinjeKafkaDto.OverstyrDagKafkaDto(
                    dato = it.dato,
                    type = enumValueOf(it.type),
                    fraType = enumValueOf(it.fraType),
                    grad = it.grad,
                    fraGrad = it.fraGrad
                )
            }
        )
        withContext(Dispatchers.IO) { hendelseMediator.håndter(message) }
        call.respond(HttpStatusCode.OK, mapOf("status" to "OK"))
    }

    post("/api/overstyr/inntektogrefusjon") {
        val overstyring = call.receive<OverstyrInntektOgRefusjonDTO>()
        val saksbehandler = Saksbehandler.fraOnBehalfOfToken(requireNotNull(call.principal()))

        val message = OverstyrInntektOgRefusjonKafkaDto(
            fødselsnummer = overstyring.fødselsnummer,
            aktørId = overstyring.aktørId,
            skjæringstidspunkt = overstyring.skjæringstidspunkt,
            saksbehandler = saksbehandler.toDto(),
            arbeidsgivere = overstyring.arbeidsgivere,
        )
        withContext(Dispatchers.IO) { hendelseMediator.håndter(message) }
        call.respond(HttpStatusCode.OK, mapOf("status" to "OK"))
    }

    post("/api/overstyr/arbeidsforhold") {
        val overstyring = call.receive<OverstyrArbeidsforholdDto>()

        val saksbehandler = Saksbehandler.fraOnBehalfOfToken(requireNotNull(call.principal()))

        val message = OverstyrArbeidsforholdKafkaDto(
            saksbehandler = saksbehandler.toDto(),
            fødselsnummer = overstyring.fødselsnummer,
            aktørId = overstyring.aktørId,
            skjæringstidspunkt = overstyring.skjæringstidspunkt,
            overstyrteArbeidsforhold = overstyring.overstyrteArbeidsforhold
        )
        withContext(Dispatchers.IO) { hendelseMediator.håndter(message) }
        call.respond(HttpStatusCode.OK, mapOf("status" to "OK"))
    }
}


data class OverstyrInntektOgRefusjonDTO(
    val aktørId: String,
    val fødselsnummer: String,
    val skjæringstidspunkt: LocalDate,
    val arbeidsgivere: List<Arbeidsgiver>,
)

data class Arbeidsgiver(
    val organisasjonsnummer: String,
    val månedligInntekt: Double,
    val fraMånedligInntekt: Double,
    val refusjonsopplysninger: List<RefusjonselementDto>?,
    val fraRefusjonsopplysninger: List<RefusjonselementDto>?,
    val begrunnelse: String,
    val forklaring: String,
    val subsumsjon: SubsumsjonDto?,
) {
    fun toMap(): Map<String, Any?> = listOfNotNull(
        "organisasjonsnummer" to organisasjonsnummer,
        "månedligInntekt" to månedligInntekt,
        "fraMånedligInntekt" to fraMånedligInntekt,
        "refusjonsopplysninger" to refusjonsopplysninger,
        "fraRefusjonsopplysninger" to fraRefusjonsopplysninger,
        "begrunnelse" to begrunnelse,
        "forklaring" to forklaring,
        "subsumsjon" to subsumsjon,
    ).toMap()
}

data class OverstyrInntektOgRefusjonKafkaDto(
    val saksbehandler: SaksbehandlerDto,
    val fødselsnummer: String,
    val aktørId: String,
    val skjæringstidspunkt: LocalDate,
    val arbeidsgivere: List<Arbeidsgiver>,
) {
    fun somKafkaMessage() = JsonMessage.newMessage(
        "saksbehandler_overstyrer_inntekt_og_refusjon",
        listOfNotNull(
            "aktørId" to aktørId,
            "fødselsnummer" to fødselsnummer,
            "skjæringstidspunkt" to skjæringstidspunkt,
            "arbeidsgivere" to arbeidsgivere.toMap(),
            "saksbehandlerOid" to saksbehandler.oid,
            "saksbehandlerNavn" to saksbehandler.navn,
            "saksbehandlerIdent" to saksbehandler.ident,
            "saksbehandlerEpost" to saksbehandler.epost,
        ).toMap()
    )
}

internal fun JsonNode.refusjonselementer(): List<RefusjonselementDto>? {
    if (this.isNull) return null
    return this.map { jsonNode ->
        RefusjonselementDto(
            fom = jsonNode["fom"].asLocalDate(),
            tom = if (jsonNode["tom"].isNull) null else jsonNode["tom"].asLocalDate(),
            beløp = jsonNode["beløp"].asDouble()
        )
    }
}

internal fun JsonNode.subsumsjonelementer(): SubsumsjonDto? {
    if (this.isNull) return null
    return SubsumsjonDto(
        paragraf = this["paragraf"].asText(),
        ledd = if (this["ledd"].isNull) null else this["ledd"].asText(),
        bokstav = if (this["bokstav"].isNull) null else this["bokstav"].asText(),
    )
}

internal fun JsonNode.arbeidsgiverelementer(): List<Arbeidsgiver> {
    return this.map { jsonNode ->
        Arbeidsgiver(
            organisasjonsnummer = jsonNode["organisasjonsnummer"].asText(),
            månedligInntekt = jsonNode["månedligInntekt"].asDouble(),
            fraMånedligInntekt = jsonNode["fraMånedligInntekt"].asDouble(),
            refusjonsopplysninger = jsonNode["refusjonsopplysninger"].refusjonselementer(),
            fraRefusjonsopplysninger = jsonNode["fraRefusjonsopplysninger"].refusjonselementer(),
            begrunnelse = jsonNode["begrunnelse"].asText(),
            forklaring = jsonNode["forklaring"].asText(),
            subsumsjon = jsonNode["subsumsjon"].subsumsjonelementer()
        )
    }
}
@JvmName("Refusjonselement")
fun List<RefusjonselementDto>.toMap(): List<Map<String, Any?>> = this.map { it.toMap() }
@JvmName("Arbeidsgivere")
fun List<Arbeidsgiver>.toMap(): List<Map<String, Any?>> = this.map { it.toMap() }

@JsonIgnoreProperties
data class OverstyrArbeidsforholdDto(
    val fødselsnummer: String,
    val aktørId: String,
    val skjæringstidspunkt: LocalDate,
    val overstyrteArbeidsforhold: List<ArbeidsforholdOverstyrt>
) {
    @JsonIgnoreProperties
    data class ArbeidsforholdOverstyrt(
        val orgnummer: String,
        val deaktivert: Boolean,
        val begrunnelse: String,
        val forklaring: String
    )
}

data class OverstyrArbeidsforholdKafkaDto(
    val saksbehandler: SaksbehandlerDto,
    val fødselsnummer: String,
    val aktørId: String,
    val skjæringstidspunkt: LocalDate,
    val overstyrteArbeidsforhold: List<OverstyrArbeidsforholdDto.ArbeidsforholdOverstyrt>
) {
    fun somKafkaMessage() = JsonMessage.newMessage(
        "saksbehandler_overstyrer_arbeidsforhold", mapOf(
            "fødselsnummer" to fødselsnummer,
            "aktørId" to aktørId,
            "saksbehandlerOid" to saksbehandler.oid,
            "saksbehandlerNavn" to saksbehandler.navn,
            "saksbehandlerIdent" to saksbehandler.ident,
            "saksbehandlerEpost" to saksbehandler.epost,
            "skjæringstidspunkt" to skjæringstidspunkt,
            "overstyrteArbeidsforhold" to overstyrteArbeidsforhold,
        )
    )
}
