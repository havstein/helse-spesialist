package no.nav.helse.spesialist.api.graphql

import com.expediagroup.graphql.server.execution.GraphQLServer
import graphql.GraphQL
import io.ktor.http.ContentType
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.auth.authenticate
import io.ktor.server.request.ApplicationRequest
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import java.time.Duration
import no.nav.helse.Tilgangsgrupper
import no.nav.helse.spesialist.api.GraphQLMetrikker
import no.nav.helse.spesialist.api.arbeidsgiver.ArbeidsgiverApiDao
import no.nav.helse.spesialist.api.behandlingsstatistikk.BehandlingsstatistikkMediator
import no.nav.helse.spesialist.api.egenAnsatt.EgenAnsattApiDao
import no.nav.helse.spesialist.api.notat.NotatDao
import no.nav.helse.spesialist.api.objectMapper
import no.nav.helse.spesialist.api.oppgave.OppgaveApiDao
import no.nav.helse.spesialist.api.overstyring.OverstyringApiDao
import no.nav.helse.spesialist.api.periodehistorikk.PeriodehistorikkDao
import no.nav.helse.spesialist.api.person.PersonApiDao
import no.nav.helse.spesialist.api.reservasjon.ReservasjonClient
import no.nav.helse.spesialist.api.risikovurdering.RisikovurderingApiDao
import no.nav.helse.spesialist.api.snapshot.SnapshotMediator
import no.nav.helse.spesialist.api.tildeling.TildelingDao
import no.nav.helse.spesialist.api.totrinnsvurdering.TotrinnsvurderingApiDao
import no.nav.helse.spesialist.api.utbetaling.UtbetalingApiDao
import no.nav.helse.spesialist.api.varsel.ApiVarselRepository
import no.nav.helse.spesialist.api.vedtaksperiode.VarselDao
import org.slf4j.Logger
import org.slf4j.LoggerFactory

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
    totrinnsvurderingApiDao: TotrinnsvurderingApiDao,
    reservasjonClient: ReservasjonClient,
    tilgangsgrupper: Tilgangsgrupper,
    snapshotMediator: SnapshotMediator,
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
        totrinnsvurderingApiDao = totrinnsvurderingApiDao,
        reservasjonClient = reservasjonClient,
        snapshotMediator = snapshotMediator,
        behandlingsstatistikkMediator = behandlingsstatistikkMediator,
    ).build()

    val server = GraphQLServer(
        requestParser = RequestParser(),
        contextFactory = ContextFactory(tilgangsgrupper),
        requestHandler = LoggingGraphQLRequestHandler(
            GraphQL.newGraphQL(schema).build()
        )
    )

    routing {
        route("graphql") {
            authenticate("oidc") {
                install(GraphQLMetrikker)
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
        sikkerLogg.trace("Starter behandling av graphql-kall")
        val start = System.nanoTime()
        val result = server.execute(call.request)
        val tidslogging = "Kall behandlet etter ${tidBrukt(start).toMillis()} ms"

        if (result != null) {
            sikkerLogg.trace("$tidslogging, starter mapping")
            val json = objectMapper.writeValueAsString(result)
            sikkerLogg.trace("Respons mappet etter ${tidBrukt(start).toMillis()} ms")
            call.respond(json)
        } else
            sikkerLogg.trace("$tidslogging, men noe gikk galt")
    }
}

private val sikkerLogg: Logger = LoggerFactory.getLogger("tjenestekall")

private fun tidBrukt(start: Long): Duration = Duration.ofNanos(System.nanoTime() - start)

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
