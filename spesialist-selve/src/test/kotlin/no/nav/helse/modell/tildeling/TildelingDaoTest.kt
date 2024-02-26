package no.nav.helse.modell.tildeling

import DatabaseIntegrationTest
import java.util.UUID
import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.db.TildelingDao
import org.junit.jupiter.api.Assertions.assertEquals
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
    fun `henter saksbehandlerepost for tildeling med fødselsnummer`() {
        nyPerson()
        tildelTilSaksbehandler()
        val tildeling = tildelingDao.tildelingForPerson(FNR)
        assertEquals("navn@navnesen.no", tildeling?.epost)
    }

    @Test
    fun `henter bare tildelinger som har en aktiv oppgave`() {
        opprettPerson()
        opprettArbeidsgiver()
        opprettVedtaksperiode()
        opprettOppgave()
        oppgaveDao.updateOppgave(
            oppgaveId = oppgaveId,
            oppgavestatus = "Ferdigstilt",
            ferdigstiltAv = SAKSBEHANDLER_EPOST,
            oid = SAKSBEHANDLER_OID,
            egenskaper = listOf(EGENSKAP)
        )
        tildelTilSaksbehandler()
        val saksbehandlerepost = tildelingDao.tildelingForPerson(FNR)
        assertNull(saksbehandlerepost)
    }

    @Test
    fun `finn tildeling for oppgave`() {
        val oid = UUID.randomUUID()
        nyPerson()
        tildelTilSaksbehandler(oid = oid)
        val tildeling = tildelingDao.tildelingForOppgave(this.oppgaveId)!!
        assertEquals(oid, tildeling.oid)
        assertEquals("navn@navnesen.no", tildeling.epost)
        assertEquals("Navn Navnesen", tildeling.navn)
    }

    private fun nySaksbehandler(oid: UUID = UUID.randomUUID()) {
        saksbehandlerDao.opprettSaksbehandler(oid, "Navn Navnesen", "navn@navnesen.no", "Z999999")
    }

    private fun tildelTilSaksbehandler(oppgaveId: Long = this.oppgaveId, oid: UUID = SAKSBEHANDLER_OID) {
        nySaksbehandler(oid)
        nyDao.tildel(oppgaveId, oid, false)
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
