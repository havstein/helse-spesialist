package no.nav.helse.spesialist.api.graphql.schema

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.spesialist.api.Avviksvurderinghenter
import no.nav.helse.spesialist.api.SaksbehandlerTilganger
import no.nav.helse.spesialist.api.Saksbehandlerhåndterer
import no.nav.helse.spesialist.api.arbeidsgiver.ArbeidsgiverApiDao
import no.nav.helse.spesialist.api.graphql.mutation.Avslagstype
import no.nav.helse.spesialist.api.notat.NotatApiDao
import no.nav.helse.spesialist.api.objectMapper
import no.nav.helse.spesialist.api.oppgave.OppgaveApiDao
import no.nav.helse.spesialist.api.oppgave.Oppgavehåndterer
import no.nav.helse.spesialist.api.overstyring.OverstyringApiDao
import no.nav.helse.spesialist.api.overstyring.OverstyringArbeidsforholdDto
import no.nav.helse.spesialist.api.overstyring.OverstyringInntektDto
import no.nav.helse.spesialist.api.overstyring.OverstyringMinimumSykdomsgradDto
import no.nav.helse.spesialist.api.overstyring.OverstyringTidslinjeDto
import no.nav.helse.spesialist.api.overstyring.SkjønnsfastsettingSykepengegrunnlagDto
import no.nav.helse.spesialist.api.periodehistorikk.PeriodehistorikkApiDao
import no.nav.helse.spesialist.api.person.PersonApiDao
import no.nav.helse.spesialist.api.påvent.PåVentApiDao
import no.nav.helse.spesialist.api.risikovurdering.RisikovurderingApiDao
import no.nav.helse.spesialist.api.tildeling.TildelingApiDao
import no.nav.helse.spesialist.api.totrinnsvurdering.TotrinnsvurderingApiDao
import no.nav.helse.spesialist.api.varsel.ApiVarselRepository
import no.nav.helse.spleis.graphql.hentsnapshot.GraphQLGhostPeriode
import no.nav.helse.spleis.graphql.hentsnapshot.GraphQLNyttInntektsforholdPeriode
import no.nav.helse.spleis.graphql.hentsnapshot.GraphQLPerson
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class Infotrygdutbetaling(
    val fom: String,
    val tom: String,
    val grad: String,
    val dagsats: Double,
    val typetekst: String,
    val organisasjonsnummer: String,
)

