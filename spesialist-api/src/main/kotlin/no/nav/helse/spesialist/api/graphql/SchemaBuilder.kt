package no.nav.helse.spesialist.api.graphql

import com.expediagroup.graphql.generator.SchemaGeneratorConfig
import com.expediagroup.graphql.generator.TopLevelObject
import com.expediagroup.graphql.generator.toSchema
import com.expediagroup.graphql.server.operations.Mutation
import com.expediagroup.graphql.server.operations.Query
import graphql.schema.GraphQLSchema
import no.nav.helse.mediator.IBehandlingsstatistikkService
import no.nav.helse.spesialist.api.Avviksvurderinghenter
import no.nav.helse.spesialist.api.Dokumenthåndterer
import no.nav.helse.spesialist.api.Godkjenninghåndterer
import no.nav.helse.spesialist.api.Personhåndterer
import no.nav.helse.spesialist.api.Saksbehandlerhåndterer
import no.nav.helse.spesialist.api.StansAutomatiskBehandlinghåndterer
import no.nav.helse.spesialist.api.Totrinnsvurderinghåndterer
import no.nav.helse.spesialist.api.arbeidsgiver.ArbeidsgiverApiDao
import no.nav.helse.spesialist.api.egenAnsatt.EgenAnsattApiDao
import no.nav.helse.spesialist.api.graphql.mutation.AnnulleringMutation
import no.nav.helse.spesialist.api.graphql.mutation.MinimumSykdomsgradMutation
import no.nav.helse.spesialist.api.graphql.mutation.NotatMutation
import no.nav.helse.spesialist.api.graphql.mutation.OpphevStansMutation
import no.nav.helse.spesialist.api.graphql.mutation.OpptegnelseMutation
import no.nav.helse.spesialist.api.graphql.mutation.OverstyringMutation
import no.nav.helse.spesialist.api.graphql.mutation.PaVentMutation
import no.nav.helse.spesialist.api.graphql.mutation.PersonMutation
import no.nav.helse.spesialist.api.graphql.mutation.SkjonnsfastsettelseMutation
import no.nav.helse.spesialist.api.graphql.mutation.TildelingMutation
import no.nav.helse.spesialist.api.graphql.mutation.TotrinnsvurderingMutation
import no.nav.helse.spesialist.api.graphql.mutation.VarselMutation
import no.nav.helse.spesialist.api.graphql.mutation.VedtakMutation
import no.nav.helse.spesialist.api.graphql.query.BehandlingsstatistikkQuery
import no.nav.helse.spesialist.api.graphql.query.DokumentQuery
import no.nav.helse.spesialist.api.graphql.query.NotatQuery
import no.nav.helse.spesialist.api.graphql.query.OppgaverQuery
import no.nav.helse.spesialist.api.graphql.query.OpptegnelseQuery
import no.nav.helse.spesialist.api.graphql.query.PersonQuery
import no.nav.helse.spesialist.api.notat.NotatApiDao
import no.nav.helse.spesialist.api.oppgave.OppgaveApiDao
import no.nav.helse.spesialist.api.oppgave.Oppgavehåndterer
import no.nav.helse.spesialist.api.overstyring.OverstyringApiDao
import no.nav.helse.spesialist.api.periodehistorikk.PeriodehistorikkApiDao
import no.nav.helse.spesialist.api.person.PersonApiDao
import no.nav.helse.spesialist.api.person.PersonService
import no.nav.helse.spesialist.api.påvent.PåVentApiDao
import no.nav.helse.spesialist.api.reservasjon.ReservasjonClient
import no.nav.helse.spesialist.api.risikovurdering.RisikovurderingApiDao
import no.nav.helse.spesialist.api.snapshot.SnapshotService
import no.nav.helse.spesialist.api.tildeling.TildelingApiDao
import no.nav.helse.spesialist.api.totrinnsvurdering.TotrinnsvurderingApiDao
import no.nav.helse.spesialist.api.varsel.ApiVarselRepository
import no.nav.helse.spesialist.api.vergemål.VergemålApiDao

