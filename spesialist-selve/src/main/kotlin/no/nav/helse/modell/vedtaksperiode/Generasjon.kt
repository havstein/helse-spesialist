package no.nav.helse.modell.vedtaksperiode

import net.logstash.logback.argument.StructuredArguments.keyValue
import net.logstash.logback.argument.StructuredArguments.kv
import no.nav.helse.modell.person.vedtaksperiode.Varsel
import no.nav.helse.modell.person.vedtaksperiode.Varsel.Companion.automatiskGodkjennSpesialsakvarsler
import no.nav.helse.modell.person.vedtaksperiode.Varsel.Companion.finnEksisterendeVarsel
import no.nav.helse.modell.person.vedtaksperiode.Varsel.Companion.forhindrerAutomatisering
import no.nav.helse.modell.person.vedtaksperiode.Varsel.Companion.inneholderAktivtVarselOmAvvik
import no.nav.helse.modell.person.vedtaksperiode.Varsel.Companion.inneholderMedlemskapsvarsel
import no.nav.helse.modell.person.vedtaksperiode.Varsel.Companion.inneholderSvartelistedeVarsler
import no.nav.helse.modell.person.vedtaksperiode.Varsel.Companion.inneholderVarselOmAvvik
import no.nav.helse.modell.person.vedtaksperiode.Varsel.Companion.inneholderVarselOmNegativtBeløp
import no.nav.helse.modell.person.vedtaksperiode.Varsel.Companion.inneholderVarselOmTilbakedatering
import no.nav.helse.modell.person.vedtaksperiode.Varsel.Companion.inneholderVarselOmÅpenGosysOppgave
import no.nav.helse.modell.vedtak.Avslag
import no.nav.helse.modell.vedtak.SaksbehandlerVurdering
import no.nav.helse.modell.vedtak.SykepengevedtakBuilder
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.util.UUID

