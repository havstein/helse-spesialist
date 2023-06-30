package no.nav.helse.modell.sykefraværstilfelle

import java.time.LocalDate
import java.util.UUID
import no.nav.helse.modell.varsel.Varsel
import no.nav.helse.modell.vedtaksperiode.Generasjon
import no.nav.helse.modell.vedtaksperiode.Generasjon.Companion.deaktiver
import no.nav.helse.modell.vedtaksperiode.Generasjon.Companion.forhindrerAutomatisering
import no.nav.helse.modell.vedtaksperiode.Generasjon.Companion.håndterGodkjent
import no.nav.helse.modell.vedtaksperiode.Generasjon.Companion.håndterNyttVarsel
import no.nav.helse.modell.vedtaksperiode.Generasjon.Companion.kreverTotrinnsvurdering

internal class Sykefraværstilfelle(
    private val fødselsnummer: String,
    private val skjæringstidspunkt: LocalDate,
    private val gjeldendeGenerasjoner: List<Generasjon>
) {

    internal fun forhindrerAutomatisering(tilOgMed: LocalDate): Boolean {
        return gjeldendeGenerasjoner.forhindrerAutomatisering(tilOgMed)
    }

    internal fun håndter(varsel: Varsel, hendelseId: UUID) {
        gjeldendeGenerasjoner.håndterNyttVarsel(listOf(varsel), hendelseId)
    }

    internal fun deaktiver(varsel: Varsel) {
        gjeldendeGenerasjoner.deaktiver(varsel)
    }

    internal fun håndterGodkjent(saksbehandlerIdent: String, vedtaksperiodeId: UUID, hendelseId: UUID) {
        gjeldendeGenerasjoner.håndterGodkjent(saksbehandlerIdent, vedtaksperiodeId, hendelseId)
    }

    internal fun kreverTotrinnsvurdering(vedtaksperiodeId: UUID): Boolean {
        return gjeldendeGenerasjoner.kreverTotrinnsvurdering(vedtaksperiodeId)
    }
}