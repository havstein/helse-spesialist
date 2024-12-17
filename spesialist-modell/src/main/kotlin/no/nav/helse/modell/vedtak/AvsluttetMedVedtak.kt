package no.nav.helse.modell.vedtak

import java.time.LocalDateTime
import java.util.UUID

class AvsluttetMedVedtak(
    val spleisBehandlingId: UUID,
    private val hendelser: List<UUID>,
    private val sykepengegrunnlag: Double,
    private val grunnlagForSykepengegrunnlag: Double,
    private val grunnlagForSykepengegrunnlagPerArbeidsgiver: Map<String, Double>,
    private val begrensning: String,
    private val inntekt: Double,
    private val sykepengegrunnlagsfakta: Sykepengegrunnlagsfakta,
    private val vedtakFattetTidspunkt: LocalDateTime,
) {
    fun byggVedtak(vedtakBuilder: SykepengevedtakBuilder) {
        vedtakBuilder.hendelser(hendelser)
        vedtakBuilder.sykepengegrunnlag(sykepengegrunnlag)
        vedtakBuilder.grunnlagForSykepengegrunnlag(grunnlagForSykepengegrunnlag)
        vedtakBuilder.grunnlagForSykepengegrunnlagPerArbeidsgiver(grunnlagForSykepengegrunnlagPerArbeidsgiver)
        vedtakBuilder.begrensning(begrensning)
        vedtakBuilder.inntekt(inntekt)
        vedtakBuilder.vedtakFattetTidspunkt(vedtakFattetTidspunkt)
        vedtakBuilder.sykepengegrunnlagsfakta(sykepengegrunnlagsfakta)
    }
}
