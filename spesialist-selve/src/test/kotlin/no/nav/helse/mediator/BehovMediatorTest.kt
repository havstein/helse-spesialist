package no.nav.helse.mediator

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.mediator.meldinger.Vedtaksperiodemelding
import no.nav.helse.modell.kommando.CommandContext
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

internal class BehovMediatorTest {
    private companion object {
        private const val FNR = "fødselsnummer"
        private val hendelseId = UUID.randomUUID()
        private val contextId = UUID.randomUUID()
        private val vedtaksperiodeId = UUID.randomUUID()
        private val objectMapper = jacksonObjectMapper()
    }

    private val testRapid: TestRapid = TestRapid()
    private val behovMediator: BehovMediator = BehovMediator()
    private lateinit var testmelding: Testmelding
    private lateinit var testContext: CommandContext

    @BeforeEach
    fun setupEach() {
        testRapid.reset()
        testmelding = Testmelding(hendelseId)
        testContext = CommandContext(contextId)
        testContext.nyObserver(behovMediator)
    }

    @Test
    fun `sender behov`() {
        val params = mapOf(
            "param 1" to 1,
            "param 2" to 2
        )
        testContext.behov("type 1", params)
        behovMediator.håndter(testmelding, testRapid)
        assertEquals(listOf("type 1"), testRapid.inspektør.field(0, "@behov").map(JsonNode::asText))
        assertEquals(contextId.toString(), testRapid.inspektør.field(0, "contextId").asText())
        assertEquals(hendelseId.toString(), testRapid.inspektør.field(0, "hendelseId").asText())
        testRapid.inspektør.field(0, "type 1").also {
            assertEquals(1, it.path("param 1").asInt())
            assertEquals(2, it.path("param 2").asInt())
        }
    }

    @Test
    fun `sender meldinger`() {
        val melding1 = """{ "a_key": "with_a_value" }"""
        val melding2 = """{ "a_key": "with_a_value" }"""
        testContext.publiser(melding1)
        testContext.publiser(melding2)
        behovMediator.håndter(testmelding, testRapid)
        assertEquals(2, testRapid.inspektør.size)
        assertEquals(objectMapper.readTree(melding1), testRapid.inspektør.message(0))
        assertEquals(objectMapper.readTree(melding2), testRapid.inspektør.message(1))
    }

    @Test
    fun standardfelter() {
        testContext.behov("testbehov")
        behovMediator.håndter(testmelding, testRapid)
        assertEquals("behov", testRapid.inspektør.field(0, "@event_name").asText())
        assertEquals(FNR, testRapid.inspektør.field(0, "fødselsnummer").asText())
        assertDoesNotThrow { UUID.fromString(testRapid.inspektør.field(0, "@id").asText()) }
        assertDoesNotThrow { LocalDateTime.parse(testRapid.inspektør.field(0, "@opprettet").asText()) }
    }

    private inner class Testmelding(override val id: UUID) : Vedtaksperiodemelding {

        override fun fødselsnummer(): String {
            return FNR
        }

        override fun vedtaksperiodeId(): UUID {
            return vedtaksperiodeId
        }

        @Language("JSON")
        override fun toJson(): String {
            return """{ "@id": "${UUID.randomUUID()}", "@event_name": "testhendelse", "@opprettet": "${LocalDateTime.now()}" }"""
        }
    }
}
