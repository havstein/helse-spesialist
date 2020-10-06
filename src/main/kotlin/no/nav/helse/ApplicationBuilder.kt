package no.nav.helse

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.auth.jwt.*
import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.client.features.json.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.jackson.*
import io.ktor.request.*
import io.ktor.routing.*
import kotlinx.coroutines.runBlocking
import no.nav.helse.api.*
import no.nav.helse.mediator.kafka.HendelseMediator
import no.nav.helse.mediator.kafka.Hendelsefabrikk
import no.nav.helse.mediator.kafka.MiljøstyrtFeatureToggle
import no.nav.helse.modell.CommandContextDao
import no.nav.helse.modell.SnapshotDao
import no.nav.helse.modell.VedtakDao
import no.nav.helse.modell.arbeidsgiver.ArbeidsgiverDao
import no.nav.helse.modell.automatisering.Automatisering
import no.nav.helse.modell.automatisering.AutomatiseringDao
import no.nav.helse.modell.command.HendelseDao
import no.nav.helse.modell.command.OppgaveDao
import no.nav.helse.modell.dkif.DigitalKontaktinformasjonDao
import no.nav.helse.modell.overstyring.OverstyringDao
import no.nav.helse.modell.person.PersonDao
import no.nav.helse.modell.risiko.RisikovurderingDao
import no.nav.helse.modell.saksbehandler.SaksbehandlerDao
import no.nav.helse.modell.vedtak.snapshot.SpeilSnapshotRestClient
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.tildeling.ReservasjonDao
import no.nav.helse.tildeling.TildelingDao
import no.nav.helse.tildeling.TildelingMediator
import no.nav.helse.vedtaksperiode.VedtaksperiodeMediator
import org.apache.http.impl.conn.SystemDefaultRoutePlanner
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import java.net.ProxySelector
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*

const val azureMountPath: String = "/var/run/secrets/nais.io/azure"
private val auditLog = LoggerFactory.getLogger("auditLogger")

internal class ApplicationBuilder(env: Map<String, String>) : RapidsConnection.StatusListener {
    private val dataSourceBuilder = DataSourceBuilder(System.getenv())
    private val dataSource = dataSourceBuilder.getDataSource(DataSourceBuilder.Role.User)

    private val azureAdClient = HttpClient(Apache) {
        engine {
            customizeClient {
                setRoutePlanner(SystemDefaultRoutePlanner(ProxySelector.getDefault()))
            }
        }
        install(JsonFeature) {
            serializer = JacksonSerializer {
                registerModule(JavaTimeModule())
            }
        }
    }
    private val spleisClient = HttpClient {
        install(JsonFeature) { serializer = JacksonSerializer() }
    }
    private val oidcDiscovery =
        runBlocking { AzureAadClient(azureAdClient).oidcDiscovery(System.getenv("AZURE_CONFIG_URL")) }
    private val accessTokenClient = AccessTokenClient(
        aadAccessTokenUrl = oidcDiscovery.token_endpoint,
        clientId = readClientId(),
        clientSecret = readClientSecret(),
        httpClient = azureAdClient
    )
    private val speilSnapshotRestClient = SpeilSnapshotRestClient(
        httpClient = spleisClient,
        accessTokenClient = accessTokenClient,
        spleisClientId = env.getValue("SPLEIS_CLIENT_ID")
    )

    private val azureConfig = AzureAdAppConfig(
        clientId = readClientId(),
        speilClientId = env.getValue("SPEIL_CLIENT_ID"),
        requiredGroup = env.getValue("AZURE_REQUIRED_GROUP")
    )
    private val httpTraceLog = LoggerFactory.getLogger("tjenestekall")
    private lateinit var hendelseMediator: HendelseMediator
    private val personDao = PersonDao(dataSource)
    private val oppgaveDao = OppgaveDao(dataSource)
    private val vedtakDao = VedtakDao(dataSource)
    private val risikovurderingDao = RisikovurderingDao(dataSource)
    private val saksbehandlerDao = SaksbehandlerDao(dataSource)
    private val commandContextDao = CommandContextDao(dataSource)
    private val tildelingDao = TildelingDao(dataSource)
    private val digitalKontaktinformasjonDao = DigitalKontaktinformasjonDao(dataSource)
    private val hendelseDao = HendelseDao(dataSource)

