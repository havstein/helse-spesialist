package no.nav.helse.spesialist.domain.tilkommeninntekt

import no.nav.helse.modell.totrinnsvurdering.TotrinnsvurderingId
import no.nav.helse.spesialist.domain.Periode
import no.nav.helse.spesialist.domain.ddd.AggregateRoot
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.util.SortedSet
import java.util.UUID
import kotlin.reflect.KMutableProperty0

@JvmInline
value class TilkommenInntektId(val value: UUID)

class TilkommenInntekt private constructor(
    opprettetEvent: TilkommenInntektOpprettetEvent,
) : AggregateRoot<TilkommenInntektId>(opprettetEvent.metadata.tilkommenInntektId) {
    private val _events: MutableList<TilkommenInntektEvent> = mutableListOf(opprettetEvent)
    val events: List<TilkommenInntektEvent> get() = _events

    val fødselsnummer: String = opprettetEvent.fødselsnummer
    var totrinnsvurderingId: TotrinnsvurderingId = opprettetEvent.metadata.totrinnsvurderingId
        private set
    var organisasjonsnummer: String = opprettetEvent.organisasjonsnummer
        private set
    var periode: Periode = opprettetEvent.periode
        private set
    var periodebeløp: BigDecimal = opprettetEvent.periodebeløp
        private set
    var dager: SortedSet<LocalDate> = opprettetEvent.dager
        private set
    var fjernet: Boolean = false
        private set
    var versjon: Int = opprettetEvent.metadata.sekvensnummer
        private set

    fun dagbeløp(): BigDecimal = periodebeløp.setScale(4).divide(dager.size.toBigDecimal(), RoundingMode.HALF_UP)

    fun endreTil(
        organisasjonsnummer: String,
        periode: Periode,
        periodebeløp: BigDecimal,
        dager: Set<LocalDate>,
        saksbehandlerIdent: String,
        notatTilBeslutter: String,
        totrinnsvurderingId: TotrinnsvurderingId,
    ) {
        apply(
            TilkommenInntektEndretEvent(
                TilkommenInntektEvent.Metadata(
                    tilkommenInntektId = id(),
                    sekvensnummer = versjon + 1,
                    tidspunkt = Instant.now(),
                    utførtAvSaksbehandlerIdent = saksbehandlerIdent,
                    notatTilBeslutter = notatTilBeslutter,
                    totrinnsvurderingId = totrinnsvurderingId,
                ),
                endringer =
                    TilkommenInntektEvent.Endringer(
                        organisasjonsnummer = muligEndring(fra = this.organisasjonsnummer, til = organisasjonsnummer),
                        periode = muligEndring(fra = this.periode, til = periode),
                        periodebeløp = muligEndring(fra = this.periodebeløp, til = periodebeløp),
                        dager = muligEndring(fra = this.dager, til = dager.toSortedSet()),
                    ),
            ),
        )
    }

    fun fjern(
        saksbehandlerIdent: String,
        notatTilBeslutter: String,
        totrinnsvurderingId: TotrinnsvurderingId,
    ) {
        apply(
            TilkommenInntektFjernetEvent(
                TilkommenInntektEvent.Metadata(
                    tilkommenInntektId = id(),
                    sekvensnummer = versjon + 1,
                    tidspunkt = Instant.now(),
                    utførtAvSaksbehandlerIdent = saksbehandlerIdent,
                    notatTilBeslutter = notatTilBeslutter,
                    totrinnsvurderingId = totrinnsvurderingId,
                ),
            ),
        )
    }

    fun gjenopprett(
        organisasjonsnummer: String,
        periode: Periode,
        periodebeløp: BigDecimal,
        dager: Set<LocalDate>,
        saksbehandlerIdent: String,
        notatTilBeslutter: String,
        totrinnsvurderingId: TotrinnsvurderingId,
    ) {
        apply(
            TilkommenInntektGjenopprettetEvent(
                TilkommenInntektEvent.Metadata(
                    tilkommenInntektId = id(),
                    sekvensnummer = versjon + 1,
                    tidspunkt = Instant.now(),
                    utførtAvSaksbehandlerIdent = saksbehandlerIdent,
                    notatTilBeslutter = notatTilBeslutter,
                    totrinnsvurderingId = totrinnsvurderingId,
                ),
                endringer =
                    TilkommenInntektEvent.Endringer(
                        organisasjonsnummer = muligEndring(fra = this.organisasjonsnummer, til = organisasjonsnummer),
                        periode = muligEndring(fra = this.periode, til = periode),
                        periodebeløp = muligEndring(fra = this.periodebeløp, til = periodebeløp),
                        dager = muligEndring(fra = this.dager, til = dager.toSortedSet()),
                    ),
            ),
        )
    }

    private fun <T> muligEndring(
        fra: T,
        til: T,
    ): Endring<T>? = if (fra != til) Endring(fra = fra, til = til) else null

    private fun apply(event: TilkommenInntektEvent) {
        håndterEvent(event)
        when (event) {
            is TilkommenInntektOpprettetEvent -> error("Kan ikke håndtere opphavsevent inni et eksisterende objekt")

            is TilkommenInntektEndretEvent -> {
                håndterEndringer(event.endringer)
            }

            is TilkommenInntektFjernetEvent -> {
                if (fjernet) error("Prøvde å fjerne tilkommen inntekt som allerede var fjernet!")
                fjernet = true
            }

            is TilkommenInntektGjenopprettetEvent -> {
                if (!fjernet) error("Prøvde å gjenopprette tilkommen inntekt som ikke var fjernet!")
                fjernet = false
                håndterEndringer(event.endringer)
            }
        }
    }

    private fun håndterEvent(event: TilkommenInntektEvent) {
        if (event.metadata.sekvensnummer != this.versjon + 1) {
            error(
                "Fikk events ute av rekkefølge: $versjon -> ${event.metadata.sekvensnummer}",
            )
        }
        this.totrinnsvurderingId = event.metadata.totrinnsvurderingId
        this.versjon = event.metadata.sekvensnummer
        this._events.add(event)
    }

    private fun håndterEndringer(endringer: TilkommenInntektEvent.Endringer) {
        håndterEndring(endringer.organisasjonsnummer, this::organisasjonsnummer)
        håndterEndring(endringer.periode, this::periode)
        håndterEndring(endringer.periodebeløp, this::periodebeløp)
        håndterEndring(endringer.dager, this::dager)
    }

    private fun <T> håndterEndring(
        endring: Endring<T>?,
        prop: KMutableProperty0<T>,
    ) {
        if (endring != null) {
            if (endring.fra != prop.get()) {
                error("Fikk event med endring med feil fra-verdi for ${prop.name}!")
            } else {
                prop.set(endring.til)
            }
        }
    }

    companion object {
        fun ny(
            fødselsnummer: String,
            saksbehandlerIdent: String,
            notatTilBeslutter: String,
            totrinnsvurderingId: TotrinnsvurderingId,
            organisasjonsnummer: String,
            periode: Periode,
            periodebeløp: BigDecimal,
            dager: Set<LocalDate>,
        ) = TilkommenInntekt(
            TilkommenInntektOpprettetEvent(
                TilkommenInntektEvent.Metadata(
                    tilkommenInntektId = TilkommenInntektId(UUID.randomUUID()),
                    sekvensnummer = 1,
                    tidspunkt = Instant.now(),
                    utførtAvSaksbehandlerIdent = saksbehandlerIdent,
                    notatTilBeslutter = notatTilBeslutter,
                    totrinnsvurderingId = totrinnsvurderingId,
                ),
                fødselsnummer = fødselsnummer,
                organisasjonsnummer = organisasjonsnummer,
                periode = periode,
                periodebeløp = periodebeløp,
                dager = dager.toSortedSet(),
            ),
        )

        fun fraLagring(events: List<TilkommenInntektEvent>): TilkommenInntekt =
            TilkommenInntekt(events.first() as TilkommenInntektOpprettetEvent)
                .also { tilkommenInntekt -> events.drop(1).forEach(tilkommenInntekt::apply) }
    }
}
