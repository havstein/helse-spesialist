package no.nav.helse.spesialist.api.graphql.query

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.treeToValue
import io.mockk.every
import no.nav.helse.spesialist.api.AbstractGraphQLApiTest
import no.nav.helse.spesialist.api.graphql.schema.ApiOpptegnelse
import no.nav.helse.spesialist.api.graphql.schema.ApiOpptegnelsetype.REVURDERING_FERDIGBEHANDLET
import no.nav.helse.spesialist.api.graphql.schema.ApiOpptegnelsetype.UTBETALING_ANNULLERING_FEILET
import no.nav.helse.spesialist.api.graphql.schema.ApiOpptegnelsetype.UTBETALING_ANNULLERING_OK
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class OpptegnelseQueryTest: AbstractGraphQLApiTest() {

    @Test
    fun `hent opptegnelser uten sekvensId`() {
        opprettPerson()
        val typer = listOf(
            UTBETALING_ANNULLERING_FEILET,
            UTBETALING_ANNULLERING_OK,
            REVURDERING_FERDIGBEHANDLET
        )
        abonner(AKTØRID)
        every { saksbehandlerhåndterer.hentAbonnerteOpptegnelser(any()) } returns typer.mapIndexed { idx, type -> ApiOpptegnelse(AKTØRID, idx + 1, type, "{}") }

        val body = runQuery(
            """query HentOpptegnelser {
                opptegnelser {
                    aktorId
                    sekvensnummer
                    type
                    payload
                }
            }"""
        )
        val opptegnelser = jacksonObjectMapper().treeToValue<List<ApiOpptegnelse>>(body["data"]["opptegnelser"])
        assertEquals(3, opptegnelser.size)
        opptegnelser.forEachIndexed { index, opptegnelse ->
            assertEquals(AKTØRID, opptegnelse.aktorId)
            assertEquals(index + 1, opptegnelse.sekvensnummer)
            assertEquals("""{}""", opptegnelse.payload)
            assertEquals(typer[index], opptegnelse.type)
        }
    }

    @Test
    fun `hent opptegnelser med sekvensId`() {
        opprettPerson()
        val typer = listOf(
            UTBETALING_ANNULLERING_FEILET,
            UTBETALING_ANNULLERING_OK,
            REVURDERING_FERDIGBEHANDLET
        )
        abonner(AKTØRID)
        every { saksbehandlerhåndterer.hentAbonnerteOpptegnelser(any(), any()) } returns listOf(ApiOpptegnelse(AKTØRID, 3, typer[2], "{}"))
        val body = runQuery(
            """query HentOpptegnelser {
                opptegnelser(sekvensId: 2) {
                    aktorId
                    sekvensnummer
                    type
                    payload
                }
            }"""
        )

        val opptegnelser = jacksonObjectMapper().treeToValue<List<ApiOpptegnelse>>(body["data"]["opptegnelser"])
        assertEquals(1, opptegnelser.size)
        val opptegnelse = opptegnelser.single()
        assertEquals(AKTØRID, opptegnelse.aktorId)
        assertEquals(3, opptegnelse.sekvensnummer)
        assertEquals("""{}""", opptegnelse.payload)
        assertEquals(typer[2], opptegnelse.type)
    }

    private fun abonner(personId: String) {
        runQuery(
            """mutation Abonner {
                opprettAbonnement(personidentifikator: "$personId")
            }"""
        )
    }
}
