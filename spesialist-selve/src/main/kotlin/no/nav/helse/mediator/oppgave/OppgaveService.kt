package no.nav.helse.mediator.oppgave

import net.logstash.logback.argument.StructuredArguments.kv
import no.nav.helse.Tilgangsgrupper
import no.nav.helse.db.EgenskapForDatabase
import no.nav.helse.db.MeldingRepository
import no.nav.helse.db.OppgaveRepository
import no.nav.helse.db.OppgavesorteringForDatabase
import no.nav.helse.db.OpptegnelseRepository
import no.nav.helse.db.ReservasjonDao
import no.nav.helse.db.SaksbehandlerRepository
import no.nav.helse.db.SorteringsnøkkelForDatabase
import no.nav.helse.db.TildelingRepository
import no.nav.helse.db.TotrinnsvurderingFraDatabase
import no.nav.helse.db.TotrinnsvurderingRepository
import no.nav.helse.mediator.SaksbehandlerMediator.Companion.tilApiversjon
import no.nav.helse.mediator.TilgangskontrollørForApi
import no.nav.helse.mediator.oppgave.OppgaveMapper.tilApiversjon
import no.nav.helse.mediator.oppgave.OppgaveMapper.tilBehandledeOppgaver
import no.nav.helse.mediator.oppgave.OppgaveMapper.tilDatabaseversjon
import no.nav.helse.mediator.oppgave.OppgaveMapper.tilEgenskaperForVisning
import no.nav.helse.mediator.oppgave.OppgaveMapper.tilOppgaverTilBehandling
import no.nav.helse.modell.Modellfeil
import no.nav.helse.modell.oppgave.Egenskap
import no.nav.helse.modell.oppgave.Oppgave
import no.nav.helse.modell.oppgave.Oppgave.Companion.toDto
import no.nav.helse.modell.saksbehandler.Saksbehandler
import no.nav.helse.modell.saksbehandler.Tilgangskontroll
import no.nav.helse.modell.saksbehandler.handlinger.LeggPåVent
import no.nav.helse.modell.saksbehandler.handlinger.Oppgavehandling
import no.nav.helse.modell.saksbehandler.handlinger.Overstyring
import no.nav.helse.modell.totrinnsvurdering.TotrinnsvurderingDto
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.spesialist.api.abonnement.GodkjenningsbehovPayload
import no.nav.helse.spesialist.api.abonnement.OpptegnelseType
import no.nav.helse.spesialist.api.graphql.schema.AntallOppgaver
import no.nav.helse.spesialist.api.graphql.schema.BehandledeOppgaver
import no.nav.helse.spesialist.api.graphql.schema.Filtrering
import no.nav.helse.spesialist.api.graphql.schema.Oppgaveegenskap
import no.nav.helse.spesialist.api.graphql.schema.OppgaverTilBehandling
import no.nav.helse.spesialist.api.graphql.schema.Oppgavesortering
import no.nav.helse.spesialist.api.graphql.schema.Sorteringsnokkel
import no.nav.helse.spesialist.api.oppgave.Oppgavehåndterer
import no.nav.helse.spesialist.api.saksbehandler.SaksbehandlerFraApi
import org.slf4j.LoggerFactory
import java.sql.SQLException
import java.util.UUID

interface Oppgavefinner {
    fun oppgave(
        utbetalingId: UUID,
        oppgaveBlock: Oppgave?.() -> Unit,
    )
}