internal class SchemaBuilder(
    private val personApiDao: PersonApiDao,
    private val egenAnsattApiDao: EgenAnsattApiDao,
    private val tildelingApiDao: TildelingApiDao,
    private val arbeidsgiverApiDao: ArbeidsgiverApiDao,
    private val overstyringApiDao: OverstyringApiDao,
    private val risikovurderingApiDao: RisikovurderingApiDao,
    private val varselRepository: ApiVarselRepository,
    private val oppgaveApiDao: OppgaveApiDao,
    private val periodehistorikkDao: PeriodehistorikkApiDao,
    private val påVentApiDao: PåVentApiDao,
    private val vergemålApiDao: VergemålApiDao,
    private val snapshotService: SnapshotService,
    private val notatDao: NotatApiDao,
    private val totrinnsvurderingApiDao: TotrinnsvurderingApiDao,
    private val reservasjonClient: ReservasjonClient,
    private val avviksvurderinghenter: Avviksvurderinghenter,
    private val behandlingsstatistikkMediator: IBehandlingsstatistikkService,
    private val saksbehandlerhåndterer: Saksbehandlerhåndterer,
    private val oppgavehåndterer: Oppgavehåndterer,
    private val totrinnsvurderinghåndterer: Totrinnsvurderinghåndterer,
    private val godkjenninghåndterer: Godkjenninghåndterer,
    private val personhåndterer: Personhåndterer,
    private val dokumenthåndterer: Dokumenthåndterer,
    private val stansAutomatiskBehandlinghåndterer: StansAutomatiskBehandlinghåndterer,
) {
    fun build(): GraphQLSchema {
        val schemaConfig =
            SchemaGeneratorConfig(
                supportedPackages =
                    listOf(
                        "no.nav.helse.spesialist.api.graphql",
                        "no.nav.helse.spleis.graphql",
                    ),
                hooks = schemaGeneratorHooks,
            )
        return toSchema(
            config = schemaConfig,
            queries = queries().map { TopLevelObject(it) },
            mutations = mutations().map { TopLevelObject(it) },
        )
    }

    fun queries(): List<Query> =
        listOf(
            PersonQuery(
                personoppslagService = PersonService(personApiDao = personApiDao, egenAnsattApiDao = egenAnsattApiDao, tildelingApiDao = tildelingApiDao, arbeidsgiverApiDao = arbeidsgiverApiDao, overstyringApiDao = overstyringApiDao, risikovurderingApiDao = risikovurderingApiDao, varselRepository = varselRepository, oppgaveApiDao = oppgaveApiDao, periodehistorikkDao = periodehistorikkDao, notatDao = notatDao, totrinnsvurderingApiDao = totrinnsvurderingApiDao, påVentApiDao = påVentApiDao, vergemålApiDao = vergemålApiDao, snapshotService = snapshotService, reservasjonClient = reservasjonClient, oppgavehåndterer = oppgavehåndterer, saksbehandlerhåndterer = saksbehandlerhåndterer, avviksvurderinghenter = avviksvurderinghenter, personhåndterer = personhåndterer, stansAutomatiskBehandlinghåndterer = stansAutomatiskBehandlinghåndterer),
            ),
            OppgaverQuery(oppgavehåndterer = oppgavehåndterer),
            BehandlingsstatistikkQuery(behandlingsstatistikkMediator = behandlingsstatistikkMediator),
            NotatQuery(notatDao = notatDao),
            OpptegnelseQuery(saksbehandlerhåndterer = saksbehandlerhåndterer),
            DokumentQuery(personApiDao = personApiDao, egenAnsattApiDao = egenAnsattApiDao, dokumenthåndterer = dokumenthåndterer),
        )

    fun mutations(): List<Mutation> =
        listOf(
            NotatMutation(notatDao = notatDao),
            VarselMutation(varselRepository = varselRepository),
            TildelingMutation(saksbehandlerhåndterer = saksbehandlerhåndterer),
            OpptegnelseMutation(saksbehandlerhåndterer = saksbehandlerhåndterer),
            OverstyringMutation(saksbehandlerhåndterer = saksbehandlerhåndterer),
            SkjonnsfastsettelseMutation(saksbehandlerhåndterer = saksbehandlerhåndterer),
            MinimumSykdomsgradMutation(saksbehandlerhåndterer = saksbehandlerhåndterer),
            TotrinnsvurderingMutation(
                saksbehandlerhåndterer = saksbehandlerhåndterer,
                oppgavehåndterer = oppgavehåndterer,
                totrinnsvurderinghåndterer = totrinnsvurderinghåndterer,
            ),
            VedtakMutation(
                oppgavehåndterer = oppgavehåndterer,
                totrinnsvurderinghåndterer = totrinnsvurderinghåndterer,
                saksbehandlerhåndterer = saksbehandlerhåndterer,
                godkjenninghåndterer = godkjenninghåndterer,
            ),
            PersonMutation(personhåndterer = personhåndterer),
            AnnulleringMutation(saksbehandlerhåndterer = saksbehandlerhåndterer),
            PaVentMutation(saksbehandlerhåndterer = saksbehandlerhåndterer),
            OpphevStansMutation(saksbehandlerhåndterer = saksbehandlerhåndterer),
        )
}
