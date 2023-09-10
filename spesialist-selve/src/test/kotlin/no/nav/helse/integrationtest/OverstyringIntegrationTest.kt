package no.nav.helse.integrationtest

import AbstractE2ETest
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import java.util.UUID
import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.TestRapidHelpers.hendelser
import no.nav.helse.Testdata.AKTØR
import no.nav.helse.Testdata.FØDSELSNUMMER
import no.nav.helse.Testdata.ORGNR
import no.nav.helse.Testdata.ORGNR_GHOST
import no.nav.helse.Testdata.SAKSBEHANDLER_EPOST
import no.nav.helse.Testdata.SAKSBEHANDLER_IDENT
import no.nav.helse.Testdata.SAKSBEHANDLER_NAVN
import no.nav.helse.Testdata.SAKSBEHANDLER_OID
import no.nav.helse.januar
import no.nav.helse.mediator.api.AbstractApiTest
import no.nav.helse.mediator.api.AbstractApiTest.Companion.authentication
import no.nav.helse.spesialist.api.endepunkter.overstyringApi
import no.nav.helse.spesialist.api.saksbehandler.handlinger.OverstyrArbeidsforholdHandling
import no.nav.helse.spesialist.api.saksbehandler.handlinger.OverstyrTidslinjeHandling
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Tester samspillet mellom API og selve, altså "integrasjonen" mellom dem 😀
 */
internal class OverstyringIntegrationTest : AbstractE2ETest() {

    @Test
    fun `overstyr tidslinje`() {
        settOppBruker()
        assertOppgaver(1)
        val overstyring = OverstyrTidslinjeHandling(
            organisasjonsnummer = ORGNR,
            fødselsnummer = FØDSELSNUMMER,
            aktørId = AKTØR,
            begrunnelse = "en begrunnelse",
            dager = listOf(
                OverstyrTidslinjeHandling.OverstyrDagDto(dato = 10.januar, type = "Feriedag", fraType = "Sykedag", grad = null, fraGrad = 100)
            ),
        )
        val response = AbstractApiTest.TestServer { overstyringApi(saksbehandlerMediator) }
            .withAuthenticatedServer { client ->
                client.post("/api/overstyr/dager") {
                    header(HttpHeaders.ContentType, "application/json")
                    authentication(
                        oid = SAKSBEHANDLER_OID,
                        epost = SAKSBEHANDLER_EPOST,
                        navn = SAKSBEHANDLER_NAVN,
                        ident = SAKSBEHANDLER_IDENT,
                    )
                    setBody(objectMapper.writeValueAsString(overstyring))
                }
            }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(1, testRapid.inspektør.hendelser("saksbehandler_overstyrer_tidslinje").size)
        testRapid.sendTestMessage(
            testRapid.inspektør.hendelser("saksbehandler_overstyrer_tidslinje").first().toString()
        )
        assertEquals("Invalidert", oppgaveStatus())
        assertEquals(1, testRapid.inspektør.hendelser("overstyr_tidslinje").size)

    }

