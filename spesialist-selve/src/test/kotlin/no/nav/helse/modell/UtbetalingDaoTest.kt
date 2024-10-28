package no.nav.helse.modell

import DatabaseIntegrationTest
import no.nav.helse.modell.utbetaling.Utbetaling
import no.nav.helse.modell.utbetaling.Utbetalingsstatus
import no.nav.helse.modell.utbetaling.Utbetalingsstatus.ANNULLERT
import no.nav.helse.modell.utbetaling.Utbetalingsstatus.UTBETALT
import no.nav.helse.modell.utbetaling.Utbetalingtype
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.time.LocalDateTime
import java.util.UUID
import kotlin.random.Random.Default.nextLong

class UtbetalingDaoTest : DatabaseIntegrationTest() {
    @Test
    fun `Ingen tidligere utbetalinger`() {
        nyPerson()

        assertFalse(utbetalingDao.erUtbetaltFør(AKTØR))
    }

    @Test
    fun `Er behandlet tidligere hvis hen har en tidligere utbetaling`() {
        nyPerson()

        val arbeidsgiveroppdragId = lagArbeidsgiveroppdrag(fagsystemId())
        val personOppdragId = lagPersonoppdrag(fagsystemId())
        val utbetaling_idId = lagUtbetalingId(arbeidsgiveroppdragId, personOppdragId, UUID.randomUUID())
        utbetalingDao.nyUtbetalingStatus(utbetaling_idId, UTBETALT, LocalDateTime.now(), "{}")

        assertTrue(utbetalingDao.erUtbetaltFør(AKTØR))
    }

    @Test
    fun `finner utbetaling`() {
        nyPerson()
        val arbeidsgiverFagsystemId = fagsystemId()
        val personFagsystemId = fagsystemId()

        val arbeidsgiveroppdragId1 = lagArbeidsgiveroppdrag(arbeidsgiverFagsystemId)
        val personOppdragId1 = lagPersonoppdrag(personFagsystemId)
        val utbetalingId = UUID.randomUUID()
        utbetalingDao.opprettUtbetalingId(
            utbetalingId,
            FNR,
            ORGNUMMER,
            Utbetalingtype.UTBETALING,
            LocalDateTime.now(),
            arbeidsgiveroppdragId1,
            personOppdragId1,
            2000,
            2000,
        )

        val utbetaling = utbetalingDao.utbetalingFor(utbetalingId)
        assertEquals(Utbetaling(utbetalingId, 2000, 2000, Utbetalingtype.UTBETALING), utbetaling)
    }

    @Test
    fun `finner utbetaling basert på oppgaveId`() {
        nyPerson()
        val arbeidsgiverFagsystemId = fagsystemId()
        val personFagsystemId = fagsystemId()

        val arbeidsgiveroppdragId1 = lagArbeidsgiveroppdrag(arbeidsgiverFagsystemId)
        val personOppdragId1 = lagPersonoppdrag(personFagsystemId)
        val utbetalingId = UUID.randomUUID()
        pgOppgaveDao.updateOppgave(oppgaveId = OPPGAVE_ID, oppgavestatus = "Ferdigstilt", egenskaper = listOf(EGENSKAP))
        val oppgaveId =
            pgOppgaveDao.opprettOppgave(
                nextLong(),
                UUID.randomUUID(),
                listOf(EGENSKAP),
                VEDTAKSPERIODE,
                utbetalingId,
                true,
            )
        utbetalingDao.opprettUtbetalingId(
            utbetalingId,
            FNR,
            ORGNUMMER,
            Utbetalingtype.UTBETALING,
            LocalDateTime.now(),
            arbeidsgiveroppdragId1,
            personOppdragId1,
            2000,
            2000,
        )

        val utbetaling = utbetalingDao.utbetalingFor(oppgaveId)
        assertEquals(Utbetaling(utbetalingId, 2000, 2000, Utbetalingtype.UTBETALING), utbetaling)
    }

    @Test
    fun `finner siste utbetaling for fødselsnummer`() {
        nyPerson()
        val arbeidsgiveroppdragId1 = lagArbeidsgiveroppdrag(fagsystemId())
        val arbeidsgiveroppdragId2 = lagArbeidsgiveroppdrag(fagsystemId())
        val personOppdragId1 = lagPersonoppdrag(fagsystemId())
        val personOppdragId2 = lagPersonoppdrag(fagsystemId())
        val utbetalingId = UUID.randomUUID()

        utbetalingDao.opprettUtbetalingId(
            UUID.randomUUID(),
            FNR,
            ORGNUMMER,
            Utbetalingtype.UTBETALING,
            LocalDateTime.now(),
            arbeidsgiveroppdragId1,
            personOppdragId1,
            2000,
            2000,
        )
        utbetalingDao.opprettUtbetalingId(
            utbetalingId,
            FNR,
            ORGNUMMER,
            Utbetalingtype.UTBETALING,
            LocalDateTime.now(),
            arbeidsgiveroppdragId2,
            personOppdragId2,
            3000,
            3000,
        )
        val sisteUtbetalingId = utbetalingDao.sisteUtbetalingIdFor(FNR)

        assertEquals(utbetalingId, sisteUtbetalingId)
    }

