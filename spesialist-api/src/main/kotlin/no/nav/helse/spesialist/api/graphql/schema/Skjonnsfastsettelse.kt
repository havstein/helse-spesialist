package no.nav.helse.spesialist.api.graphql.schema

import no.nav.helse.spesialist.api.saksbehandler.handlinger.HandlingFraApi
import java.time.LocalDate
import java.util.UUID

data class Skjonnsfastsettelse(
    val aktorId: String,
    val fodselsnummer: String,
    val skjaringstidspunkt: LocalDate,
    val arbeidsgivere: List<SkjonnsfastsettelseArbeidsgiver>,
    val vedtaksperiodeId: UUID,
) : HandlingFraApi {
    data class SkjonnsfastsettelseArbeidsgiver(
        val organisasjonsnummer: String,
        val arlig: Double,
        val fraArlig: Double,
        val arsak: String,
        val type: SkjonnsfastsettelseType,
        val begrunnelseMal: String?,
        val begrunnelseFritekst: String?,
        val begrunnelseKonklusjon: String?,
        val lovhjemmel: Lovhjemmel?,
        val initierendeVedtaksperiodeId: String?,
    ) {
        enum class SkjonnsfastsettelseType {
            OMREGNET_ARSINNTEKT,
            RAPPORTERT_ARSINNTEKT,
            ANNET,
        }
    }
}
