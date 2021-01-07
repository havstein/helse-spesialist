package no.nav.helse.mediator

import no.nav.helse.mediator.meldinger.Hendelse
import no.nav.helse.modell.Oppgave
import no.nav.helse.modell.OppgaveDao
import no.nav.helse.modell.Oppgavestatus
import no.nav.helse.modell.Oppgavestatus.AvventerSaksbehandler
import no.nav.helse.modell.VedtakDao
import no.nav.helse.modell.tildeling.ReservasjonDao
import no.nav.helse.modell.tildeling.TildelingDao
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import java.time.LocalDateTime
import java.util.*

internal class OppgaveMediator(
    private val oppgaveDao: OppgaveDao,
    private val vedtakDao: VedtakDao,
    private val tildelingDao: TildelingDao,
    private val reservasjonDao: ReservasjonDao
) {
    private val oppgaver = mutableSetOf<Oppgave>()
    private val meldinger = mutableListOf<String>()

    internal fun hentOppgaver() = oppgaveDao.finnOppgaver()

    internal fun hentOppgaveId(fødselsnummer: String) = oppgaveDao.finnOppgaveId(fødselsnummer)

    internal fun opprett(oppgave: Oppgave) {
        nyOppgave(oppgave)
    }

    internal fun tildel(oppgaveId: Long, saksbehandleroid: UUID, gyldigTil: LocalDateTime) {
        tildelingDao.opprettTildeling(oppgaveId, saksbehandleroid, gyldigTil)
    }

    private fun nyOppgave(oppgave: Oppgave) {
        oppgaver.add(oppgave)
    }

    internal fun ferdigstill(oppgave: Oppgave, saksbehandlerIdent: String, oid: UUID) {
        oppgave.ferdigstill(saksbehandlerIdent, oid)
        nyOppgave(oppgave)
    }

    private fun avbryt(oppgave: Oppgave) {
        oppgave.avbryt()
        nyOppgave(oppgave)
    }

    private fun avventerSystem(oppgave: Oppgave, saksbehandlerIdent: String, oid: UUID) {
        oppgave.avventerSystem(saksbehandlerIdent, oid)
        nyOppgave(oppgave)
    }

    internal fun lagreOgTildelOppgaver(hendelse: Hendelse, messageContext: RapidsConnection.MessageContext, contextId: UUID) {
        lagreOppgaver(hendelse.id, contextId, { messageContext.send(it) }) { tildelOppgaver(hendelse.fødselsnummer()) }
    }

    internal fun lagreOppgaver(rapidsConnection: RapidsConnection, hendelseId: UUID, contextId: UUID) {
        lagreOppgaver(hendelseId, contextId, { rapidsConnection.publish(it) })
    }

    internal fun avbrytOppgaver(fødselsnummer: String) {
        oppgaveDao.finn(fødselsnummer).forEach(::avbryt)
    }

    internal fun avbrytOppgaver(vedtaksperiodeId: UUID) {
        oppgaveDao.finn(vedtaksperiodeId).forEach(::avbryt)
    }

    internal fun avventerSystem(oppgaveId: Long, saksbehandlerIdent: String, oid: UUID) {
        val oppgave = oppgaveDao.finn(oppgaveId) ?: return
        avventerSystem(oppgave, saksbehandlerIdent, oid)
    }

    internal fun opprett(
        hendelseId: UUID,
        contextId: UUID,
        vedtaksperiodeId: UUID,
        navn: String
    ): Long? {
        if (oppgaveDao.harAktivOppgave(vedtaksperiodeId)) return null

        val vedtakRef = requireNotNull(vedtakDao.findVedtak(vedtaksperiodeId)?.id)
        return oppgaveDao.opprettOppgave(
            contextId,
            navn,
            vedtakRef
        ).also { oppgaveId ->
            køMelding("oppgave_opprettet", hendelseId, contextId, oppgaveId, AvventerSaksbehandler)
        }
    }

    internal fun oppdater(
        hendelseId: UUID,
        contextId: UUID,
        oppgaveId: Long,
        status: Oppgavestatus,
        ferdigstiltAvIdent: String?,
        ferdigstiltAvOid: UUID?
    ) {
        oppgaveDao.updateOppgave(oppgaveId, status, ferdigstiltAvIdent, ferdigstiltAvOid)
        køMelding(
            "oppgave_oppdatert",
            hendelseId,
            contextId,
            oppgaveId,
            status,
            ferdigstiltAvIdent,
            ferdigstiltAvOid
        )
    }

    private fun tildelOppgaver(fødselsnummer: String) {
        reservasjonDao.hentReservasjonFor(fødselsnummer)?.let { (oid, gyldigTil) ->
            oppgaver.forEach { it.tildel(this, oid, gyldigTil) }
        }
    }

    private fun lagreOppgaver(hendelseId: UUID, contextId: UUID, publisher: (String) -> Unit, doAlso: () -> Unit = {}) {
        oppgaver.onEach { oppgave -> oppgave.lagre(this, hendelseId, contextId) }
        doAlso()
        oppgaver.clear()
        meldinger.onEach { publisher(it) }.clear()
    }

    private fun køMelding(
        eventNavn: String,
        hendelseId: UUID,
        contextId: UUID,
        oppgaveId: Long,
        status: Oppgavestatus,
        ferdigstiltAvIdent: String? = null,
        ferdigstiltAvOid: UUID? = null
    ) {
        meldinger.add(JsonMessage.newMessage(
            mutableMapOf(
                "@event_name" to eventNavn,
                "@id" to UUID.randomUUID(),
                "@opprettet" to LocalDateTime.now(),
                "hendelseId" to hendelseId,
                "contextId" to contextId,
                "oppgaveId" to oppgaveId,
                "status" to status.name
            ).apply {
                ferdigstiltAvIdent?.also { put("ferdigstiltAvIdent", it) }
                ferdigstiltAvOid?.also { put("ferdigstiltAvOid", it) }
            }
        ).toJson())
    }
}
