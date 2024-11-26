package no.nav.helse.modell.vedtaksperiode

import no.nav.helse.modell.person.vedtaksperiode.VarselDto
import no.nav.helse.modell.vedtak.SaksbehandlerVurderingDto
import no.nav.helse.modell.vedtak.VedtakBegrunnelseDto
import java.time.LocalDate
import java.util.UUID

data class GenerasjonDto(
    val id: UUID,
    val vedtaksperiodeId: UUID,
    val utbetalingId: UUID?,
    val spleisBehandlingId: UUID?,
    val skjæringstidspunkt: LocalDate,
    val fom: LocalDate,
    val tom: LocalDate,
    val tilstand: TilstandDto,
    val tags: List<String>,
    val varsler: List<VarselDto>,
    val avslag: VedtakBegrunnelseDto?,
    val saksbehandlerVurdering: SaksbehandlerVurderingDto?,
)

enum class TilstandDto {
    VedtakFattet,
    VidereBehandlingAvklares,
    AvsluttetUtenVedtak,
    AvsluttetUtenVedtakMedVarsler,
    KlarTilBehandling,
}
