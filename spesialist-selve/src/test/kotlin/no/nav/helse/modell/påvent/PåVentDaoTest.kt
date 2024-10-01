package no.nav.helse.modell.påvent

import DatabaseIntegrationTest
import kotliquery.queryOf
import kotliquery.sessionOf
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

internal class PåVentDaoTest : DatabaseIntegrationTest() {
    @Test
    fun `lagre påvent`() {
        nyPerson()
        val frist = LocalDate.now().plusDays(21)
        påVentDao.lagrePåVent(OPPGAVE_ID, SAKSBEHANDLER_OID, frist)
        val påVent = påvent()
        assertEquals(1, påVent.size)
        påVent.first().assertEquals(VEDTAKSPERIODE, SAKSBEHANDLER_OID, frist)
    }

    @Test
    fun `slett påvent`() {
        nyPerson()
        val frist = LocalDate.now().plusDays(21)
        påVentDao.lagrePåVent(oppgaveId, SAKSBEHANDLER_OID, frist)
        val påVent = påvent()
        assertEquals(1, påVent.size)
        påVentDao.slettPåVent(oppgaveId)
        val påVentEtterSletting = påvent()
        assertEquals(0, påVentEtterSletting.size)
    }

    @Test
    fun `finnes påvent`() {
        nyPerson()
        val frist = LocalDate.now().plusDays(21)
        påVentDao.lagrePåVent(OPPGAVE_ID, SAKSBEHANDLER_OID, frist)
        val erPåVent = påVentDao.erPåVent(VEDTAKSPERIODE)
        assertTrue(erPåVent)
    }

    private fun påvent(vedtaksperiodeId: UUID = VEDTAKSPERIODE) =
        sessionOf(dataSource).use { session ->
            @Language("PostgreSQL")
            val statement = "SELECT * FROM pa_vent WHERE vedtaksperiode_id = :vedtaksperiodeId"
            session.run(
                queryOf(statement, mapOf("vedtaksperiodeId" to vedtaksperiodeId)).map { row ->
                    PåVent(
                        row.uuid("vedtaksperiode_id"),
                        row.uuid("saksbehandler_ref"),
                        row.localDateOrNull("frist"),
                        row.localDateTime("opprettet"),
                    )
                }.asList,
            )
        }

    private class PåVent(
        private val vedtaksperiodeId: UUID,
        private val saksbehandlerRef: UUID,
        private val frist: LocalDate?,
        private val opprettet: LocalDateTime,
    ) {
        fun assertEquals(
            forventetVedtaksperiodeId: UUID,
            forventetSaksbehandlerRef: UUID,
            forventetFrist: LocalDate?,
        ) {
            assertEquals(forventetVedtaksperiodeId, vedtaksperiodeId)
            assertEquals(forventetSaksbehandlerRef, saksbehandlerRef)
            assertEquals(forventetFrist, frist)
            assertNotNull(opprettet)
        }
    }
}
