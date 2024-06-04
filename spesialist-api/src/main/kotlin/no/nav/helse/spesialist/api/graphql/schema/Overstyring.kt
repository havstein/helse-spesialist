package no.nav.helse.spesialist.api.graphql.schema

import java.time.LocalDate

data class TidslinjeOverstyring(
    val vedtaksperiodeId: String,
    val organisasjonsnummer: String,
    val fodselsnummer: String,
    val aktorId: String,
    val begrunnelse: String,
    val dager: List<OverstyringDag>,
)

data class InntektOgRefusjonOverstyring(
    val aktorId: String,
    val fodselsnummer: String,
    val skjaringstidspunkt: LocalDate,
    val arbeidsgivere: List<OverstyringArbeidsgiver>,
    val vedtaksperiodeId: String,
)

data class ArbeidsforholdOverstyringHandling(
    val fodselsnummer: String,
    val aktorId: String,
    val skjaringstidspunkt: LocalDate,
    val overstyrteArbeidsforhold: List<OverstyringArbeidsforhold>,
    val vedtaksperiodeId: String,
)

data class OverstyringArbeidsforhold(
    val orgnummer: String,
    val deaktivert: Boolean,
    val begrunnelse: String,
    val forklaring: String,
    val lovhjemmel: Lovhjemmel?,
)

data class OverstyringArbeidsgiver(
    val organisasjonsnummer: String,
    val manedligInntekt: Double,
    val fraManedligInntekt: Double,
    val refusjonsopplysninger: List<OverstyringRefusjonselement>?,
    val fraRefusjonsopplysninger: List<OverstyringRefusjonselement>?,
    val begrunnelse: String,
    val forklaring: String,
    val lovhjemmel: Lovhjemmel?,
) {
    data class OverstyringRefusjonselement(
        val fom: LocalDate,
        val tom: LocalDate? = null,
        val belop: Double,
    )
}

data class OverstyringDag(
    val dato: LocalDate,
    val type: String,
    val fraType: String,
    val grad: Int?,
    val fraGrad: Int?,
    val lovhjemmel: Lovhjemmel?,
)
