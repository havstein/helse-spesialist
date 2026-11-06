package no.nav.helse.spesialist.domain.tilgangskontroll

import no.nav.helse.spesialist.domain.ddd.ValueObject

// Brukes både om hvilke roller en saksbehandler har, og hvilke roller som kreves for tilgang til en ressurs/handling.
data class Brukerroller(
    private val roller: Set<Brukerrolle>,
) : ValueObject {
    constructor(vararg roller: Brukerrolle) : this(roller.toSet())

    fun harRolle(rolle: Brukerrolle): Boolean = rolle in roller

    fun manglerBrukerrolle(påkrevdeRoller: Brukerroller): Boolean = påkrevdeRoller.roller.isNotEmpty() && påkrevdeRoller.roller.none { it in roller }

    // For de få stedene som faktisk trenger å transformere hele mengden av roller, for eksempel mapping til
    // API-representasjon.
    fun tilSet(): Set<Brukerrolle> = roller

    companion object {
        val INGEN = Brukerroller(emptySet())
    }
}
