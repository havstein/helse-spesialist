package no.nav.helse.spesialist.api.graphql.mutation

import no.nav.helse.spesialist.api.AbstractGraphQLApiTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

internal class NotatMutationTest : AbstractGraphQLApiTest() {

    @BeforeAll
    fun setup() {
        setupGraphQLServer()
    }

    @Test
    fun `feilregistrerer notat`() {
        opprettSaksbehandler()
        opprettVedtaksperiode()
        val notatId = opprettNotat()

        val query = queryize(
            """
            mutation FeilregistrerNotat {
                feilregistrerNotat(id: $notatId)
            }
        """
        )
        val body = runQuery(query)

        assertTrue(body["data"]["feilregistrerNotat"].asBoolean())
    }

    @Test
    fun `feilregistrerer kommentar`() {
        opprettSaksbehandler()
        opprettVedtaksperiode()
        val notatId = opprettNotat()!!
        val kommentarId = opprettKommentar(notatRef = notatId.toInt())

        val query = queryize(
            """
            mutation FeilregistrerKommentar {
                feilregistrerKommentar(id: $kommentarId)
            }
        """
        )
        val body = runQuery(query)

        assertTrue(body["data"]["feilregistrerKommentar"].asBoolean())
    }

    @Test
    fun `legger til notat`() {
        opprettSaksbehandler()
        opprettVedtaksperiode()

        val query = queryize(
            """
            mutation LeggTilNotat {
                leggTilNotat(
                    tekst: "Dette er et notat",
                    type: Generelt,
                    vedtaksperiodeId: "${PERIODE.id}",
                    saksbehandlerOid: "$SAKSBEHANDLER_OID"
                )
            }
        """
        )

        val antallNyeNotater = runQuery(query)["data"]["leggTilNotat"].asInt()

        assertEquals(1, antallNyeNotater)
    }

    @Test
    fun `legger til ny kommentar`() {
        opprettSaksbehandler()
        opprettVedtaksperiode()
        val notatId = opprettNotat()

        val query = queryize(
            """
            mutation LeggTilKommentar {
                leggTilKommentar(notatId: $notatId, tekst: "En kommentar", saksbehandlerident: "$SAKSBEHANDLER_IDENT") {
                    tekst
                }
            }
        """
        )
        val body = runQuery(query)

        assertEquals("En kommentar", body["data"]["leggTilKommentar"]["tekst"].asText())
    }

    @Test
    fun `får 404-feil ved oppretting av kommentar dersom notat ikke finnes`() {
        opprettSaksbehandler()
        opprettVedtaksperiode()

        val query = queryize(
            """
            mutation LeggTilKommentar {
                leggTilKommentar(notatId: 1, tekst: "En kommentar", saksbehandlerident: "$SAKSBEHANDLER_IDENT") {
                    id
                }
            }
        """
        )
        val body = runQuery(query)

        assertEquals(404, body["errors"].first()["extensions"]["code"].asInt())
    }

}