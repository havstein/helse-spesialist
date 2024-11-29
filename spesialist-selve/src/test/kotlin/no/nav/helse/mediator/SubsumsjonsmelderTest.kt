package no.nav.helse.mediator

import com.fasterxml.jackson.module.kotlin.convertValue
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDateTime
import no.nav.helse.TestRapidHelpers.meldinger
import no.nav.helse.modell.vilkårsprøving.SubsumsjonEvent
import no.nav.helse.objectMapper
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class SubsumsjonsmelderTest {

    private companion object {
        private const val FNR = "12345678910"
    }

    private val testRapid = TestRapid()
    private val subsumsjonsmelder = Subsumsjonsmelder("versjonAvKode", testRapid)

    @BeforeEach
    fun beforeEach() {
        testRapid.reset()
    }

    @Test
    fun `bygg kafkamelding`() {
        val id = UUID.randomUUID()
        val tidsstempel = LocalDateTime.now()

        val subsumsjonEvent = SubsumsjonEvent(
            id = id,
            fødselsnummer = FNR,
            paragraf = "EN PARAGRAF",
            ledd = "ET LEDD",
            bokstav = "EN BOKSTAV",
            lovverk = "folketrygdloven",
            lovverksversjon = "1970-01-01",
            utfall = "VILKAR_BEREGNET",
            input = mapOf("foo" to "bar"),
            output = mapOf("foo" to "bar"),
            sporing = mapOf("identifikator" to listOf("EN ID")),
            tidsstempel = tidsstempel,
            kilde = "KILDE",
        )

        subsumsjonsmelder.nySubsumsjon(FNR, subsumsjonEvent)

        val meldinger = testRapid.inspektør.meldinger()
        assertEquals(1, meldinger.size)
        val melding = meldinger.single()
        val subsumsjon = melding.path("subsumsjon")

        assertEquals("subsumsjon", melding["@event_name"].asText())
        assertNotNull(melding["@id"].asUUID())
        assertNotNull(melding["@opprettet"].asLocalDateTime())
        assertEquals(id, subsumsjon["id"].asUUID())
        assertEquals(FNR, subsumsjon["fodselsnummer"].asText())
        assertEquals("versjonAvKode", subsumsjon["versjonAvKode"].asText())
        assertEquals("1.0.0", subsumsjon["versjon"].asText())
        assertEquals("EN PARAGRAF", subsumsjon["paragraf"].asText())
        assertEquals("ET LEDD", subsumsjon["ledd"].asText())
        assertEquals("EN BOKSTAV", subsumsjon["bokstav"].asText())
        assertEquals("folketrygdloven", subsumsjon["lovverk"].asText())
        assertEquals("1970-01-01", subsumsjon["lovverksversjon"].asText())
        assertEquals("VILKAR_BEREGNET", subsumsjon["utfall"].asText())
        assertEquals(mapOf("foo" to "bar"), objectMapper.convertValue<Map<String, Any>>(subsumsjon["input"]))
        assertEquals(mapOf("foo" to "bar"), objectMapper.convertValue<Map<String, Any>>(subsumsjon["output"]))
        assertEquals(tidsstempel, subsumsjon["tidsstempel"].asLocalDateTime())
        assertEquals("KILDE", subsumsjon["kilde"].asText())
    }

    @Test
    fun `bygg kafkamelding uten ledd og bokstav`() {
        val id = UUID.randomUUID()
        val tidsstempel = LocalDateTime.now()

        val subsumsjonEvent = SubsumsjonEvent(
            id = id,
            fødselsnummer = FNR,
            paragraf = "EN PARAGRAF",
            ledd = null,
            bokstav = null,
            lovverk = "folketrygdloven",
            lovverksversjon = "1970-01-01",
            utfall = "VILKAR_BEREGNET",
            input = mapOf("foo" to "bar"),
            output = mapOf("foo" to "bar"),
            sporing = mapOf("identifikator" to listOf("EN ID")),
            tidsstempel = tidsstempel,
            kilde = "KILDE",
        )

        subsumsjonsmelder.nySubsumsjon(FNR, subsumsjonEvent)

        val meldinger = testRapid.inspektør.meldinger()
        assertEquals(1, meldinger.size)
        val melding = meldinger.single()
        val subsumsjon = melding.path("subsumsjon")

        assertEquals("subsumsjon", melding["@event_name"].asText())
        assertNotNull(melding["@id"].asUUID())
        assertNotNull(melding["@opprettet"].asLocalDateTime())
        assertEquals(id, subsumsjon["id"].asUUID())
        assertEquals(FNR, subsumsjon["fodselsnummer"].asText())
        assertEquals("versjonAvKode", subsumsjon["versjonAvKode"].asText())
        assertEquals("1.0.0", subsumsjon["versjon"].asText())
        assertEquals("EN PARAGRAF", subsumsjon["paragraf"].asText())
        assertNull(subsumsjon["ledd"])
        assertNull(subsumsjon["bokstav"])
        assertEquals("folketrygdloven", subsumsjon["lovverk"].asText())
        assertEquals("1970-01-01", subsumsjon["lovverksversjon"].asText())
        assertEquals("VILKAR_BEREGNET", subsumsjon["utfall"].asText())
        assertEquals(mapOf("foo" to "bar"), objectMapper.convertValue<Map<String, Any>>(subsumsjon["input"]))
        assertEquals(mapOf("foo" to "bar"), objectMapper.convertValue<Map<String, Any>>(subsumsjon["output"]))
        assertEquals(tidsstempel, subsumsjon["tidsstempel"].asLocalDateTime())
        assertEquals("KILDE", subsumsjon["kilde"].asText())
    }
}