internal class OppgaveService(
    private val meldingRepository: MeldingRepository,
    private val oppgaveRepository: OppgaveRepository,
    private val tildelingRepository: TildelingRepository,
    private val reservasjonDao: ReservasjonDao,
    private val opptegnelseRepository: OpptegnelseRepository,
    private val totrinnsvurderingRepository: TotrinnsvurderingRepository,
    private val saksbehandlerRepository: SaksbehandlerRepository,
    private val rapidsConnection: RapidsConnection,
    private val tilgangskontroll: Tilgangskontroll,
    private val tilgangsgrupper: Tilgangsgrupper,
) : Oppgavehåndterer, Oppgavefinner {
    private val logg = LoggerFactory.getLogger(this::class.java)
    private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")

    internal fun nyOppgave(
        fødselsnummer: String,
        contextId: UUID,
        opprettOppgaveBlock: (reservertId: Long) -> Oppgave,
    ) {
        val nesteId = oppgaveRepository.reserverNesteId()
        val oppgave = opprettOppgaveBlock(nesteId)
        val oppgavemelder = Oppgavemelder(meldingRepository, rapidsConnection)
        oppgave.register(oppgavemelder)
        tildelVedReservasjon(fødselsnummer, oppgave)
        Oppgavelagrer(tildelingRepository).lagre(this, oppgave.toDto(), contextId)
        oppgavemelder.oppgaveOpprettet(oppgave)
    }

    fun <T> oppgave(
        id: Long,
        oppgaveBlock: Oppgave.() -> T,
    ): T {
        val oppgave =
            Oppgavehenter(
                oppgaveRepository,
                totrinnsvurderingRepository,
                saksbehandlerRepository,
                tilgangskontroll,
            ).oppgave(id)
        oppgave.register(Oppgavemelder(meldingRepository, rapidsConnection))
        val returverdi = oppgaveBlock(oppgave)
        Oppgavelagrer(tildelingRepository).oppdater(this@OppgaveService, oppgave.toDto())
        return returverdi
    }

    override fun oppgave(
        utbetalingId: UUID,
        oppgaveBlock: Oppgave?.() -> Unit,
    ) {
        val oppgaveId = oppgaveRepository.finnOppgaveId(utbetalingId)
        oppgaveId?.let {
            oppgave(it, oppgaveBlock)
        }
    }

    internal fun lagreTotrinnsvurdering(totrinnsvurderingDto: TotrinnsvurderingDto) {
        val totrinnsvurderingFraDatabase =
            TotrinnsvurderingFraDatabase(
                vedtaksperiodeId = totrinnsvurderingDto.vedtaksperiodeId,
                erRetur = totrinnsvurderingDto.erRetur,
                saksbehandler = totrinnsvurderingDto.saksbehandler?.oid,
                beslutter = totrinnsvurderingDto.beslutter?.oid,
                utbetalingId = totrinnsvurderingDto.utbetalingId,
                opprettet = totrinnsvurderingDto.opprettet,
                oppdatert = totrinnsvurderingDto.oppdatert,
            )
        totrinnsvurderingRepository.oppdater(totrinnsvurderingFraDatabase)
    }

    internal fun håndter(
        handling: Oppgavehandling,
        saksbehandler: Saksbehandler,
    ) {
        oppgave(handling.oppgaveId()) {
            handling.oppgave(this)
            handling.utførAv(saksbehandler)
        }
    }

    internal fun håndter(handling: Overstyring) {
        oppgaveRepository.finnOppgaveId(handling.gjelderFødselsnummer())?.let {
            oppgave(it) {
                this.avbryt()
            }
        }
    }

    internal fun leggPåVent(
        handling: LeggPåVent,
        saksbehandler: Saksbehandler,
    ) {
        oppgave(handling.oppgaveId) {
            this.leggPåVent(handling.skalTildeles, saksbehandler)
        }
    }

    internal fun fjernFraPåVent(oppgaveId: Long) {
        oppgave(oppgaveId) {
            this.fjernFraPåVent()
        }
    }

    override fun sendTilBeslutter(
        oppgaveId: Long,
        behandlendeSaksbehandler: SaksbehandlerFraApi,
    ) {
        val saksbehandler = behandlendeSaksbehandler.tilSaksbehandler()
        oppgave(oppgaveId) {
            try {
                sendTilBeslutter(saksbehandler)
            } catch (e: Modellfeil) {
                throw e.tilApiversjon()
            }
        }
    }

    override fun sendIRetur(
        oppgaveId: Long,
        besluttendeSaksbehandler: SaksbehandlerFraApi,
    ) {
        val saksbehandler = besluttendeSaksbehandler.tilSaksbehandler()
        oppgave(oppgaveId) {
            try {
                sendIRetur(saksbehandler)
            } catch (e: Modellfeil) {
                throw e.tilApiversjon()
            }
        }
    }

    override fun endretEgenAnsattStatus(
        erEgenAnsatt: Boolean,
        fødselsnummer: String,
    ) {
        val oppgaveId =
            oppgaveRepository.finnOppgaveId(fødselsnummer) ?: run {
                sikkerlogg.info("Ingen aktiv oppgave for {}", kv("fødselsnummer", fødselsnummer))
                return
            }
        oppgave(oppgaveId) {
            if (erEgenAnsatt) {
                logg.info("Legger til egenskap EGEN_ANSATT på {}", kv("oppgaveId", oppgaveId))
                sikkerlogg.info("Legger til egenskap EGEN_ANSATT for {}", kv("fødselsnummer", fødselsnummer))
                leggTilEgenAnsatt()
            } else {
                logg.info("Fjerner egenskap EGEN_ANSATT på {}", kv("oppgaveId", oppgaveId))
                sikkerlogg.info("Fjerner egenskap EGEN_ANSATT for {}", kv("fødselsnummer", fødselsnummer))
                fjernEgenAnsatt()
            }
        }
    }

    override fun venterPåSaksbehandler(oppgaveId: Long): Boolean = oppgaveRepository.venterPåSaksbehandler(oppgaveId)

    override fun spleisBehandlingId(oppgaveId: Long): UUID = oppgaveRepository.finnSpleisBehandlingId(oppgaveId)

    override fun oppgaver(
        saksbehandlerFraApi: SaksbehandlerFraApi,
        offset: Int,
        limit: Int,
        sortering: List<Oppgavesortering>,
        filtrering: Filtrering,
    ): OppgaverTilBehandling {
        val saksbehandler = saksbehandlerFraApi.tilSaksbehandler()
        val egenskaperSaksbehandlerIkkeHarTilgangTil =
            Egenskap
                .alleTilgangsstyrteEgenskaper
                .filterNot { saksbehandler.harTilgangTil(listOf(it)) }
                .map(Egenskap::toString)

        val alleUkategoriserteEgenskaper =
            Egenskap
                .alleUkategoriserteEgenskaper
                .map(Egenskap::toString)

        val ekskluderteEgenskaper =
            filtrering.ekskluderteEgenskaper?.tilDatabaseversjon()?.map(
                EgenskapForDatabase::toString,
            ) ?: emptyList()

        val egenskaperSomSkalEkskluderes =
            egenskaperSaksbehandlerIkkeHarTilgangTil + ekskluderteEgenskaper + if (filtrering.ingenUkategoriserteEgenskaper) alleUkategoriserteEgenskaper else emptyList()

        val grupperteFiltrerteEgenskaper =
            filtrering.egenskaper
                .groupBy { it.kategori }
                .map { it.key.tilDatabaseversjon() to it.value.tilDatabaseversjon() }
                .toMap()

        val oppgaver =
            oppgaveRepository
                .finnOppgaverForVisning(
                    ekskluderEgenskaper = egenskaperSomSkalEkskluderes,
                    saksbehandlerOid = saksbehandler.oid(),
                    offset = offset,
                    limit = limit,
                    sortering = sortering.tilOppgavesorteringForDatabase(),
                    egneSakerPåVent = filtrering.egneSakerPaVent,
                    egneSaker = filtrering.egneSaker,
                    tildelt = filtrering.tildelt,
                    grupperteFiltrerteEgenskaper = grupperteFiltrerteEgenskaper,
                )
        return OppgaverTilBehandling(
            oppgaver = oppgaver.tilOppgaverTilBehandling(),
            totaltAntallOppgaver = if (oppgaver.isEmpty()) 0 else oppgaver.first().filtrertAntall,
        )
    }

    override fun antallOppgaver(saksbehandlerFraApi: SaksbehandlerFraApi): AntallOppgaver {
        val saksbehandler = saksbehandlerFraApi.tilSaksbehandler()
        val antallOppgaver = oppgaveRepository.finnAntallOppgaver(saksbehandlerOid = saksbehandler.oid())
        return antallOppgaver.tilApiversjon()
    }

    override fun behandledeOppgaver(
        saksbehandlerFraApi: SaksbehandlerFraApi,
        offset: Int,
        limit: Int,
    ): BehandledeOppgaver {
        val saksbehandler = saksbehandlerFraApi.tilSaksbehandler()
        val behandledeOppgaver =
            oppgaveRepository.finnBehandledeOppgaver(
                behandletAvOid = saksbehandler.oid(),
                offset = offset,
                limit = limit,
            )
        return BehandledeOppgaver(
            oppgaver = behandledeOppgaver.tilBehandledeOppgaver(),
            totaltAntallOppgaver = if (behandledeOppgaver.isEmpty()) 0 else behandledeOppgaver.first().filtrertAntall,
        )
    }

    override fun hentEgenskaper(
        vedtaksperiodeId: UUID,
        utbetalingId: UUID,
    ): List<Oppgaveegenskap> {
        val egenskaper =
            oppgaveRepository.finnEgenskaper(
                vedtaksperiodeId = vedtaksperiodeId,
                utbetalingId = utbetalingId,
            )

        return egenskaper?.tilEgenskaperForVisning() ?: emptyList()
    }

    fun avbrytOppgaveFor(vedtaksperiodeId: UUID) {
        oppgaveRepository.finnIdForAktivOppgave(vedtaksperiodeId)?.also {
            oppgave(it) {
                this.avbryt()
            }
        }
    }

    fun fjernTilbakedatert(vedtaksperiodeId: UUID) {
        oppgaveRepository.finnIdForAktivOppgave(vedtaksperiodeId)?.also { oppgaveId ->
            oppgave(oppgaveId) {
                logg.info("Fjerner egenskap TILBAKEDATERT på {}", kv("oppgaveId", oppgaveId))
                fjernTilbakedatert()
            }
        }
    }

    fun fjernGosysEgenskap(vedtaksperiodeId: UUID) {
        oppgaveRepository.finnIdForAktivOppgave(vedtaksperiodeId)?.also { oppgaveId ->
            oppgave(oppgaveId) {
                logg.info("Fjerner egenskap GOSYS på {}", kv("oppgaveId", oppgaveId))
                fjernGosys()
            }
        }
    }

    fun leggTilGosysEgenskap(vedtaksperiodeId: UUID) {
        oppgaveRepository.finnIdForAktivOppgave(vedtaksperiodeId)?.also { oppgaveId ->
            oppgave(oppgaveId) {
                logg.info("Legger til egenskap GOSYS på {}", kv("oppgaveId", oppgaveId))
                leggTilGosys()
            }
        }
    }

    internal fun opprett(
        id: Long,
        contextId: UUID,
        vedtaksperiodeId: UUID,
        utbetalingId: UUID,
        egenskaper: List<EgenskapForDatabase>,
        hendelseId: UUID,
        kanAvvises: Boolean,
    ) {
        oppgaveRepository.opprettOppgave(id, contextId, egenskaper, vedtaksperiodeId, utbetalingId, kanAvvises)
        opptegnelseRepository.opprettOpptegnelse(
            oppgaveRepository.finnFødselsnummer(id),
            GodkjenningsbehovPayload(hendelseId),
            OpptegnelseType.NY_SAKSBEHANDLEROPPGAVE,
        )
    }

    fun oppdater(
        oppgaveId: Long,
        status: String,
        ferdigstiltAvIdent: String?,
        ferdigstiltAvOid: UUID?,
        egenskaper: List<EgenskapForDatabase>,
    ) {
        oppgaveRepository.updateOppgave(oppgaveId, status, ferdigstiltAvIdent, ferdigstiltAvOid, egenskaper)
    }

    fun reserverOppgave(
        saksbehandleroid: UUID,
        fødselsnummer: String,
    ) {
        try {
            reservasjonDao.reserverPerson(saksbehandleroid, fødselsnummer)
        } catch (e: SQLException) {
            logg.warn("Kunne ikke reservere person")
        }
    }

    private fun tildelVedReservasjon(
        fødselsnummer: String,
        oppgave: Oppgave,
    ) {
        val (saksbehandlerFraDatabase) =
            reservasjonDao.hentReservasjonFor(fødselsnummer) ?: run {
                logg.info("Finner ingen reservasjon for $oppgave, blir ikke tildelt.")
                return
            }
        val saksbehandler =
            Saksbehandler(
                epostadresse = saksbehandlerFraDatabase.epostadresse,
                oid = saksbehandlerFraDatabase.oid,
                navn = saksbehandlerFraDatabase.navn,
                ident = saksbehandlerFraDatabase.ident,
                tilgangskontroll = tilgangskontroll,
            )
        oppgave.forsøkTildelingVedReservasjon(saksbehandler)
    }

    fun harFerdigstiltOppgave(vedtaksperiodeId: UUID) = oppgaveRepository.harFerdigstiltOppgave(vedtaksperiodeId)

    private fun SaksbehandlerFraApi.tilSaksbehandler() =
        Saksbehandler(
            epostadresse = epost,
            oid = oid,
            navn = navn,
            ident = ident,
            tilgangskontroll = TilgangskontrollørForApi(grupper, tilgangsgrupper),
        )

    private fun List<Oppgavesortering>.tilOppgavesorteringForDatabase() =
        map {
            when (it.nokkel) {
                Sorteringsnokkel.TILDELT_TIL ->
                    OppgavesorteringForDatabase(
                        SorteringsnøkkelForDatabase.TILDELT_TIL,
                        it.stigende,
                    )

                Sorteringsnokkel.OPPRETTET ->
                    OppgavesorteringForDatabase(
                        SorteringsnøkkelForDatabase.OPPRETTET,
                        it.stigende,
                    )

                Sorteringsnokkel.SOKNAD_MOTTATT ->
                    OppgavesorteringForDatabase(
                        SorteringsnøkkelForDatabase.SØKNAD_MOTTATT,
                        it.stigende,
                    )

                Sorteringsnokkel.TIDSFRIST ->
                    OppgavesorteringForDatabase(
                        SorteringsnøkkelForDatabase.TIDSFRIST,
                        it.stigende,
                    )
            }
        }
}
