package no.nav.helse.spesialist.api.auth

import no.nav.helse.spesialist.domain.Saksbehandler
import no.nav.helse.spesialist.domain.tilgangskontroll.Brukerroller
import no.nav.helse.spesialist.domain.tilgangskontroll.Tilgang

data class SaksbehandlerPrincipal(
    val saksbehandler: Saksbehandler,
    val brukerroller: Brukerroller,
    val tilganger: Set<Tilgang>,
    val accessToken: AccessToken,
)
