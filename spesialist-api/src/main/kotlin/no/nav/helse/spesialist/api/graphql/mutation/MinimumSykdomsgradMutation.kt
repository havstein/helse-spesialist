package no.nav.helse.spesialist.api.graphql.mutation

import com.expediagroup.graphql.server.operations.Mutation
import graphql.GraphQLError
import graphql.GraphqlErrorException
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.helse.spesialist.api.Saksbehandlerhåndterer
import no.nav.helse.spesialist.api.graphql.ContextValues.SAKSBEHANDLER
import no.nav.helse.spesialist.api.graphql.schema.MinimumSykdomsgrad
import no.nav.helse.spesialist.api.saksbehandler.SaksbehandlerFraApi
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class MinimumSykdomsgradMutation(private val saksbehandlerhåndterer: Saksbehandlerhåndterer) : Mutation {
    private companion object {
        private val logg: Logger = LoggerFactory.getLogger(MinimumSykdomsgradMutation::class.java)
    }

    @Suppress("unused")
    suspend fun minimumSykdomsgrad(
        minimumSykdomsgrad: MinimumSykdomsgrad,
        env: DataFetchingEnvironment,
    ): DataFetcherResult<Boolean> =
        withContext(Dispatchers.IO) {
            val saksbehandler: SaksbehandlerFraApi = env.graphQlContext.get(SAKSBEHANDLER)
            if (minimumSykdomsgrad.perioderVurdertOk.isEmpty() && minimumSykdomsgrad.perioderVurdertIkkeOk.isEmpty()) {
                return@withContext DataFetcherResult.newResult<Boolean>()
                    .error(
                        GraphqlErrorException.newErrorException().message("Mangler vurderte perioder")
                            .extensions(mapOf("code" to 400)).build(),
                    )
                    .data(false)
                    .build()
            }

            try {
                withContext(Dispatchers.IO) {
                    saksbehandlerhåndterer.håndter(minimumSykdomsgrad, saksbehandler)
                }
            } catch (e: Exception) {
                val kunneIkkeVurdereMinimumSykdomsgradError = kunneIkkeVurdereMinimumSykdomsgradError()
                logg.error(kunneIkkeVurdereMinimumSykdomsgradError.message, e)
                return@withContext DataFetcherResult.newResult<Boolean>()
                    .error(kunneIkkeVurdereMinimumSykdomsgradError)
                    .data(false)
                    .build()
            }
            DataFetcherResult.newResult<Boolean>().data(true).build()
        }

    private fun kunneIkkeVurdereMinimumSykdomsgradError(): GraphQLError =
        GraphqlErrorException.newErrorException().message("Kunne ikke vurdere minimum sykdomsgrad")
            .extensions(mapOf("code" to 500)).build()
}
