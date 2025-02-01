package no.nav.helse.spesialist.api.graphql.mutation

import com.expediagroup.graphql.server.operations.Mutation
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.helse.spesialist.api.Saksbehandlerhåndterer
import no.nav.helse.spesialist.api.graphql.ContextValues
import no.nav.helse.spesialist.api.graphql.schema.ApiAnnulleringData
import no.nav.helse.spesialist.api.saksbehandler.SaksbehandlerFraApi

class AnnulleringMutation(
    private val saksbehandlerhåndterer: Saksbehandlerhåndterer,
) : Mutation {
    @Suppress("unused")
    suspend fun annuller(
        annullering: ApiAnnulleringData,
        env: DataFetchingEnvironment,
    ): DataFetcherResult<Boolean> =
        withContext(Dispatchers.IO) {
            val saksbehandler: SaksbehandlerFraApi = env.graphQlContext.get(ContextValues.SAKSBEHANDLER)

            saksbehandlerhåndterer.håndter(annullering, saksbehandler)
            DataFetcherResult.newResult<Boolean>().data(true).build()
        }
}
