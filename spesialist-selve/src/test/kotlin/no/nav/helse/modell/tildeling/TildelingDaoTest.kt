package no.nav.helse.modell.tildeling

import DatabaseIntegrationTest
import java.util.UUID
import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.db.TildelingDao
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class TildelingDaoTest : DatabaseIntegrationTest() {

    private val nyDao = TildelingDao(dataSource)

    @Test
    fun tildel() {
        val oid = UUID.randomUUID()
        nyPerson()
        nySaksbehandler(oid)
        nyDao.tildel(OPPGAVE_ID, oid, false)
        assertTildeling(OPPGAVE_ID, oid)
    }

    @Test
    fun `tildel selv om tildeling allerede eksisterer`() {
        val oid = UUID.randomUUID()
        val annenOid = UUID.randomUUID()
        nyPerson()
        nySaksbehandler(oid)
        nySaksbehandler(annenOid)
        nyDao.tildel(OPPGAVE_ID, oid, false)
        nyDao.tildel(OPPGAVE_ID, annenOid, false)
        assertTildeling(OPPGAVE_ID, annenOid)
    }

    @Test
    fun avmeld() {
        val oid = UUID.randomUUID()
        nyPerson()
        nySaksbehandler(oid)
        nyDao.tildel(OPPGAVE_ID, oid, false)
        nyDao.avmeld(OPPGAVE_ID)
        assertTildeling(OPPGAVE_ID, null)
    }

    @Test
    fun `oppretter tildeling`() {
        nyPerson()
        val saksbehandleroid = UUID.randomUUID()
        saksbehandlerDao.opprettSaksbehandler(saksbehandleroid, "Navn Navnesen", "navn@navnesen.no", "Z999999")
        tildelingDao.opprettTildeling(oppgaveId, saksbehandleroid)
        assertTildeling(oppgaveId, saksbehandleroid)
    }

    @Test
    fun `en saksbehandler kan ikke tildele seg en oppgave som allerede er tildelt`() {
        nyPerson()
        val saksbehandlerOid1 = UUID.randomUUID()
        val saksbehandlerOid2 = UUID.randomUUID()
        saksbehandlerDao.opprettSaksbehandler(saksbehandlerOid1, "A", "a@nav.no", "A999999")
        saksbehandlerDao.opprettSaksbehandler(saksbehandlerOid2, "B", "b@nav.no", "A999999")

        tildelingDao.opprettTildeling(oppgaveId, saksbehandlerOid1)
        val tildelingNrToSuksess = tildelingDao.opprettTildeling(oppgaveId, saksbehandlerOid2) != null

        assertFalse(tildelingNrToSuksess)
        assertTildeling(oppgaveId, saksbehandlerOid1)
    }

    @Test
    fun `kan ikke tildele oppgave når gyldig_til ikke har gått ut`() {
        nyPerson()
        val saksbehandlerOid = UUID.randomUUID()
        saksbehandlerDao.opprettSaksbehandler(saksbehandlerOid, "A", "a@nav.no", "A999999")

        tildelingDao.opprettTildeling(oppgaveId, saksbehandlerOid)

        val tildelingNrToSuksess = tildelingDao.opprettTildeling(oppgaveId, saksbehandlerOid) != null

        assertFalse(tildelingNrToSuksess)
    }

    @Test
    fun `henter saksbehandlerepost for tildeling med fødselsnummer`() {
        nyPerson()
        tildelTilSaksbehandler()
        val tildeling = tildelingDao.tildelingForPerson(FNR)
        assertEquals(SAKSBEHANDLEREPOST, tildeling?.epost)
    }

    @Test
    fun `slett tildeling`() {
        nyPerson()
        saksbehandlerDao.opprettSaksbehandler(SAKSBEHANDLER_OID, "Navn Navnesen", SAKSBEHANDLEREPOST, SAKSBEHANDLER_IDENT)
        tildelingDao.opprettTildeling(oppgaveId, SAKSBEHANDLER_OID)
        assertTildeling(oppgaveId, SAKSBEHANDLER_OID)
        tildelingDao.slettTildeling(oppgaveId)
        assertTildeling(oppgaveId, null)
    }

    @Test
    fun `henter bare tildelinger som har en aktiv oppgave`() {
        opprettPerson()
        opprettArbeidsgiver()
        opprettVedtaksperiode()
        opprettOppgave()
        oppgaveDao.updateOppgave(oppgaveId, "Ferdigstilt", SAKSBEHANDLEREPOST, SAKSBEHANDLER_OID)
        tildelTilSaksbehandler()
        val saksbehandlerepost = tildelingDao.tildelingForPerson(FNR)
        assertNull(saksbehandlerepost)
    }

    @Test
    fun `finn tildeling for oppgave`() {
        nyPerson()
        tildelTilSaksbehandler()
        val tildeling = tildelingDao.tildelingForOppgave(this.oppgaveId)!!
        assertEquals(SAKSBEHANDLER_OID, tildeling.oid)
        assertEquals(SAKSBEHANDLEREPOST, tildeling.epost)
        assertEquals(SAKSBEHANDLER_NAVN, tildeling.navn)
        assertEquals(false, tildeling.påVent)
    }

    private fun nySaksbehandler(oid: UUID = UUID.randomUUID()) {
        saksbehandlerDao.opprettSaksbehandler(oid, "Navn Navnesen", "navn@navnesen.no", "Z999999")
    }

    private fun tildelTilSaksbehandler(
        oppgaveId: Long = this.oppgaveId,
        oid: UUID = SAKSBEHANDLER_OID,
        navn: String = SAKSBEHANDLER_NAVN,
        epost: String = SAKSBEHANDLEREPOST,
        ident: String = SAKSBEHANDLER_IDENT
    ) {
        saksbehandlerDao.opprettSaksbehandler(oid, navn, epost, ident)
        tildelingDao.opprettTildeling(oppgaveId, oid)
    }

    private fun assertTildeling(oppgaveId: Long, saksbehandleroid: UUID?) {
        val result = sessionOf(dataSource).use { session ->
            session.run(
                queryOf("SELECT saksbehandler_ref FROM tildeling WHERE oppgave_id_ref = ?", oppgaveId)
                    .map { UUID.fromString(it.string("saksbehandler_ref")) }.asSingle
            )
        }
        assertEquals(saksbehandleroid, result)
    }
}