    private val oppgaveMediator = OppgaveMediator(oppgaveDao, vedtakDao, tildelingDao)

    private val miljøstyrtFeatureToggle = MiljøstyrtFeatureToggle(env)

    private val hendelsefabrikk = Hendelsefabrikk(
        personDao = personDao,
        arbeidsgiverDao = ArbeidsgiverDao(dataSource),
        vedtakDao = vedtakDao,
        commandContextDao = commandContextDao,
        snapshotDao = SnapshotDao(dataSource),
        oppgaveDao = oppgaveDao,
        reservasjonDao = ReservasjonDao(dataSource),
        saksbehandlerDao = saksbehandlerDao,
        overstyringDao = OverstyringDao(dataSource),
        risikovurderingDao = risikovurderingDao,
        digitalKontaktinformasjonDao = digitalKontaktinformasjonDao,
        speilSnapshotRestClient = speilSnapshotRestClient,
        oppgaveMediator = oppgaveMediator,
        miljøstyrtFeatureToggle = miljøstyrtFeatureToggle,
        automatisering = Automatisering(
            vedtakDao,
            risikovurderingDao,
            AutomatiseringDao(dataSource),
            digitalKontaktinformasjonDao
        )
    )

    private val rapidsConnection =
        RapidApplication.Builder(RapidApplication.RapidApplicationConfig.fromEnv(env)).withKtorModule {
            install(CallId) {
                generate {
                    UUID.randomUUID().toString()
                }
            }
            install(CallLogging) {
                logger = httpTraceLog
                level = Level.INFO
                callIdMdc("callId")
                filter { call -> call.request.path().startsWith("/api/") }
            }
            intercept(ApplicationCallPipeline.Call) {
                call.principal<JWTPrincipal>()?.let { principal ->
                    auditLog.info(
                        "Bruker=\"${
                            principal.payload.getClaim("NAVident")
                                .asString()
                        }\" gjør kall mot url=\"${call.request.uri}\""
                    )
                }
            }
            install(ContentNegotiation) { register(ContentType.Application.Json, JacksonConverter(objectMapper)) }
            requestResponseTracing(httpTraceLog)
            azureAdAppAuthentication(
                oidcDiscovery = oidcDiscovery,
                config = azureConfig
            )
            basicAuthentication(env.getValue("ADMIN_SECRET"))
            routing {
                authenticate("saksbehandler") {
                    oppgaveApi(oppgaveMediator)
                    vedtaksperiodeApi(
                        hendelseMediator = hendelseMediator,
                        vedtaksperiodeMediator = VedtaksperiodeMediator(dataSource)
                    )
                }
                authenticate("saksbehandler-direkte") {
                    tildelingApi(TildelingMediator(saksbehandlerDao, tildelingDao))
                    direkteOppgaveApi(oppgaveMediator)
                    annulleringApi(hendelseMediator)
                }
            }
            adminApi(hendelseMediator)
        }.build()

    init {
        rapidsConnection.register(this)
        hendelseMediator = HendelseMediator(
            rapidsConnection = rapidsConnection,
            oppgaveDao = oppgaveDao,
            personDao = personDao,
            vedtakDao = vedtakDao,
            commandContextDao = commandContextDao,
            hendelseDao = hendelseDao,
            hendelsefabrikk = hendelsefabrikk,
            oppgaveMediator = oppgaveMediator
        )
    }

    fun start() = rapidsConnection.start()
    fun stop() = rapidsConnection.stop()

    override fun onShutdown(rapidsConnection: RapidsConnection) {
        hendelseMediator.shutdown()
    }

    override fun onStartup(rapidsConnection: RapidsConnection) {
        dataSourceBuilder.migrate()
    }

    private fun readClientId(): String {
        return Files.readString(Paths.get(azureMountPath, "client_id"))
    }

    private fun readClientSecret(): String {
        return Files.readString(Paths.get(azureMountPath, "client_secret"))
    }
}