data class Saksbehandler(
    val navn: String,
    val ident: String?,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Reservasjon(
    val kanVarsles: Boolean,
    val reservert: Boolean,
)

data class UnntattFraAutomatiskGodkjenning(
    val erUnntatt: Boolean,
    val arsaker: List<String>,
    val tidspunkt: LocalDateTime?,
)

data class Enhet(
    val id: String,
    val navn: String,
)

data class Tildeling(
    val navn: String,
    val epost: String,
    val oid: UUID,
)

data class PaVent(
    val frist: LocalDate?,
    val oid: UUID,
)

data class Avslag(
    val type: Avslagstype,
    val begrunnelse: String,
    val opprettet: LocalDateTime,
    val saksbehandlerIdent: String,
    val invalidert: Boolean,
)

data class Annullering(
    val saksbehandlerIdent: String,
    val arbeidsgiverFagsystemId: String?,
    val personFagsystemId: String?,
    val tidspunkt: LocalDateTime,
    val arsaker: List<String>,
    val begrunnelse: String?,
)

data class Person(
    private val snapshot: GraphQLPerson,
    private val personinfo: Personinfo,
    private val personApiDao: PersonApiDao,
    private val tildelingApiDao: TildelingApiDao,
    private val arbeidsgiverApiDao: ArbeidsgiverApiDao,
    private val overstyringApiDao: OverstyringApiDao,
    private val risikovurderingApiDao: RisikovurderingApiDao,
    private val varselRepository: ApiVarselRepository,
    private val oppgaveApiDao: OppgaveApiDao,
    private val periodehistorikkDao: PeriodehistorikkApiDao,
    private val notatDao: NotatApiDao,
    private val totrinnsvurderingApiDao: TotrinnsvurderingApiDao,
    private val påVentApiDao: PåVentApiDao,
    private val avviksvurderinghenter: Avviksvurderinghenter,
    private val tilganger: SaksbehandlerTilganger,
    private val oppgavehåndterer: Oppgavehåndterer,
    private val saksbehandlerhåndterer: Saksbehandlerhåndterer,
) {
    fun versjon(): Int = snapshot.versjon

    fun aktorId(): String = snapshot.aktorId

    fun fodselsnummer(): String = snapshot.fodselsnummer

    fun dodsdato(): LocalDate? = snapshot.dodsdato

    fun personinfo(): Personinfo = personinfo

    fun enhet(): Enhet = personApiDao.finnEnhet(snapshot.fodselsnummer).let { Enhet(it.id, it.navn) }

    fun tildeling(): Tildeling? =
        tildelingApiDao.tildelingForPerson(snapshot.fodselsnummer)?.let {
            Tildeling(
                navn = it.navn,
                epost = it.epost,
                oid = it.oid,
            )
        }

    @Suppress("unused")
    fun tilleggsinfoForInntektskilder(): List<TilleggsinfoForInntektskilde> {
        return snapshot.vilkarsgrunnlag.flatMap { vilkårsgrunnlag ->
            val avviksvurdering = avviksvurderinghenter.hentAvviksvurdering(vilkårsgrunnlag.id)
            (
                avviksvurdering?.sammenligningsgrunnlag?.innrapporterteInntekter?.map { innrapportertInntekt ->
                    innrapportertInntekt.arbeidsgiverreferanse
                } ?: emptyList()
            ) + vilkårsgrunnlag.inntekter.map { inntekt -> inntekt.arbeidsgiver }
        }.toSet().map { orgnr ->
            TilleggsinfoForInntektskilde(
                orgnummer = orgnr,
                navn = arbeidsgiverApiDao.finnNavn(orgnr) ?: "Ikke tilgjengelig",
            )
        }
    }

    fun arbeidsgivere(): List<Arbeidsgiver> {
        val overstyringer = overstyringApiDao.finnOverstyringer(snapshot.fodselsnummer)

        return snapshot.arbeidsgivere.map { arbeidsgiver ->
            Arbeidsgiver(
                organisasjonsnummer = arbeidsgiver.organisasjonsnummer,
                navn = arbeidsgiverApiDao.finnNavn(arbeidsgiver.organisasjonsnummer) ?: "Ikke tilgjengelig",
                bransjer = arbeidsgiverApiDao.finnBransjer(arbeidsgiver.organisasjonsnummer),
                ghostPerioder = arbeidsgiver.ghostPerioder.tilGhostPerioder(arbeidsgiver.organisasjonsnummer),
                nyeInntektsforholdPerioder = arbeidsgiver.nyeInntektsforholdPerioder.tilNyeInntektsforholdPerioder(),
                fødselsnummer = snapshot.fodselsnummer,
                generasjoner = arbeidsgiver.generasjoner,
                oppgavehåndterer = oppgavehåndterer,
                saksbehandlerhåndterer = saksbehandlerhåndterer,
                arbeidsgiverApiDao = arbeidsgiverApiDao,
                risikovurderingApiDao = risikovurderingApiDao,
                varselRepository = varselRepository,
                oppgaveApiDao = oppgaveApiDao,
                periodehistorikkDao = periodehistorikkDao,
                notatDao = notatDao,
                totrinnsvurderingApiDao = totrinnsvurderingApiDao,
                påVentApiDao = påVentApiDao,
                tilganger = tilganger,
                overstyringer =
                    overstyringer
                        .filter { it.relevantFor(arbeidsgiver.organisasjonsnummer) }
                        .map { overstyring ->
                            when (overstyring) {
                                is OverstyringTidslinjeDto -> overstyring.tilDagoverstyring()
                                is OverstyringArbeidsforholdDto -> overstyring.tilArbeidsforholdoverstyring()
                                is OverstyringInntektDto -> overstyring.tilInntektoverstyring()
                                is SkjønnsfastsettingSykepengegrunnlagDto -> overstyring.tilSykepengegrunnlagSkjønnsfastsetting()
                                is OverstyringMinimumSykdomsgradDto -> overstyring.tilMinimumSykdomsgradOverstyring()
                            }
                        },
            )
        }
    }

    @Suppress("unused")
    fun infotrygdutbetalinger(): List<Infotrygdutbetaling>? =
        personApiDao
            .finnInfotrygdutbetalinger(snapshot.fodselsnummer)
            ?.let { objectMapper.readValue(it) }

    fun vilkarsgrunnlag(): List<Vilkarsgrunnlag> = snapshot.vilkarsgrunnlag.map { it.tilVilkarsgrunnlag(avviksvurderinghenter) }

    private fun List<GraphQLGhostPeriode>.tilGhostPerioder(organisasjonsnummer: String): List<GhostPeriode> =
        map {
            GhostPeriode(
                fom = it.fom,
                tom = it.tom,
                skjaeringstidspunkt = it.skjaeringstidspunkt,
                vilkarsgrunnlagId = it.vilkarsgrunnlagId,
                deaktivert = it.deaktivert,
                organisasjonsnummer = organisasjonsnummer,
            )
        }

    private fun List<GraphQLNyttInntektsforholdPeriode>.tilNyeInntektsforholdPerioder(): List<NyttInntektsforholdPeriode> =
        map {
            NyttInntektsforholdPeriode(
                id = it.id,
                fom = it.fom,
                tom = it.tom,
                vilkarsgrunnlagId = it.vilkarsgrunnlagId,
                organisasjonsnummer = it.organisasjonsnummer,
                skjaeringstidspunkt = it.skjaeringstidspunkt,
            )
        }
}

private fun OverstyringTidslinjeDto.tilDagoverstyring() =
    Dagoverstyring(
        hendelseId = hendelseId,
        begrunnelse = begrunnelse,
        timestamp = timestamp,
        saksbehandler =
            Saksbehandler(
                navn = saksbehandlerNavn,
                ident = saksbehandlerIdent,
            ),
        dager =
            overstyrteDager.map { dag ->
                Dagoverstyring.OverstyrtDag(
                    dato = dag.dato,
                    type = dag.type,
                    fraType = dag.fraType,
                    grad = dag.grad,
                    fraGrad = dag.fraGrad,
                )
            },
        ferdigstilt = ferdigstilt,
    )

private fun OverstyringInntektDto.tilInntektoverstyring() =
    Inntektoverstyring(
        hendelseId = hendelseId,
        timestamp = timestamp,
        saksbehandler =
            Saksbehandler(
                navn = saksbehandlerNavn,
                ident = saksbehandlerIdent,
            ),
        inntekt =
            Inntektoverstyring.OverstyrtInntekt(
                forklaring = forklaring,
                begrunnelse = begrunnelse,
                manedligInntekt = månedligInntekt,
                fraManedligInntekt = fraMånedligInntekt,
                skjaeringstidspunkt = skjæringstidspunkt,
                refusjonsopplysninger =
                    refusjonsopplysninger?.map {
                        Inntektoverstyring.Refusjonsopplysning(
                            fom = it.fom,
                            tom = it.tom,
                            belop = it.beløp,
                        )
                    } ?: emptyList(),
                fraRefusjonsopplysninger =
                    fraRefusjonsopplysninger?.map {
                        Inntektoverstyring.Refusjonsopplysning(
                            fom = it.fom,
                            tom = it.tom,
                            belop = it.beløp,
                        )
                    } ?: emptyList(),
            ),
        ferdigstilt = ferdigstilt,
    )

private fun OverstyringArbeidsforholdDto.tilArbeidsforholdoverstyring() =
    Arbeidsforholdoverstyring(
        hendelseId = hendelseId,
        begrunnelse = begrunnelse,
        timestamp = timestamp,
        saksbehandler =
            Saksbehandler(
                navn = saksbehandlerNavn,
                ident = saksbehandlerIdent,
            ),
        deaktivert = deaktivert,
        skjaeringstidspunkt = skjæringstidspunkt,
        forklaring = forklaring,
        ferdigstilt = ferdigstilt,
    )

private fun SkjønnsfastsettingSykepengegrunnlagDto.tilSykepengegrunnlagSkjønnsfastsetting() =
    Sykepengegrunnlagskjonnsfastsetting(
        hendelseId = hendelseId,
        timestamp = timestamp,
        saksbehandler =
            Saksbehandler(
                navn = saksbehandlerNavn,
                ident = saksbehandlerIdent,
            ),
        skjonnsfastsatt =
            Sykepengegrunnlagskjonnsfastsetting.SkjonnsfastsattSykepengegrunnlag(
                arsak = årsak,
                type = type,
                begrunnelse = begrunnelse,
                begrunnelseMal = begrunnelseMal,
                begrunnelseFritekst = begrunnelseFritekst,
                begrunnelseKonklusjon = begrunnelseKonklusjon,
                arlig = årlig,
                fraArlig = fraÅrlig,
                skjaeringstidspunkt = skjæringstidspunkt,
            ),
        ferdigstilt = ferdigstilt,
    )

private fun OverstyringMinimumSykdomsgradDto.tilMinimumSykdomsgradOverstyring() =
    MinimumSykdomsgradOverstyring(
        hendelseId = hendelseId,
        timestamp = timestamp,
        saksbehandler =
            Saksbehandler(
                navn = saksbehandlerNavn,
                ident = saksbehandlerIdent,
            ),
        minimumSykdomsgrad =
            MinimumSykdomsgradOverstyring.OverstyrtMinimumSykdomsgrad(
                fom = fom,
                tom = tom,
                vurdering = vurdering,
                begrunnelse = begrunnelse,
                initierendeVedtaksperiodeId = initierendeVedtaksperiodeId,
            ),
        ferdigstilt = ferdigstilt,
    )
