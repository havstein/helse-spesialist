package no.nav.helse.spesialist.api.graphql

import com.expediagroup.graphql.server.execution.GraphQLRequestHandler
import com.expediagroup.graphql.server.execution.GraphQLServer
import graphql.GraphQL
import io.ktor.http.ContentType
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.request.ApplicationRequest
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import java.util.UUID
import no.nav.helse.spesialist.api.arbeidsgiver.ArbeidsgiverApiDao
import no.nav.helse.spesialist.api.behandlingsstatistikk.BehandlingsstatistikkMediator
import no.nav.helse.spesialist.api.egenAnsatt.EgenAnsattApiDao
import no.nav.helse.spesialist.api.notat.NotatDao
import no.nav.helse.spesialist.api.objectMapper
import no.nav.helse.spesialist.api.oppgave.OppgaveApiDao
import no.nav.helse.spesialist.api.oppgave.experimental.OppgaveService
import no.nav.helse.spesialist.api.overstyring.OverstyringApiDao
import no.nav.helse.spesialist.api.periodehistorikk.PeriodehistorikkDao
import no.nav.helse.spesialist.api.person.PersonApiDao
import no.nav.helse.spesialist.api.reservasjon.ReservasjonClient
import no.nav.helse.spesialist.api.risikovurdering.RisikovurderingApiDao
import no.nav.helse.spesialist.api.snapshot.SnapshotMediator
import no.nav.helse.spesialist.api.tildeling.TildelingDao
import no.nav.helse.spesialist.api.utbetaling.UtbetalingApiDao
import no.nav.helse.spesialist.api.varsel.ApiVarselRepository
import no.nav.helse.spesialist.api.vedtaksperiode.VarselDao

fun Application.graphQLApi(
    personApiDao: PersonApiDao,
    egenAnsattApiDao: EgenAnsattApiDao,
    tildelingDao: TildelingDao,
    arbeidsgiverApiDao: ArbeidsgiverApiDao,
    overstyringApiDao: OverstyringApiDao,
    risikovurderingApiDao: RisikovurderingApiDao,
    varselDao: VarselDao,
    varselRepository: ApiVarselRepository,
    utbetalingApiDao: UtbetalingApiDao,
    oppgaveApiDao: OppgaveApiDao,
    periodehistorikkDao: PeriodehistorikkDao,
    notatDao: NotatDao,
    reservasjonClient: ReservasjonClient,
    skjermedePersonerGruppeId: UUID,
    kode7Saksbehandlergruppe: UUID,
    beslutterGruppeId: UUID,
    riskGruppeId: UUID,
    snapshotMediator: SnapshotMediator,
    oppgaveService: OppgaveService,
    behandlingsstatistikkMediator: BehandlingsstatistikkMediator,
) {
    val schema = SchemaBuilder(
        personApiDao = personApiDao,
        egenAnsattApiDao = egenAnsattApiDao,
        tildelingDao = tildelingDao,
        arbeidsgiverApiDao = arbeidsgiverApiDao,
        overstyringApiDao = overstyringApiDao,
        risikovurderingApiDao = risikovurderingApiDao,
        varselDao = varselDao,
        varselRepository = varselRepository,
        utbetalingApiDao = utbetalingApiDao,
        oppgaveApiDao = oppgaveApiDao,
        periodehistorikkDao = periodehistorikkDao,
        notatDao = notatDao,
        reservasjonClient = reservasjonClient,
        snapshotMediator = snapshotMediator,
        oppgaveService = oppgaveService,
        behandlingsstatistikkMediator = behandlingsstatistikkMediator,
    ).build()

    val server = GraphQLServer(
        requestParser = RequestParser(),
        contextFactory = ContextFactory(
            kode7Saksbehandlergruppe = kode7Saksbehandlergruppe,
            skjermedePersonerSaksbehandlergruppe = skjermedePersonerGruppeId,
            beslutterSaksbehandlergruppe = beslutterGruppeId,
            riskSaksbehandlergruppe = riskGruppeId,
        ),
        requestHandler = GraphQLRequestHandler(
            GraphQL.newGraphQL(schema).build()
        )
    )

    routing {
        route("graphql") {
            authenticate("oidc") {
                queryHandler(server)
                playground()
            }

            if (erDev()) {
                route("introspection") {
                    queryHandler(server)
                }
            }
        }
    }
}

internal fun Route.queryHandler(server: GraphQLServer<ApplicationRequest>) {
    post {
        val result = server.execute(call.request)

        if (result != null) {
            val json = objectMapper.writeValueAsString(result)
            call.respond(json)
        }
    }
}

internal fun Route.playground() {
    get("playground") {
        call.respondText(buildPlaygroundHtml("graphql", "subscriptions"), ContentType.Text.Html)
    }
}

private fun buildPlaygroundHtml(graphQLEndpoint: String, subscriptionsEndpoint: String) =
    Application::class.java.classLoader.getResource("graphql-playground.html")?.readText()
        ?.replace("\${graphQLEndpoint}", graphQLEndpoint)?.replace("\${subscriptionsEndpoint}", subscriptionsEndpoint)
        ?: throw IllegalStateException("graphql-playground.html cannot be found in the classpath")

private fun erDev() = "dev-gcp" == System.getenv("NAIS_CLUSTER_NAME")