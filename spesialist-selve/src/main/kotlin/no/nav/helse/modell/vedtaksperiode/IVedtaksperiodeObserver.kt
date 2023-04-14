package no.nav.helse.modell.vedtaksperiode

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

interface IVedtaksperiodeObserver {

    fun tidslinjeOppdatert(generasjonId: UUID, fom: LocalDate, tom: LocalDate, skjæringstidspunkt: LocalDate) {}
    fun generasjonOpprettet(generasjonId: UUID, vedtaksperiodeId: UUID, hendelseId: UUID, fom: LocalDate, tom: LocalDate, skjæringstidspunkt: LocalDate) {}
    fun nyUtbetaling(generasjonId: UUID, utbetalingId: UUID) {}
    fun utbetalingForkastet(generasjonId: UUID, utbetalingId: UUID) {}
    fun vedtakFattet(generasjonId: UUID, hendelseId: UUID) {}
    fun førsteGenerasjonOpprettet(generasjonId: UUID, vedtaksperiodeId: UUID, hendelseId: UUID, fom: LocalDate, tom: LocalDate, skjæringstidspunkt: LocalDate) {}
    fun varselOpprettet(varselId: UUID, vedtaksperiodeId: UUID, generasjonId: UUID, varselkode: String, opprettet: LocalDateTime) {}
    fun varselReaktivert(varselId: UUID, varselkode: String, generasjonId: UUID, vedtaksperiodeId: UUID) {}
    fun varselDeaktivert(varselId: UUID, varselkode: String, generasjonId: UUID, vedtaksperiodeId: UUID) {}
    fun varselFlyttet(varselId: UUID, gammelGenerasjonId: UUID, nyGenerasjonId: UUID) {}
    fun varselGodkjent(varselId: UUID, vedtaksperiodeId: UUID, generasjonId: UUID, varselkode: String, ident: String) {}
    fun varselAvvist(varselId: UUID, vedtaksperiodeId: UUID, generasjonId: UUID, varselkode: String, ident: String) {}
}