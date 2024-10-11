package no.nav.helse.spesialist.api.graphql

import com.expediagroup.graphql.server.ktor.GraphQL
import com.expediagroup.graphql.server.ktor.graphQLPostRoute
import com.expediagroup.graphql.server.ktor.graphiQLRoute
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.routing
import no.nav.helse.mediator.IBehandlingsstatistikkService
import no.nav.helse.spesialist.api.Avviksvurderinghenter
import no.nav.helse.spesialist.api.Dokumenthåndterer
import no.nav.helse.spesialist.api.Godkjenninghåndterer
import no.nav.helse.spesialist.api.GraphQLMetrikker
import no.nav.helse.spesialist.api.Personhåndterer
import no.nav.helse.spesialist.api.Saksbehandlerhåndterer
import no.nav.helse.spesialist.api.StansAutomatiskBehandlinghåndterer
import no.nav.helse.spesialist.api.Totrinnsvurderinghåndterer
import no.nav.helse.spesialist.api.arbeidsgiver.ArbeidsgiverApiDao
import no.nav.helse.spesialist.api.egenAnsatt.EgenAnsattApiDao
import no.nav.helse.spesialist.api.notat.NotatApiDao
import no.nav.helse.spesialist.api.oppgave.OppgaveApiDao
import no.nav.helse.spesialist.api.oppgave.Oppgavehåndterer
import no.nav.helse.spesialist.api.overstyring.OverstyringApiDao
import no.nav.helse.spesialist.api.periodehistorikk.PeriodehistorikkApiDao
import no.nav.helse.spesialist.api.person.PersonApiDao
import no.nav.helse.spesialist.api.påvent.PåVentApiDao
import no.nav.helse.spesialist.api.reservasjon.ReservasjonClient
import no.nav.helse.spesialist.api.risikovurdering.RisikovurderingApiDao
import no.nav.helse.spesialist.api.snapshot.SnapshotService
import no.nav.helse.spesialist.api.tildeling.TildelingApiDao
import no.nav.helse.spesialist.api.totrinnsvurdering.TotrinnsvurderingApiDao
import no.nav.helse.spesialist.api.varsel.ApiVarselRepository
import no.nav.helse.spesialist.api.vergemål.VergemålApiDao
import java.util.UUID

fun Application.graphQLApi(
    personApiDao: PersonApiDao,
    egenAnsattApiDao: EgenAnsattApiDao,
    tildelingApiDao: TildelingApiDao,
    arbeidsgiverApiDao: ArbeidsgiverApiDao,
    overstyringApiDao: OverstyringApiDao,
    risikovurderingApiDao: RisikovurderingApiDao,
    varselRepository: ApiVarselRepository,
    oppgaveApiDao: OppgaveApiDao,
    periodehistorikkDao: PeriodehistorikkApiDao,
    notatDao: NotatApiDao,
    totrinnsvurderingApiDao: TotrinnsvurderingApiDao,
    påVentApiDao: PåVentApiDao,
    vergemålApiDao: VergemålApiDao,
    reservasjonClient: ReservasjonClient,
    avviksvurderinghenter: Avviksvurderinghenter,
    skjermedePersonerGruppeId: UUID,
    kode7Saksbehandlergruppe: UUID,
    beslutterGruppeId: UUID,
    snapshotService: SnapshotService,
    behandlingsstatistikkMediator: IBehandlingsstatistikkService,
    saksbehandlerhåndtererProvider: () -> Saksbehandlerhåndterer,
    oppgavehåndtererProvider: () -> Oppgavehåndterer,
    totrinnsvurderinghåndterer: Totrinnsvurderinghåndterer,
    godkjenninghåndtererProvider: () -> Godkjenninghåndterer,
    personhåndtererProvider: () -> Personhåndterer,
    dokumenthåndtererProvider: () -> Dokumenthåndterer,
    stansAutomatiskBehandlinghåndterer: StansAutomatiskBehandlinghåndterer,
) {
    val saksbehandlerhåndterer: Saksbehandlerhåndterer by lazy { saksbehandlerhåndtererProvider() }
    val oppgavehåndterer: Oppgavehåndterer by lazy { oppgavehåndtererProvider() }
    val godkjenninghåndterer: Godkjenninghåndterer by lazy { godkjenninghåndtererProvider() }
    val personhåndterer: Personhåndterer by lazy { personhåndtererProvider() }
    val dokumenthåndterer: Dokumenthåndterer by lazy { dokumenthåndtererProvider() }
    val schemaBuilder =
        SchemaBuilder(
            personApiDao = personApiDao,
            egenAnsattApiDao = egenAnsattApiDao,
            tildelingApiDao = tildelingApiDao,
            arbeidsgiverApiDao = arbeidsgiverApiDao,
            overstyringApiDao = overstyringApiDao,
            risikovurderingApiDao = risikovurderingApiDao,
            varselRepository = varselRepository,
            oppgaveApiDao = oppgaveApiDao,
            periodehistorikkDao = periodehistorikkDao,
            påVentApiDao = påVentApiDao,
            snapshotService = snapshotService,
            notatDao = notatDao,
            totrinnsvurderingApiDao = totrinnsvurderingApiDao,
            vergemålApiDao = vergemålApiDao,
            reservasjonClient = reservasjonClient,
            avviksvurderinghenter = avviksvurderinghenter,
            behandlingsstatistikkMediator = behandlingsstatistikkMediator,
            saksbehandlerhåndterer = saksbehandlerhåndterer,
            oppgavehåndterer = oppgavehåndterer,
            totrinnsvurderinghåndterer = totrinnsvurderinghåndterer,
            godkjenninghåndterer = godkjenninghåndterer,
            personhåndterer = personhåndterer,
            dokumenthåndterer = dokumenthåndterer,
            stansAutomatiskBehandlinghåndterer = stansAutomatiskBehandlinghåndterer,
        )

    wiring(kode7Saksbehandlergruppe, skjermedePersonerGruppeId, beslutterGruppeId, schemaBuilder)
}

internal fun Application.wiring(
    kode7Saksbehandlergruppe: UUID,
    skjermedePersonerGruppeId: UUID,
    beslutterGruppeId: UUID,
    schemaBuilder: SchemaBuilder,
) {
    install(GraphQL) {
        server {
            this.contextFactory =
                ContextFactory(
                    kode7Saksbehandlergruppe = kode7Saksbehandlergruppe,
                    skjermedePersonerSaksbehandlergruppe = skjermedePersonerGruppeId,
                    beslutterSaksbehandlergruppe = beslutterGruppeId,
                )
        }
        schema {
            packages =
                listOf(
                    "no.nav.helse.spesialist.api.graphql",
                    "no.nav.helse.spleis.graphql",
                )
            hooks = schemaGeneratorHooks
            mutations = schemaBuilder.mutations()
            queries = schemaBuilder.queries()
        }
    }

    routing {
        authenticate("oidc") {
            graphQLPostRoute()
            graphiQLRoute()
            install(GraphQLMetrikker)
        }
    }
}
