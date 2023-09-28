package no.nav.helse

import com.expediagroup.graphql.client.types.GraphQLClientResponse
import java.util.UUID
import no.nav.helse.spleis.graphql.HentSnapshot
import no.nav.helse.spleis.graphql.enums.GraphQLInntektstype
import no.nav.helse.spleis.graphql.enums.GraphQLPeriodetilstand
import no.nav.helse.spleis.graphql.enums.GraphQLPeriodetype
import no.nav.helse.spleis.graphql.enums.GraphQLUtbetalingstatus
import no.nav.helse.spleis.graphql.enums.Utbetalingtype
import no.nav.helse.spleis.graphql.hentsnapshot.Alder
import no.nav.helse.spleis.graphql.hentsnapshot.GraphQLArbeidsgiver
import no.nav.helse.spleis.graphql.hentsnapshot.GraphQLBeregnetPeriode
import no.nav.helse.spleis.graphql.hentsnapshot.GraphQLGenerasjon
import no.nav.helse.spleis.graphql.hentsnapshot.GraphQLPeriodevilkar
import no.nav.helse.spleis.graphql.hentsnapshot.GraphQLPerson
import no.nav.helse.spleis.graphql.hentsnapshot.GraphQLUtbetaling
import no.nav.helse.spleis.graphql.hentsnapshot.Sykepengedager

object Testdata {
    const val FØDSELSNUMMER = "12020052345"

    const val AKTØR = "999999999"
    const val ORGNR = "222222222"
    const val ORGNR_GHOST = "666666666"
    const val ENHET_OSLO = "0301"

    const val SAKSBEHANDLER_EPOST = "sara.saksbehandler@nav.no"
    val SAKSBEHANDLER_OID: UUID = UUID.randomUUID()
    const val SAKSBEHANDLER_IDENT = "X999999"
    const val SAKSBEHANDLER_NAVN = "Sara Saksbehandler"
    val SAKSBEHANDLERTILGANGER_UTEN_TILGANGER =
        no.nav.helse.spesialist.api.SaksbehandlerTilganger(
            gruppetilganger = emptyList(),
            kode7Saksbehandlergruppe = UUID.randomUUID(),
            riskSaksbehandlergruppe = UUID.randomUUID(),
            beslutterSaksbehandlergruppe = UUID.randomUUID(),
            skjermedePersonerSaksbehandlergruppe = UUID.randomUUID(),
            stikkprøveSaksbehandlergruppe = UUID.randomUUID(),
            spesialsakSaksbehandlergruppe = UUID.randomUUID()
        )
    internal val UTBETALING_ID = UUID.randomUUID()

    const val VARSEL_KODE_1 = "VARSEL_KODE_1"
    const val VARSEL_KODE_2 = "VARSEL_KODE_2"

    val VEDTAKSPERIODE_ID: UUID = UUID.randomUUID()
    val SNAPSHOT = snapshot(
        aktørId = AKTØR,
        organisasjonsnummer = ORGNR,
        fødselsnummer = FØDSELSNUMMER,
        vedtaksperiodeId = VEDTAKSPERIODE_ID,
        utbetalingId = UTBETALING_ID,
    )

    fun snapshot(
        versjon: Int = 1,
        aktørId: String = AKTØR,
        organisasjonsnummer: String = ORGNR,
        fødselsnummer: String = FØDSELSNUMMER,
        vedtaksperiodeId: UUID = VEDTAKSPERIODE_ID,
        utbetalingId: UUID = UTBETALING_ID,
        arbeidsgiverbeløp: Int = 30000,
        personbeløp: Int = 0,
        utbetaling: GraphQLUtbetaling = GraphQLUtbetaling(
            id = utbetalingId.toString(),
            arbeidsgiverFagsystemId = "EN_FAGSYSTEMID",
            arbeidsgiverNettoBelop = arbeidsgiverbeløp,
            personFagsystemId = "EN_FAGSYSTEMID",
            personNettoBelop = personbeløp,
            statusEnum = GraphQLUtbetalingstatus.UBETALT,
            typeEnum = Utbetalingtype.UTBETALING,
            vurdering = null,
            personoppdrag = null,
            arbeidsgiveroppdrag = null
        ),
    ): GraphQLClientResponse<HentSnapshot.Result> =
        object : GraphQLClientResponse<HentSnapshot.Result> {
            override val data = HentSnapshot.Result(
                GraphQLPerson(
                    aktorId = aktørId,
                    fodselsnummer = fødselsnummer,
                    versjon = versjon,
                    arbeidsgivere = listOf(
                        GraphQLArbeidsgiver(
                            organisasjonsnummer = organisasjonsnummer,
                            ghostPerioder = emptyList(),
                            generasjoner = listOf(
                                GraphQLGenerasjon(
                                    id = UUID.randomUUID().toString(),
                                    perioder = listOf(
                                        GraphQLBeregnetPeriode(
                                            id = UUID.randomUUID().toString(),
                                            vedtaksperiodeId = vedtaksperiodeId.toString(),
                                            utbetaling = utbetaling,
                                            erForkastet = false,
                                            fom = "2020-01-01",
                                            tom = "2020-01-31",
                                            inntektstype = GraphQLInntektstype.ENARBEIDSGIVER,
                                            opprettet = "2020-01-31",
                                            periodetype = GraphQLPeriodetype.FORSTEGANGSBEHANDLING,
                                            tidslinje = emptyList(),
                                            beregningId = UUID.randomUUID().toString(),
                                            forbrukteSykedager = null,
                                            gjenstaendeSykedager = null,
                                            hendelser = emptyList(),
                                            maksdato = "2021-01-01",
                                            periodevilkar = GraphQLPeriodevilkar(
                                                alder = Alder(
                                                    alderSisteSykedag = 30,
                                                    oppfylt = true,
                                                ),
                                                sykepengedager = Sykepengedager(
                                                    forbrukteSykedager = null,
                                                    gjenstaendeSykedager = null,
                                                    maksdato = "2021-01-01",
                                                    skjaeringstidspunkt = "2020-01-01",
                                                    oppfylt = true,
                                                )
                                            ),
                                            skjaeringstidspunkt = "2020-01-01",
                                            vilkarsgrunnlagId = null,
                                            periodetilstand = GraphQLPeriodetilstand.TILGODKJENNING
                                        )
                                    )
                                )
                            ),
                        )
                    ),
                    dodsdato = null,
                    vilkarsgrunnlag = emptyList(),
                )
            )
        }

}
