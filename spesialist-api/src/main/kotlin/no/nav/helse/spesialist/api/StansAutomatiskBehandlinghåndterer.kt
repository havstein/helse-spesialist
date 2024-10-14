package no.nav.helse.spesialist.api

import kotliquery.TransactionalSession
import no.nav.helse.spesialist.api.graphql.schema.UnntattFraAutomatiskGodkjenning
import java.util.UUID

interface StansAutomatiskBehandlinghåndterer {
    fun unntattFraAutomatiskGodkjenning(fødselsnummer: String): UnntattFraAutomatiskGodkjenning

    fun sjekkOmAutomatiseringErStanset(
        fødselsnummer: String,
        vedtaksperiodeId: UUID,
        organisasjonsnummer: String,
    ): Boolean

    fun nyStansAutomatiskBehandlinghåndterer(transactionalSession: TransactionalSession): StansAutomatiskBehandlinghåndterer
}