internal class Generasjon private constructor(
    private val id: UUID,
    private val vedtaksperiodeId: UUID,
    utbetalingId: UUID?,
    private var spleisBehandlingId: UUID?,
    private var skjæringstidspunkt: LocalDate,
    private var periode: Periode,
    private var tilstand: Tilstand,
    private var tags: List<String>,
    private val avslag: Avslag?,
    private val saksbehandlerVurdering: SaksbehandlerVurdering?,
    varsler: Set<Varsel>,
) {
    internal constructor(
        id: UUID,
        vedtaksperiodeId: UUID,
        fom: LocalDate,
        tom: LocalDate,
        skjæringstidspunkt: LocalDate,
        spleisBehandlingId: UUID? = null,
        utbetalingId: UUID? = null,
    ) : this(
        id = id,
        vedtaksperiodeId = vedtaksperiodeId,
        utbetalingId = utbetalingId,
        spleisBehandlingId = spleisBehandlingId,
        skjæringstidspunkt = skjæringstidspunkt,
        periode = Periode(fom, tom),
        tilstand = VidereBehandlingAvklares,
        tags = emptyList(),
        avslag = null,
        saksbehandlerVurdering = null,
        varsler = emptySet(),
    )

    private val varsler: MutableList<Varsel> = varsler.toMutableList()

    internal var utbetalingId: UUID? = utbetalingId
        private set

    internal fun spleisBehandlingId() = spleisBehandlingId

    internal fun skjæringstidspunkt() = skjæringstidspunkt

    internal fun unikId() = id

    internal fun hasterÅBehandle() = varsler.inneholderVarselOmNegativtBeløp()

    internal fun fom() = periode.fom()

    internal fun tom() = periode.tom()

    internal fun toDto(): GenerasjonDto =
        GenerasjonDto(
            id = id,
            vedtaksperiodeId = vedtaksperiodeId,
            utbetalingId = utbetalingId,
            spleisBehandlingId = spleisBehandlingId,
            skjæringstidspunkt = skjæringstidspunkt,
            fom = periode.fom(),
            tom = periode.tom(),
            tilstand = tilstand.toDto(),
            tags = tags,
            avslag = avslag?.toDto(),
            saksbehandlerVurdering = saksbehandlerVurdering?.toDto(),
            varsler = varsler.map(Varsel::toDto),
        )

    internal fun tilhører(dato: LocalDate): Boolean = periode.tom() <= dato

    internal fun nySpleisBehandling(spleisBehandling: SpleisBehandling) = nyBehandling(spleisBehandling)

    internal fun forhindrerAutomatisering(): Boolean = varsler.forhindrerAutomatisering()

    internal fun harKunGosysvarsel() = varsler.size == 1 && varsler.single().erGosysvarsel()

    internal fun håndter(
        vedtaksperiode: Vedtaksperiode,
        spleisVedtaksperiode: SpleisVedtaksperiode,
    ) {
        tilstand.spleisVedtaksperiode(vedtaksperiode, this, spleisVedtaksperiode)
    }

    private fun spleisVedtaksperiode(spleisVedtaksperiode: SpleisVedtaksperiode) {
        this.periode = Periode(spleisVedtaksperiode.fom, spleisVedtaksperiode.tom)
        this.skjæringstidspunkt = spleisVedtaksperiode.skjæringstidspunkt
        this.spleisBehandlingId = spleisVedtaksperiode.spleisBehandlingId
    }

    internal fun erSpesialsakSomKanAutomatiseres() = !varsler.inneholderSvartelistedeVarsler()

    internal fun automatiskGodkjennSpesialsakvarsler() = varsler.automatiskGodkjennSpesialsakvarsler()

    internal fun håndterNyUtbetaling(utbetalingId: UUID) {
        tilstand.nyUtbetaling(this, utbetalingId)
    }

    internal fun håndterForkastetUtbetaling(utbetalingId: UUID) {
        if (utbetalingId != this.utbetalingId) return
        tilstand.invaliderUtbetaling(this, utbetalingId)
    }

    internal fun håndterNyttVarsel(varsel: Varsel) {
        if (!varsel.erRelevantFor(vedtaksperiodeId)) return
        val eksisterendeVarsel = varsler.finnEksisterendeVarsel(varsel) ?: return nyttVarsel(varsel)
        if (varsel.erVarselOmAvvik() && varsler.inneholderVarselOmAvvik()) {
            varsler.remove(eksisterendeVarsel)
            logg.info("Slettet eksisterende varsel ({}) for generasjon med id {}", varsel.toString(), id)
            nyttVarsel(varsel)
        }
        if (eksisterendeVarsel.erAktiv()) return
        eksisterendeVarsel.reaktiver()
    }

    internal fun håndterDeaktivertVarsel(varsel: Varsel) {
        val funnetVarsel = varsler.finnEksisterendeVarsel(varsel) ?: return
        funnetVarsel.deaktiver()
    }

    internal fun deaktiverVarsel(varselkode: String) {
        val funnetVarsel = varsler.finnEksisterendeVarsel(varselkode) ?: return
        sikkerlogg.info("Deaktiverer varsel: {}", funnetVarsel)
        funnetVarsel.deaktiver()
    }

    internal fun oppdaterBehandlingsinformasjon(
        tags: List<String>,
        spleisBehandlingId: UUID,
        utbetalingId: UUID,
    ) {
        tilstand.oppdaterBehandlingsinformasjon(this, tags, spleisBehandlingId, utbetalingId)
    }

    internal fun håndterGodkjentAvSaksbehandler() {
        tilstand.håndterGodkjenning(this)
    }

    internal fun håndterVedtakFattet() {
        tilstand.vedtakFattet(this)
    }

    internal fun avsluttetUtenVedtak(
        avsluttetUtenVedtak: no.nav.helse.modell.vedtak.AvsluttetUtenVedtak,
        sykepengevedtakBuilder: SykepengevedtakBuilder,
    ) {
        if (spleisBehandlingId == null) spleisBehandlingId = avsluttetUtenVedtak.spleisBehandlingId()
        tilstand.avsluttetUtenVedtak(this, sykepengevedtakBuilder)
    }

    internal fun byggVedtak(vedtakBuilder: SykepengevedtakBuilder) {
        if (tags.isEmpty()) {
            sikkerlogg.error(
                "Ingen tags funnet for spleisBehandlingId: $spleisBehandlingId på vedtaksperiodeId: $vedtaksperiodeId",
            )
        }

        vedtakBuilder.tags(tags)
        vedtakBuilder.vedtaksperiodeId(vedtaksperiodeId)
        vedtakBuilder.spleisBehandlingId(behandlingId())
        vedtakBuilder.utbetalingId(utbetalingId())
        vedtakBuilder.skjæringstidspunkt(skjæringstidspunkt)
        vedtakBuilder.fom(fom())
        vedtakBuilder.tom(tom())
        avslag?.also { vedtakBuilder.avslag(it) }
        saksbehandlerVurdering?.also { vedtakBuilder.saksbehandlerVurdering(it) }
    }

    private fun behandlingId(): UUID {
        return spleisBehandlingId ?: throw IllegalStateException("Forventer at spleisBehandlingId er satt")
    }

    private fun utbetalingId(): UUID {
        return utbetalingId ?: throw IllegalStateException("Forventer at utbetalingId er satt")
    }

    private fun nyTilstand(ny: Tilstand) {
        this.tilstand = ny
    }

    private fun supplerAvsluttetUtenVedtak(sykepengevedtakBuilder: SykepengevedtakBuilder) {
        spleisBehandlingId?.let { sykepengevedtakBuilder.spleisBehandlingId(it) }
        sykepengevedtakBuilder
            .tags(tags)
            .skjæringstidspunkt(skjæringstidspunkt)
            .fom(fom())
            .tom(tom())
    }

    private fun nyUtbetaling(utbetalingId: UUID) {
        this.utbetalingId = utbetalingId
    }

    private fun nyBehandling(spleisBehandling: SpleisBehandling): Generasjon {
        val nyGenerasjon =
            Generasjon(
                id = UUID.randomUUID(),
                vedtaksperiodeId = vedtaksperiodeId,
                fom = spleisBehandling.fom,
                tom = spleisBehandling.tom,
                skjæringstidspunkt = skjæringstidspunkt,
                spleisBehandlingId = spleisBehandling.spleisBehandlingId,
            )
        flyttAktiveVarslerTil(nyGenerasjon)
        return nyGenerasjon
    }

    private fun flyttAktiveVarslerTil(generasjon: Generasjon) {
        val aktiveVarsler = varsler.filter(Varsel::erAktiv)
        this.varsler.removeAll(aktiveVarsler)
        generasjon.varsler.addAll(aktiveVarsler)
        if (aktiveVarsler.isNotEmpty()) {
            sikkerlogg.info(
                "Flytter ${aktiveVarsler.size} varsler fra {} til {}. Gammel generasjon har {}",
                kv("gammel_generasjon", this.id),
                kv("ny_generasjon", generasjon.id),
                kv("utbetalingId", this.utbetalingId),
            )
        }
    }

    private fun nyttVarsel(varsel: Varsel) {
        varsler.add(varsel)
        tilstand.nyttVarsel(this)
    }

    private fun harMedlemskapsvarsel(): Boolean {
        val inneholderMedlemskapsvarsel = varsler.inneholderMedlemskapsvarsel()
        logg.info("$this harMedlemskapsvarsel: $inneholderMedlemskapsvarsel")
        return inneholderMedlemskapsvarsel
    }

    private fun kreverSkjønnsfastsettelse(): Boolean {
        val inneholderAvviksvarsel = varsler.inneholderAktivtVarselOmAvvik()
        logg.info("$this harAvviksvarsel: $inneholderAvviksvarsel")
        return inneholderAvviksvarsel
    }

    private fun erTilbakedatert(): Boolean {
        val inneholderTilbakedateringsvarsel = varsler.inneholderVarselOmTilbakedatering()
        logg.info("$this harTilbakedateringsvarsel: $inneholderTilbakedateringsvarsel")
        return inneholderTilbakedateringsvarsel
    }

    private fun harKunÅpenGosysOppgave(): Boolean {
        val inneholderKunÅpenGosysOppgaveVarsel = varsler.inneholderVarselOmÅpenGosysOppgave() && varsler.size == 1
        logg.info("$this harKunÅpenGosysOppgavevarsel: $inneholderKunÅpenGosysOppgaveVarsel")
        return inneholderKunÅpenGosysOppgaveVarsel
    }

    internal sealed interface Tilstand {
        fun navn(): String

        fun toDto(): TilstandDto =
            when (this) {
                AvsluttetUtenVedtak -> TilstandDto.AvsluttetUtenVedtak
                VedtakFattet -> TilstandDto.VedtakFattet
                VidereBehandlingAvklares -> TilstandDto.VidereBehandlingAvklares
                AvsluttetUtenVedtakMedVarsler -> TilstandDto.AvsluttetUtenVedtakMedVarsler
                KlarTilBehandling -> TilstandDto.KlarTilBehandling
            }

        fun avsluttetUtenVedtak(
            generasjon: Generasjon,
            sykepengevedtakBuilder: SykepengevedtakBuilder,
        ): Unit = throw IllegalStateException("Forventer ikke avsluttet_uten_vedtak i tilstand=${this::class.simpleName}")

        fun vedtakFattet(generasjon: Generasjon) {
            sikkerlogg.info("Forventet ikke vedtak_fattet i {}", kv("tilstand", this::class.simpleName))
        }

        fun spleisVedtaksperiode(
            vedtaksperiode: Vedtaksperiode,
            generasjon: Generasjon,
            spleisVedtaksperiode: SpleisVedtaksperiode,
        ) {
        }

        fun nyUtbetaling(
            generasjon: Generasjon,
            utbetalingId: UUID,
        ) {
            sikkerlogg.error(
                "Mottatt ny utbetaling med {} for {} i {}",
                keyValue("utbetalingId", utbetalingId),
                keyValue("generasjon", generasjon),
                keyValue("tilstand", this::class.simpleName),
            )
            logg.error(
                "Mottatt ny utbetaling med {} i {}",
                keyValue("utbetalingId", utbetalingId),
                keyValue("tilstand", this::class.simpleName),
            )
        }

        fun invaliderUtbetaling(
            generasjon: Generasjon,
            utbetalingId: UUID,
        ) {
            logg.error(
                "{} er i {}. Utbetaling med {} forsøkt forkastet",
                keyValue("Generasjon", generasjon),
                keyValue("tilstand", this::class.simpleName),
                keyValue("utbetalingId", utbetalingId),
            )
            sikkerlogg.error(
                "{} er i {}. Utbetaling med {} forsøkt forkastet",
                keyValue("Generasjon", generasjon),
                keyValue("tilstand", this::class.simpleName),
                keyValue("utbetalingId", utbetalingId),
            )
        }

        fun nyttVarsel(generasjon: Generasjon) {}

        fun håndterGodkjenning(generasjon: Generasjon) {}

        fun oppdaterBehandlingsinformasjon(
            generasjon: Generasjon,
            tags: List<String>,
            spleisBehandlingId: UUID,
            utbetalingId: UUID,
        ): Unit = throw IllegalStateException("Mottatt godkjenningsbehov i tilstand=${navn()}")
    }

    internal data object VidereBehandlingAvklares : Tilstand {
        override fun navn(): String = "VidereBehandlingAvklares"

        override fun nyUtbetaling(
            generasjon: Generasjon,
            utbetalingId: UUID,
        ) {
            generasjon.nyUtbetaling(utbetalingId)
            generasjon.nyTilstand(KlarTilBehandling)
        }

        override fun spleisVedtaksperiode(
            vedtaksperiode: Vedtaksperiode,
            generasjon: Generasjon,
            spleisVedtaksperiode: SpleisVedtaksperiode,
        ) {
            generasjon.spleisVedtaksperiode(spleisVedtaksperiode)
        }

        override fun avsluttetUtenVedtak(
            generasjon: Generasjon,
            sykepengevedtakBuilder: SykepengevedtakBuilder,
        ) {
            check(
                generasjon.utbetalingId == null,
            ) { "Mottatt avsluttet_uten_vedtak på generasjon som har utbetaling. Det gir ingen mening." }
            val nesteTilstand =
                when {
                    generasjon.varsler.isNotEmpty() -> AvsluttetUtenVedtakMedVarsler
                    else -> AvsluttetUtenVedtak
                }
            generasjon.nyTilstand(nesteTilstand)
            generasjon.supplerAvsluttetUtenVedtak(sykepengevedtakBuilder)
        }
    }

    internal data object KlarTilBehandling : Tilstand {
        override fun navn(): String = "KlarTilBehandling"

        override fun vedtakFattet(generasjon: Generasjon) {
            checkNotNull(generasjon.utbetalingId) { "Mottatt vedtak_fattet i tilstand=${navn()}, men mangler utbetalingId" }
            generasjon.nyTilstand(VedtakFattet)
        }

        override fun oppdaterBehandlingsinformasjon(
            generasjon: Generasjon,
            tags: List<String>,
            spleisBehandlingId: UUID,
            utbetalingId: UUID,
        ) {
            generasjon.tags = tags
            generasjon.spleisBehandlingId = spleisBehandlingId
            generasjon.utbetalingId = utbetalingId
        }

        override fun invaliderUtbetaling(
            generasjon: Generasjon,
            utbetalingId: UUID,
        ) {
            generasjon.utbetalingId = null
            generasjon.nyTilstand(VidereBehandlingAvklares)
        }

        override fun spleisVedtaksperiode(
            vedtaksperiode: Vedtaksperiode,
            generasjon: Generasjon,
            spleisVedtaksperiode: SpleisVedtaksperiode,
        ) {
            generasjon.spleisVedtaksperiode(spleisVedtaksperiode)
        }
    }

    internal data object VedtakFattet : Tilstand {
        override fun navn(): String = "VedtakFattet"
    }

    internal data object AvsluttetUtenVedtak : Tilstand {
        override fun navn(): String = "AvsluttetUtenVedtak"

        override fun nyttVarsel(generasjon: Generasjon) {
            sikkerlogg.warn("Mottar nytt varsel i tilstand ${navn()}")
            generasjon.nyTilstand(AvsluttetUtenVedtakMedVarsler)
        }

        override fun vedtakFattet(generasjon: Generasjon) {}
    }

    internal data object AvsluttetUtenVedtakMedVarsler : Tilstand {
        override fun navn(): String = "AvsluttetUtenVedtakMedVarsler"

        override fun håndterGodkjenning(generasjon: Generasjon) {
            generasjon.nyTilstand(AvsluttetUtenVedtak)
        }

        override fun vedtakFattet(generasjon: Generasjon) {}
    }

    override fun toString(): String = "generasjonId=$id, vedtaksperiodeId=$vedtaksperiodeId"

    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is Generasjon &&
                    javaClass == other.javaClass &&
                    id == other.id &&
                    vedtaksperiodeId == other.vedtaksperiodeId &&
                    utbetalingId == other.utbetalingId &&
                    spleisBehandlingId == other.spleisBehandlingId &&
                    tilstand == other.tilstand &&
                    skjæringstidspunkt == other.skjæringstidspunkt &&
                    periode == other.periode
            )

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + vedtaksperiodeId.hashCode()
        result = 31 * result + utbetalingId.hashCode()
        result = 31 * result + spleisBehandlingId.hashCode()
        result = 31 * result + tilstand.hashCode()
        result = 31 * result + skjæringstidspunkt.hashCode()
        result = 31 * result + periode.hashCode()
        return result
    }

    internal companion object {
        val logg: Logger = LoggerFactory.getLogger(Generasjon::class.java)
        private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")

        internal fun List<Generasjon>.finnGenerasjonForVedtaksperiode(vedtaksperiodeId: UUID): Generasjon? =
            this.find { it.vedtaksperiodeId == vedtaksperiodeId }

        internal fun List<Generasjon>.finnGenerasjonForSpleisBehandling(spleisBehandlingId: UUID): Generasjon? =
            this.find { it.spleisBehandlingId == spleisBehandlingId }

        internal fun List<Generasjon>.finnSisteGenerasjonUtenSpleisBehandlingId(): Generasjon? =
            this.lastOrNull { it.spleisBehandlingId == null }

        internal fun fraLagring(
            id: UUID,
            vedtaksperiodeId: UUID,
            utbetalingId: UUID?,
            spleisBehandlingId: UUID?,
            skjæringstidspunkt: LocalDate,
            fom: LocalDate,
            tom: LocalDate,
            tilstand: Tilstand,
            tags: List<String>,
            varsler: Set<Varsel>,
            avslag: Avslag?,
            saksbehandlerVurdering: SaksbehandlerVurdering?,
        ) = Generasjon(
            id = id,
            vedtaksperiodeId = vedtaksperiodeId,
            utbetalingId = utbetalingId,
            spleisBehandlingId = spleisBehandlingId,
            skjæringstidspunkt = skjæringstidspunkt,
            periode = Periode(fom, tom),
            tilstand = tilstand,
            tags = tags,
            varsler = varsler,
            avslag = avslag,
            saksbehandlerVurdering = saksbehandlerVurdering,
        )

        internal fun List<Generasjon>.håndterNyttVarsel(varsler: List<Varsel>) {
            forEach { generasjon ->
                varsler.forEach { generasjon.håndterNyttVarsel(it) }
            }
        }

        internal fun List<Generasjon>.forhindrerAutomatisering(tilOgMed: LocalDate): Boolean =
            this
                .filter {
                    it.tilhører(tilOgMed)
                }.any { it.forhindrerAutomatisering() }

        internal fun List<Generasjon>.forhindrerAutomatisering(generasjon: Generasjon): Boolean =
            this
                .filter {
                    it.tilhører(generasjon.periode.tom())
                }.any { it.forhindrerAutomatisering() }

        internal fun List<Generasjon>.harKunGosysvarsel(generasjon: Generasjon): Boolean =
            this
                .filter {
                    it.tilhører(generasjon.periode.tom())
                }.filter { it.varsler.isNotEmpty() }
                .all { it.harKunGosysvarsel() }

        internal fun List<Generasjon>.harMedlemskapsvarsel(vedtaksperiodeId: UUID): Boolean =
            overlapperMedEllerTidligereEnn(vedtaksperiodeId).any {
                it.harMedlemskapsvarsel()
            }

        internal fun List<Generasjon>.kreverSkjønnsfastsettelse(vedtaksperiodeId: UUID): Boolean =
            overlapperMedEllerTidligereEnn(vedtaksperiodeId).any {
                it.kreverSkjønnsfastsettelse()
            }

        internal fun List<Generasjon>.erTilbakedatert(vedtaksperiodeId: UUID): Boolean =
            overlapperMedEllerTidligereEnn(vedtaksperiodeId).any {
                it.erTilbakedatert()
            }

        internal fun List<Generasjon>.harÅpenGosysOppgave(vedtaksperiodeId: UUID): Boolean =
            overlapperMedEllerTidligereEnn(vedtaksperiodeId).any {
                it.harKunÅpenGosysOppgave()
            }

        internal fun List<Generasjon>.deaktiver(varsel: Varsel) {
            find { varsel.erRelevantFor(it.vedtaksperiodeId) }?.håndterDeaktivertVarsel(varsel)
        }

        internal fun List<Generasjon>.flyttEventueltAvviksvarselTil(vedtaksperiodeId: UUID) {
            val generasjonForPeriodeTilGodkjenning =
                finnGenerasjonForVedtaksperiode(vedtaksperiodeId) ?: run {
                    logg.warn("Finner ikke generasjon for vedtaksperiode $vedtaksperiodeId, sjekker ikke om avviksvarsel skal flyttes")
                    return
                }
            val varsel =
                filterNot {
                    it == generasjonForPeriodeTilGodkjenning
                }.flatMap { it.varsler }.find { it.erVarselOmAvvik() && it.erAktiv() } ?: return

            val generasjonMedVarsel = first { generasjon -> generasjon.varsler.contains(varsel) }
            logg.info(
                "Flytter et ikke-vurdert avviksvarsel fra vedtaksperiode ${generasjonMedVarsel.vedtaksperiodeId} til vedtaksperiode $vedtaksperiodeId",
            )
            generasjonMedVarsel.varsler.remove(varsel)
            generasjonForPeriodeTilGodkjenning.varsler.add(varsel)
        }

        internal fun List<Generasjon>.håndterGodkjent(vedtaksperiodeId: UUID) {
            overlapperMedEllerTidligereEnn(vedtaksperiodeId).forEach {
                it.håndterGodkjentAvSaksbehandler()
            }
        }

        private fun List<Generasjon>.overlapperMedEllerTidligereEnn(vedtaksperiodeId: UUID): List<Generasjon> {
            val gjeldende = find { it.vedtaksperiodeId == vedtaksperiodeId } ?: return emptyList()
            return sortedByDescending { it.periode.tom() }
                .filter { it.periode.fom() <= gjeldende.periode.tom() }
        }
    }
}
