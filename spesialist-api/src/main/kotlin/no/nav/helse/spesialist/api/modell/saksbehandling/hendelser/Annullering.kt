package no.nav.helse.spesialist.api.modell.saksbehandling.hendelser

import java.util.UUID
import no.nav.helse.spesialist.api.modell.AnnullertUtbetalingEvent
import no.nav.helse.spesialist.api.modell.Saksbehandler

class Annullering(
    private val aktørId: String,
    private val fødselsnummer: String,
    private val organisasjonsnummer: String,
    private val fagsystemId: String,
    private val begrunnelser: List<String> = emptyList(),
    private val kommentar: String?
): Handling {
    override fun utførAv(saksbehandler: Saksbehandler) {
        saksbehandler.håndter(this)
    }

    internal fun byggEvent(oid: UUID, navn: String, epost: String, ident: String): AnnullertUtbetalingEvent {
        return AnnullertUtbetalingEvent(
            fødselsnummer = fødselsnummer,
            aktørId = aktørId,
            organisasjonsnummer = organisasjonsnummer,
            saksbehandlerOid = oid,
            saksbehandlerNavn = navn,
            saksbehandlerIdent = ident,
            saksbehandlerEpost = epost,
            fagsystemId = fagsystemId,
            begrunnelser = begrunnelser,
            kommentar = kommentar
        )
    }
}