    @Test
    fun `overstyr inntekt med refusjon`() {
        settOppBruker()

        val json = """
                {
                    "fødselsnummer": $FØDSELSNUMMER,
                    "aktørId": $AKTØR,
                    "skjæringstidspunkt": "2018-01-01",
                    "arbeidsgivere": [{
                        "organisasjonsnummer": $ORGNR,
                        "månedligInntekt": 25000.0,
                        "fraMånedligInntekt": 25001.0,
                        "refusjonsopplysninger": [
                            {
                            "fom": "2018-01-01",
                            "tom": "2018-01-31",
                            "beløp": 25000.0
                            },
                            {
                            "fom": "2018-02-01",
                            "tom": null,
                            "beløp": 24000.0
                            }
                        ],                        
                        "fraRefusjonsopplysninger": [
                            {
                            "fom": "2018-01-01",
                            "tom": "2018-01-31",
                            "beløp": 24000.0
                            },
                            {
                            "fom": "2018-02-01",
                            "tom": null,
                            "beløp": 23000.0
                            }
                        ],
                        "begrunnelse": "en begrunnelse",
                        "forklaring": "en forklaring",
                        "subsumsjon": {
                            "paragraf": "8-28",
                            "ledd": "3",
                            "bokstav": null
                        }
                    },{
                        "organisasjonsnummer": "666",
                        "månedligInntekt": 21000.0,
                        "fraMånedligInntekt": 25001.0,
                        "refusjonsopplysninger": [
                            {
                            "fom": "2018-01-01",
                            "tom": "2018-01-31",
                            "beløp": 21000.0
                            },
                            {
                            "fom": "2018-02-01",
                            "tom": null,
                            "beløp": 22000.0
                            }
                        ],                        
                        "fraRefusjonsopplysninger": [
                            {
                            "fom": "2018-01-01",
                            "tom": "2018-01-31",
                            "beløp": 22000.0
                            },
                            {
                            "fom": "2018-02-01",
                            "tom": null,
                            "beløp": 23000.0
                            }
                        ],
                        "begrunnelse": "en begrunnelse 2",
                        "forklaring": "en forklaring 2",
                        "subsumsjon": {
                            "paragraf": "8-28",
                            "ledd": "3",
                            "bokstav": null
                        }
                    }]
                }
            """.trimIndent()

        val response = AbstractApiTest.TestServer { overstyringApi(saksbehandlerMediator) }
            .withAuthenticatedServer { client ->
                client.post("/api/overstyr/inntektogrefusjon") {
                    header(HttpHeaders.ContentType, "application/json")
                    authentication(
                        oid = SAKSBEHANDLER_OID,
                        epost = SAKSBEHANDLER_EPOST,
                        navn = SAKSBEHANDLER_NAVN,
                        ident = SAKSBEHANDLER_IDENT,
                    )
                    setBody(json)
                }
            }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(1, testRapid.inspektør.hendelser("saksbehandler_overstyrer_inntekt_og_refusjon").size)
        testRapid.sendTestMessage(
            testRapid.inspektør.hendelser("saksbehandler_overstyrer_inntekt_og_refusjon").first().toString()
        )

        //TODO: Bruk OverstyringApiDao når denne er oppdatert til å inkludere nye kolonner
        val refusjonsopplysninger = overstyringInntektRefusjonsopplysninger("refusjonsopplysninger")
        val fraRefusjonsopplysninger = overstyringInntektRefusjonsopplysninger("fra_refusjonsopplysninger")

        assertTrue(refusjonsopplysninger?.isNotEmpty() == true)
        assertTrue(fraRefusjonsopplysninger?.isNotEmpty() == true)

        assertEquals("Invalidert", oppgaveStatus())
        assertEquals(1, testRapid.inspektør.hendelser("overstyr_inntekt_og_refusjon").size)
    }