    @Test
    fun `finner ikke utbetaling dersom det ikke finnes noen`() {
        val utbetaling = utbetalingDao.utbetalingFor(UUID.randomUUID())
        assertNull(utbetaling)
    }

    @Test
    fun `Er behandlet tidligere selvom utbetalingen har blitt annullert`() {
        nyPerson()

        val arbeidsgiveroppdragId = lagArbeidsgiveroppdrag(fagsystemId())
        val personOppdragId = lagPersonoppdrag(fagsystemId())
        val utbetaling_idId = lagUtbetalingId(arbeidsgiveroppdragId, personOppdragId, UUID.randomUUID())
        utbetalingDao.nyUtbetalingStatus(utbetaling_idId, UTBETALT, LocalDateTime.now(), "{}")
        utbetalingDao.nyUtbetalingStatus(utbetaling_idId, ANNULLERT, LocalDateTime.now(), "{}")

        assertTrue(utbetalingDao.erUtbetaltFør(AKTØR))
    }

    @Test
    fun `Er behandlet tidligere selvom det finnes tidligere annulleringer`() {
        nyPerson()

        val arbeidsgiveroppdragId = lagArbeidsgiveroppdrag(fagsystemId())
        val personOppdragId = lagPersonoppdrag(fagsystemId())
        val utbetaling_idId = lagUtbetalingId(arbeidsgiveroppdragId, personOppdragId, UUID.randomUUID())
        utbetalingDao.nyUtbetalingStatus(utbetaling_idId, UTBETALT, LocalDateTime.now(), "{}")
        utbetalingDao.nyUtbetalingStatus(utbetaling_idId, ANNULLERT, LocalDateTime.now(), "{}")

        val arbeidsgiveroppdragId2 = lagArbeidsgiveroppdrag(fagsystemId())
        val personOppdragId2 = lagPersonoppdrag(fagsystemId())
        val utbetaling_idId2 = lagUtbetalingId(arbeidsgiveroppdragId2, personOppdragId2, UUID.randomUUID())
        utbetalingDao.nyUtbetalingStatus(utbetaling_idId2, UTBETALT, LocalDateTime.now(), "{}")

        assertTrue(utbetalingDao.erUtbetaltFør(AKTØR))
    }

    @Test
    fun `lagrer personbeløp og arbeidsgiverbeløp på utbetaling`() {
        nyPerson()
        val arbeidsgiverFagsystemId = fagsystemId()
        val personFagsystemId = fagsystemId()

        val arbeidsgiveroppdragId1 = lagArbeidsgiveroppdrag(arbeidsgiverFagsystemId)
        val personOppdragId1 = lagPersonoppdrag(personFagsystemId)
        val utbetalingId = UUID.randomUUID()
        utbetalingDao.opprettUtbetalingId(
            utbetalingId,
            FNR,
            ORGNUMMER,
            Utbetalingtype.UTBETALING,
            LocalDateTime.now(),
            arbeidsgiveroppdragId1,
            personOppdragId1,
            2000,
            2000,
        )
        assertArbeidsgiverbeløp(2000, utbetalingId)
        assertPersonbeløp(2000, utbetalingId)
    }

    @Test
    fun `alle enumer finnes også i db`() {
        nyPerson()
        val arbeidsgiverFagsystemId = fagsystemId()
        val personFagsystemId = fagsystemId()
        val arbeidsgiverOppdragId = lagArbeidsgiveroppdrag(arbeidsgiverFagsystemId)
        val personOppdragId = lagPersonoppdrag(personFagsystemId)
        val utbetaling = lagUtbetalingId(arbeidsgiverOppdragId, personOppdragId)

        assertDoesNotThrow {
            Utbetalingsstatus.entries.forEach {
                utbetalingDao.nyUtbetalingStatus(utbetaling, it, LocalDateTime.now(), "{}")
            }
        }
    }

    private fun assertArbeidsgiverbeløp(
        beløp: Int,
        utbetalingId: UUID,
    ) {
        val arbeidsgiverbeløp =
            query(
                "SELECT arbeidsgiverbeløp FROM utbetaling_id WHERE utbetaling_id = :utbetalingId",
                "utbetalingId" to utbetalingId,
            ).single { it.intOrNull("arbeidsgiverbeløp") }
        assertEquals(beløp, arbeidsgiverbeløp)
    }

    private fun assertPersonbeløp(
        beløp: Int,
        utbetalingId: UUID,
    ) {
        val personbeløp =
            query(
                "SELECT personbeløp FROM utbetaling_id WHERE utbetaling_id = :utbetalingId",
                "utbetalingId" to utbetalingId,
            ).single { it.intOrNull("personbeløp") }
        assertEquals(beløp, personbeløp)
    }
}
