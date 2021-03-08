package no.nav.helse.modell.vedtak.snapshot

import com.fasterxml.jackson.databind.JsonNode
import java.time.LocalDate
import java.util.*

data class PersonFraSpleisDto(
    val aktørId: String,
    val fødselsnummer: String,
    val dødsdato: LocalDate?,
    val arbeidsgivere: List<ArbeidsgiverFraSpleisDto>,
    val inntektsgrunnlag: JsonNode
)

data class ArbeidsgiverFraSpleisDto(
    val organisasjonsnummer: String,
    val id: UUID,
    val vedtaksperioder: List<JsonNode>
)

