package no.nav.helse.spesialist.api.graphql.query

import com.expediagroup.graphql.server.operations.Query
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import no.nav.helse.spesialist.api.Saksbehandlerhåndterer
import no.nav.helse.spesialist.api.graphql.ContextValues.SAKSBEHANDLER
import no.nav.helse.spesialist.api.graphql.schema.ApiOpptegnelse
import no.nav.helse.spesialist.api.saksbehandler.SaksbehandlerFraApi

class OpptegnelseQuery(
    private val saksbehandlerhåndterer: Saksbehandlerhåndterer,
) : Query {
    @Suppress("unused")
    fun opptegnelser(
        sekvensId: Int? = null,
        environment: DataFetchingEnvironment,
    ): DataFetcherResult<List<ApiOpptegnelse>> {
        val saksbehandler = environment.graphQlContext.get<SaksbehandlerFraApi>(SAKSBEHANDLER)
        val opptegnelser =
            if (sekvensId != null) {
                saksbehandlerhåndterer.hentAbonnerteOpptegnelser(saksbehandler, sekvensId)
            } else {
                saksbehandlerhåndterer.hentAbonnerteOpptegnelser(saksbehandler)
            }

        return DataFetcherResult.newResult<List<ApiOpptegnelse>>().data(opptegnelser).build()
    }
}