    @Test
    fun `skjønnsfastsetter sykepengegrunnlag`() {
        settOppBruker()

        val json = """
                {
                    "fødselsnummer": $FØDSELSNUMMER,
                    "aktørId": $AKTØR,
                    "skjæringstidspunkt": "2018-01-01",
                    "arbeidsgivere": [{
                        "organisasjonsnummer": $ORGNR,
                        "årlig": 250000.0,
                        "fraÅrlig": 250001.0,
                        "begrunnelseFritekst": "Begrunnelsefritekst",
                        "begrunnelseMal": "en begrunnelsemal",
                        "årsak": "en årsak",
                        "type": "OMREGNET_ÅRSINNTEKT",
                        "subsumsjon": {
                            "paragraf": "8-28",
                            "ledd": "3",
                            "bokstav": null
                        }
                    },{
                        "organisasjonsnummer": "666",
                        "årlig": 210000.0,
                        "fraÅrlig": 250001.0,
                        "begrunnelseFritekst": "Begrunnelsefritekst",
                        "begrunnelseMal": "en begrunnelsemal",
                        "årsak": "en årsak 2",
                        "type": "OMREGNET_ÅRSINNTEKT",
                        "subsumsjon": {
                            "paragraf": "8-28",
                            "ledd": "3",
                            "bokstav": null
                        },
                        "initierendeVedtaksperiodeId": "${UUID.randomUUID()}"
                    }]
                }
            """.trimIndent()

        val response = AbstractApiTest.TestServer { overstyringApi(saksbehandlerMediator) }
            .withAuthenticatedServer { client ->
                client.post("/api/skjonnsfastsett/sykepengegrunnlag") {
                    header(HttpHeaders.ContentType, "application/json")
                    authentication(
                        oid = SAKSBEHANDLER_OID,
                        epost = SAKSBEHANDLER_EPOST,
                        navn = SAKSBEHANDLER_NAVN,
                        ident = SAKSBEHANDLER_IDENT,
                    )
                    setBody(json)
                }
            }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(1, testRapid.inspektør.hendelser("saksbehandler_skjonnsfastsetter_sykepengegrunnlag").size)
        testRapid.sendTestMessage(
            testRapid.inspektør.hendelser("saksbehandler_skjonnsfastsetter_sykepengegrunnlag").first().toString()
        )

        assertEquals("Invalidert", oppgaveStatus())
        assertEquals(1, testRapid.inspektør.hendelser("skjønnsmessig_fastsettelse").size)
        assertEquals(1, testRapid.inspektør.hendelser("subsumsjon").size)
    }

    @Test
    fun `overstyr arbeidsforhold`() {
        settOppBruker(orgnummereMedRelevanteArbeidsforhold = listOf(ORGNR_GHOST))

        val overstyring = OverstyrArbeidsforholdHandling(
            fødselsnummer = FØDSELSNUMMER,
            aktørId = AKTØR,
            skjæringstidspunkt = 1.januar,
            overstyrteArbeidsforhold = listOf(
                OverstyrArbeidsforholdHandling.ArbeidsforholdDto(
                    orgnummer = ORGNR_GHOST,
                    deaktivert = true,
                    begrunnelse = "en begrunnelse",
                    forklaring = "en forklaring",
                )
            )
        )

        val response = AbstractApiTest.TestServer { overstyringApi(saksbehandlerMediator) }
            .withAuthenticatedServer { client ->
                client.post("/api/overstyr/arbeidsforhold") {
                    header(HttpHeaders.ContentType, "application/json")
                    authentication(
                        oid = SAKSBEHANDLER_OID,
                        epost = SAKSBEHANDLER_EPOST,
                        navn = SAKSBEHANDLER_NAVN,
                        ident = SAKSBEHANDLER_IDENT,
                    )
                    setBody(objectMapper.writeValueAsString(overstyring))
                }
            }


        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(1, testRapid.inspektør.hendelser("saksbehandler_overstyrer_arbeidsforhold").size)
        testRapid.sendTestMessage(
            testRapid.inspektør.hendelser("saksbehandler_overstyrer_arbeidsforhold").first().toString()
        )
        assertEquals("Invalidert", oppgaveStatus())
        assertEquals(1, testRapid.inspektør.hendelser("overstyr_arbeidsforhold").size)
    }

    private fun overstyringInntektRefusjonsopplysninger(column: String) =
        sessionOf(dataSource).use { session ->
            session.run(queryOf("SELECT * FROM overstyring_inntekt").map {
                it.stringOrNull(column)
            }.asSingle)
        }

    private fun oppgaveStatus() =
        sessionOf(dataSource).use { session ->
            session.run(queryOf("SELECT * FROM oppgave ORDER BY id DESC").map {
                it.string("status")
            }.asSingle)
        }
}
