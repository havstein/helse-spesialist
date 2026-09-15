package no.nav.helse.spesialist.application.tilgangskontroll

import no.nav.helse.spesialist.application.Either
import no.nav.helse.spesialist.domain.SaksbehandlerOid
import no.nav.helse.spesialist.domain.tilgangskontroll.Brukerroller

fun interface Brukerrollehenter {
    fun hentBrukerroller(saksbehandlerOid: SaksbehandlerOid): Either<Brukerroller, Feil>

    sealed interface Feil {
        data object SaksbehandlerFinnesIkke : Feil
    }
}
