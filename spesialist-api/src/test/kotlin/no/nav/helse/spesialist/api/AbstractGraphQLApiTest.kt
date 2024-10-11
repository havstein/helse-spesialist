package no.nav.helse.spesialist.api

import com.fasterxml.jackson.databind.JsonNode
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.accept
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.server.application.install
import io.ktor.server.plugins.doublereceive.DoubleReceive
import io.mockk.every
import io.mockk.mockk
import no.nav.helse.mediator.IBehandlingsstatistikkService
import no.nav.helse.spesialist.api.avviksvurdering.Avviksvurdering
import no.nav.helse.spesialist.api.avviksvurdering.Beregningsgrunnlag
import no.nav.helse.spesialist.api.avviksvurdering.InnrapportertInntekt
import no.nav.helse.spesialist.api.avviksvurdering.Inntekt
import no.nav.helse.spesialist.api.avviksvurdering.OmregnetÅrsinntekt
import no.nav.helse.spesialist.api.avviksvurdering.Sammenligningsgrunnlag
import no.nav.helse.spesialist.api.endepunkter.ApiTesting
import no.nav.helse.spesialist.api.graphql.SchemaBuilder
import no.nav.helse.spesialist.api.graphql.wiring
import no.nav.helse.spesialist.api.reservasjon.ReservasjonClient
import no.nav.helse.spleis.graphql.hentsnapshot.GraphQLArbeidsgiver
import org.intellij.lang.annotations.Language
import java.time.YearMonth
import java.util.UUID

internal abstract class AbstractGraphQLApiTest : DatabaseIntegrationTest() {
    protected val kode7Saksbehandlergruppe: UUID = UUID.randomUUID()
    protected val skjermedePersonerGruppeId: UUID = UUID.randomUUID()
    private val beslutterGruppeId: UUID = UUID.randomUUID()
    private val avviksvurderingId: UUID = UUID.randomUUID()

    private val reservasjonClient = mockk<ReservasjonClient>(relaxed = true)
    private val behandlingsstatistikkMediator = mockk<IBehandlingsstatistikkService>(relaxed = true)
    protected val saksbehandlerhåndterer = mockk<Saksbehandlerhåndterer>(relaxed = true)
    private val godkjenninghåndterer = mockk<Godkjenninghåndterer>(relaxed = true)
    private val personhåndterer = mockk<Personhåndterer>(relaxed = true)
    protected val dokumenthåndterer = mockk<Dokumenthåndterer>(relaxed = true)
    private val avviksvurderinghenter = mockk<Avviksvurderinghenter>(relaxed = true)
    private val stansAutomatiskBehandlinghåndterer = mockk<StansAutomatiskBehandlinghåndterer>(relaxed = true)

    protected open val useGraphQLServerWithSeparateMocks: Boolean = false

    private val schemaBuilder =
        SchemaBuilder(
            personApiDao = personApiDao,
            egenAnsattApiDao = egenAnsattApiDao,
            tildelingApiDao = tildelingApiDao,
            arbeidsgiverApiDao = arbeidsgiverApiDao,
            overstyringApiDao = overstyringApiDao,
            risikovurderingApiDao = risikovurderingApiDao,
            varselRepository = apiVarselRepository,
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

    private val apiTesting = ApiTesting(jwtStub) {
            install(DoubleReceive)
            wiring(kode7Saksbehandlergruppe, skjermedePersonerGruppeId, beslutterGruppeId, schemaBuilder)
        }

    override fun mockSnapshot(
        fødselsnummer: String,
        avviksprosent: Double,
        arbeidsgivere: List<GraphQLArbeidsgiver>,
    ) {
        super.mockSnapshot(fødselsnummer, avviksprosent, arbeidsgivere)
        every { avviksvurderinghenter.hentAvviksvurdering(any()) } returns
            Avviksvurdering(
                unikId = avviksvurderingId,
                vilkårsgrunnlagId = UUID.randomUUID(),
                fødselsnummer = fødselsnummer,
                skjæringstidspunkt = 1.januar,
                opprettet = 1.januar.atStartOfDay(),
                avviksprosent = avviksprosent,
                sammenligningsgrunnlag =
                    Sammenligningsgrunnlag(
                        totalbeløp = 10000.0,
                        innrapporterteInntekter =
                            listOf(
                                InnrapportertInntekt(
                                    arbeidsgiverreferanse = ORGANISASJONSNUMMER,
                                    inntekter =
                                        listOf(
                                            Inntekt(
                                                årMåned = YearMonth.from(1.januar),
                                                beløp = 2000.0,
                                            ),
                                            Inntekt(
                                                årMåned = YearMonth.from(1.februar),
                                                beløp = 2000.0,
                                            ),
                                        ),
                                ),
                                InnrapportertInntekt(
                                    arbeidsgiverreferanse = "987656789",
                                    inntekter =
                                        listOf(
                                            Inntekt(
                                                årMåned = YearMonth.from(1.januar),
                                                beløp = 1500.0,
                                            ),
                                            Inntekt(
                                                årMåned = YearMonth.from(1.februar),
                                                beløp = 1500.0,
                                            ),
                                            Inntekt(
                                                årMåned = YearMonth.from(1.mars),
                                                beløp = 1500.0,
                                            ),
                                            Inntekt(
                                                årMåned = YearMonth.from(1.april),
                                                beløp = 1500.0,
                                            ),
                                        ),
                                ),
                            ),
                    ),
                beregningsgrunnlag =
                    Beregningsgrunnlag(
                        totalbeløp = 10000.0,
                        omregnedeÅrsinntekter =
                            listOf(
                                OmregnetÅrsinntekt(arbeidsgiverreferanse = ORGANISASJONSNUMMER, beløp = 10000.0),
                            ),
                    ),
            )
    }

    companion object {
        internal val jwtStub = JwtStub()
        private val requiredGroup: UUID = UUID.randomUUID()
        private const val clientId = "client_id"
        private const val issuer = "https://jwt-provider-domain"

        fun HttpRequestBuilder.authentication(
            navn: String,
            epost: String,
            ident: String,
            oid: String,
            group: String? = null,
        ) {
            header(
                "Authorization",
                "Bearer ${
                    jwtStub.getToken(
                        groups = listOfNotNull(requiredGroup.toString(), group),
                        oid = oid,
                        epostadresse = epost,
                        clientId = clientId,
                        issuer = issuer,
                        navn = navn,
                        navIdent = ident,
                    )
                }",
            )
        }
    }

    protected fun runQuery(
        @Language("GraphQL") query: String,
        group: UUID? = null,
    ): JsonNode =
        apiTesting.spesialistApi { client ->
            client.post("/graphql") {
                contentType(ContentType.Application.Json)
                accept(ContentType.Application.Json)
                authentication(
                    navn = SAKSBEHANDLER.navn,
                    epost = SAKSBEHANDLER.epost,
                    ident = SAKSBEHANDLER.ident,
                    oid = SAKSBEHANDLER.oid.toString(),
                    group = group?.toString(),
                )
                setBody(mapOf("query" to query))
            }.body<String>()
        }.let(objectMapper::readTree)
}
