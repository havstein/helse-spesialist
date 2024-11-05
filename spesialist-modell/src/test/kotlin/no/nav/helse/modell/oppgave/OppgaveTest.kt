package no.nav.helse.modell.oppgave

import no.nav.helse.modell.ManglerTilgang
import no.nav.helse.modell.OppgaveAlleredeSendtBeslutter
import no.nav.helse.modell.OppgaveAlleredeSendtIRetur
import no.nav.helse.modell.OppgaveIkkeTildelt
import no.nav.helse.modell.OppgaveKreverVurderingAvToSaksbehandlere
import no.nav.helse.modell.OppgaveTildeltNoenAndre
import no.nav.helse.modell.oppgave.Egenskap.FORTROLIG_ADRESSE
import no.nav.helse.modell.oppgave.Egenskap.PÅ_VENT
import no.nav.helse.modell.oppgave.Egenskap.STIKKPRØVE
import no.nav.helse.modell.oppgave.Egenskap.STRENGT_FORTROLIG_ADRESSE
import no.nav.helse.modell.oppgave.Egenskap.SØKNAD
import no.nav.helse.modell.oppgave.Oppgave.Companion.gjenopprett
import no.nav.helse.modell.oppgave.Oppgave.Companion.toDto
import no.nav.helse.modell.oppgave.OppgaveInspektør.Companion.inspektør
import no.nav.helse.modell.saksbehandler.Saksbehandler
import no.nav.helse.modell.saksbehandler.Saksbehandler.Companion.toDto
import no.nav.helse.modell.saksbehandler.Tilgangskontroll
import no.nav.helse.modell.saksbehandler.handlinger.TilgangskontrollForTestHarIkkeTilgang
import no.nav.helse.modell.saksbehandler.handlinger.TilgangskontrollForTestHarTilgang
import no.nav.helse.modell.saksbehandler.handlinger.TilgangskontrollForTestMedKunFortroligAdresse
import no.nav.helse.modell.totrinnsvurdering.Totrinnsvurdering
import no.nav.helse.modell.totrinnsvurdering.TotrinnsvurderingDto
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.time.LocalDateTime
import java.util.UUID
import kotlin.random.Random.Default.nextLong

internal class OppgaveTest {
    private companion object {
        private val OPPGAVETYPE = SØKNAD
        private val VEDTAKSPERIODE_ID = UUID.randomUUID()
        private val UTBETALING_ID = UUID.randomUUID()
        private const val SAKSBEHANDLER_IDENT = "Z999999"
        private const val SAKSBEHANDLER_EPOST = "saksbehandler@nav.no"
        private const val SAKSBEHANDLER_NAVN = "Hen Saksbehandler"
        private val SAKSBEHANDLER_OID = UUID.randomUUID()
        private val BESLUTTER_OID = UUID.randomUUID()
        private val OPPGAVE_ID = nextLong()
        private val saksbehandlerUtenTilgang = saksbehandler()
        private val beslutter = saksbehandler(oid = BESLUTTER_OID, tilgangskontroll = TilgangskontrollForTestHarTilgang)

        private fun saksbehandler(
            epost: String = SAKSBEHANDLER_EPOST,
            oid: UUID = SAKSBEHANDLER_OID,
            navn: String = SAKSBEHANDLER_NAVN,
            ident: String = SAKSBEHANDLER_IDENT,
            tilgangskontroll: Tilgangskontroll = TilgangskontrollForTestHarIkkeTilgang,
        ) = Saksbehandler(
            epostadresse = epost,
            oid = oid,
            navn = navn,
            ident = ident,
            tilgangskontroll = tilgangskontroll,
        )
    }

    @Test
    fun `Forsøker tildeling ved reservasjon`() {
        val oppgave = nyOppgave(SØKNAD)
        oppgave.forsøkTildelingVedReservasjon(saksbehandlerUtenTilgang)

        inspektør(oppgave) {
            assertEquals(true, tildelt)
            assertEquals(false, påVent)
            assertEquals(saksbehandlerUtenTilgang.toDto(), tildeltTil)
        }
    }

    @Test
    fun `Kan tildele ved reservasjon dersom saksbehandler har tilgang til alle tilgangsstyrte egenskaper på oppgaven`() {
        val oppgave = nyOppgave(SØKNAD, STRENGT_FORTROLIG_ADRESSE, FORTROLIG_ADRESSE)
        val saksbehandler = saksbehandler(tilgangskontroll = TilgangskontrollForTestHarTilgang)
        oppgave.forsøkTildelingVedReservasjon(saksbehandler)

        inspektør(oppgave) {
            assertEquals(true, tildelt)
            assertEquals(saksbehandler.toDto(), tildeltTil)
        }
    }

    @Test
    fun `Kan ikke tildele ved reservasjon dersom saksbehandler ikke har tilgang til alle tilgangsstyrte egenskaper på oppgaven`() {
        val oppgave = nyOppgave(SØKNAD, FORTROLIG_ADRESSE, STRENGT_FORTROLIG_ADRESSE)
        assertThrows<ManglerTilgang> {
            oppgave.forsøkTildelingVedReservasjon(saksbehandler(tilgangskontroll = TilgangskontrollForTestMedKunFortroligAdresse))
        }

        inspektør(oppgave) {
            assertEquals(false, tildelt)
            assertNull(tildeltTil)
        }
    }

    @Test
    fun `Forsøker tildeling ved reservasjon med påVent`() {
        val oppgave = nyOppgave(PÅ_VENT, SØKNAD)
        oppgave.forsøkTildelingVedReservasjon(saksbehandlerUtenTilgang)

        inspektør(oppgave) {
            assertEquals(true, tildelt)
            assertEquals(true, påVent)
            assertEquals(saksbehandlerUtenTilgang.toDto(), tildeltTil)
        }
    }

    @Test
    fun `Forsøker tildeling ved reservasjon ved stikkprøve`() {
        val oppgave = nyOppgave(PÅ_VENT, STIKKPRØVE)
        oppgave.forsøkTildelingVedReservasjon(saksbehandlerUtenTilgang)

        inspektør(oppgave) {
            assertEquals(false, tildelt)
            assertEquals(null, tildeltTil)
        }
    }

    @Test
    fun `Forsøk avmelding av oppgave`() {
        val oppgave = nyOppgave()
        oppgave.forsøkTildeling(saksbehandlerUtenTilgang)
        oppgave.forsøkAvmelding(saksbehandlerUtenTilgang)

        inspektør(oppgave) {
            assertEquals(false, tildelt)
            assertEquals(false, påVent)
            assertEquals(null, tildeltTil)
        }
    }

    @Test
    fun `Forsøk avmelding av oppgave når oppgaven er tildelt noen andre`() {
        val oppgave = nyOppgave()
        oppgave.forsøkTildeling(saksbehandler(oid = UUID.randomUUID()))
        oppgave.forsøkAvmelding(saksbehandlerUtenTilgang)

        inspektør(oppgave) {
            assertEquals(false, tildelt)
            assertEquals(false, påVent)
            assertEquals(null, tildeltTil)
        }
    }

    @Test
    fun `Forsøk avmelding av oppgave når oppgaven ikke er tildelt`() {
        val oppgave = nyOppgave()
        assertThrows<OppgaveIkkeTildelt> {
            oppgave.forsøkAvmelding(saksbehandlerUtenTilgang)
        }
    }

    @ParameterizedTest
    @EnumSource(names = ["EGEN_ANSATT", "FORTROLIG_ADRESSE", "BESLUTTER", "SPESIALSAK", "STRENGT_FORTROLIG_ADRESSE"])
    fun `Forsøker tildeling ved reservasjon ved manglende tilgang`(egenskap: Egenskap) {
        val oppgave = nyOppgave(egenskap)
        assertThrows<ManglerTilgang> {
            oppgave.forsøkTildelingVedReservasjon(saksbehandlerUtenTilgang)
        }

        inspektør(oppgave) {
            assertEquals(false, tildelt)
            assertEquals(null, tildeltTil)
        }
    }

    @ParameterizedTest
    @EnumSource(names = ["EGEN_ANSATT", "FORTROLIG_ADRESSE", "BESLUTTER", "SPESIALSAK", "STRENGT_FORTROLIG_ADRESSE", "STIKKPRØVE"])
    fun `Forsøker tildeling ved manglende tilgang`(egenskap: Egenskap) {
        val oppgave = nyOppgave(egenskap)
        assertThrows<ManglerTilgang> {
            oppgave.forsøkTildeling(saksbehandlerUtenTilgang)
        }

        inspektør(oppgave) {
            assertEquals(false, tildelt)
            assertEquals(null, tildeltTil)
        }
    }

    @ParameterizedTest
    @EnumSource(names = ["EGEN_ANSATT", "FORTROLIG_ADRESSE", "BESLUTTER", "SPESIALSAK", "STRENGT_FORTROLIG_ADRESSE", "STIKKPRØVE"])
    fun `Forsøker tildeling ved tilgang`(egenskap: Egenskap) {
        val oppgave = nyOppgave(egenskap)
        val saksbehandlerMedTilgang = saksbehandler(tilgangskontroll = TilgangskontrollForTestHarTilgang)
        oppgave.forsøkTildeling(saksbehandlerMedTilgang)

        inspektør(oppgave) {
            assertEquals(true, tildelt)
            assertEquals(false, påVent)
            assertEquals(saksbehandlerMedTilgang.toDto(), tildeltTil)
        }
    }

    @Test
    fun `Forsøker tildeling når oppgaven er tildelt noen andre`() {
        val oppgave = nyOppgave()
        oppgave.forsøkTildeling(saksbehandlerUtenTilgang)
        assertThrows<OppgaveTildeltNoenAndre> {
            oppgave.forsøkTildeling(saksbehandler(oid = UUID.randomUUID()))
        }

        inspektør(oppgave) {
            assertEquals(true, tildelt)
            assertEquals(false, påVent)
            assertEquals(saksbehandlerUtenTilgang.toDto(), tildeltTil)
        }
    }

    @Test
    fun `Setter oppgavestatus til AvventerSystem når oppgaven avventer system`() {
        val oppgave = nyOppgave(SØKNAD)
        oppgave.avventerSystem(SAKSBEHANDLER_IDENT, SAKSBEHANDLER_OID)

        inspektør(oppgave) {
            assertEquals(OppgaveDto.TilstandDto.AvventerSystem, tilstand)
        }
    }

    @Test
    fun `Setter oppgavestatus til Invalidert når oppgaven avbrytes`() {
        val oppgave = nyOppgave(SØKNAD)
        oppgave.avbryt()

        inspektør(oppgave) {
            assertEquals(OppgaveDto.TilstandDto.Invalidert, tilstand)
        }
    }

    @Test
    fun `Setter oppgavestatus til Ferdigstilt når oppgaven ferdigstilles`() {
        val oppgave = nyOppgave(SØKNAD)
        oppgave.avventerSystem(SAKSBEHANDLER_IDENT, SAKSBEHANDLER_OID)
        oppgave.ferdigstill()

        inspektør(oppgave) {
            assertEquals(OppgaveDto.TilstandDto.Ferdigstilt, tilstand)
        }
    }

    @Test
    fun `kaster exception dersom oppgave allerede er sendt til beslutter når man forsøker å sende til beslutter`() {
        val oppgave = nyOppgave(SØKNAD, medTotrinnsvurdering = true)
        oppgave.sendTilBeslutter(saksbehandlerUtenTilgang)
        assertThrows<OppgaveAlleredeSendtBeslutter> {
            oppgave.sendTilBeslutter(saksbehandlerUtenTilgang)
        }
    }

    @Test
    fun `kaster exception dersom oppgave sendes til beslutter uten at oppgaven krever totrinnsvurdering`() {
        val oppgave = nyOppgave(SØKNAD, medTotrinnsvurdering = false)
        assertThrows<IllegalArgumentException> {
            oppgave.sendTilBeslutter(saksbehandlerUtenTilgang)
        }
    }

    @Test
    fun `oppgave sendt til beslutter tildeles ingen dersom det ikke finnes noen tidligere beslutter`() {
        val oppgave = nyOppgave(SØKNAD, medTotrinnsvurdering = true)
        oppgave.forsøkTildelingVedReservasjon(saksbehandlerUtenTilgang)
        oppgave.sendTilBeslutter(saksbehandlerUtenTilgang)
        inspektør(oppgave) {
            assertEquals(null, tildeltTil)
        }
    }

    @Test
    fun `oppgave sendt til beslutter får egenskap BESLUTTER`() {
        val oppgave = nyOppgave(SØKNAD, medTotrinnsvurdering = true)
        oppgave.sendTilBeslutter(saksbehandlerUtenTilgang)
        inspektør(oppgave) {
            assertTrue(egenskaper.contains(EgenskapDto.BESLUTTER))
        }
    }

    @Test
    fun `oppgave sendt i retur får egenskap RETUR`() {
        val oppgave = nyOppgave(SØKNAD, medTotrinnsvurdering = true)
        oppgave.sendTilBeslutter(saksbehandlerUtenTilgang)
        oppgave.sendIRetur(beslutter)
        inspektør(oppgave) {
            assertTrue(egenskaper.contains(EgenskapDto.RETUR))
            assertFalse(egenskaper.contains(EgenskapDto.BESLUTTER))
        }
    }

    @Test
    fun `oppgave sendt i retur ligger ikke lenger på vent`() {
        val oppgave = nyOppgave(SØKNAD, medTotrinnsvurdering = true)
        oppgave.forsøkTildelingVedReservasjon(saksbehandlerUtenTilgang)
        oppgave.sendTilBeslutter(saksbehandlerUtenTilgang)
        oppgave.forsøkTildelingVedReservasjon(beslutter)
        oppgave.sendIRetur(beslutter)
        inspektør(oppgave) {
            assertFalse(påVent)
        }
    }

    @Test
    fun `oppgave sendt til beslutter etter å ha vært sendt i retur har ikke egenskap RETUR`() {
        val oppgave = nyOppgave(SØKNAD, medTotrinnsvurdering = true)
        oppgave.sendTilBeslutter(saksbehandlerUtenTilgang)
        oppgave.sendIRetur(beslutter)
        oppgave.sendTilBeslutter(saksbehandlerUtenTilgang)
        inspektør(oppgave) {
            assertFalse(egenskaper.contains(EgenskapDto.RETUR))
            assertTrue(egenskaper.contains(EgenskapDto.BESLUTTER))
        }
    }

    @Test
    fun `oppgave sendt i retur tildeles opprinnelig saksbehandler`() {
        val oppgave = nyOppgave(SØKNAD, medTotrinnsvurdering = true)
        oppgave.sendTilBeslutter(saksbehandlerUtenTilgang)
        oppgave.sendIRetur(beslutter)
        inspektør(oppgave) {
            assertEquals(saksbehandlerUtenTilgang.toDto(), tildeltTil)
        }
    }

    @Test
    fun `oppgave sendt i retur og deretter tilbake til beslutter tildeles opprinnelig beslutter`() {
        val oppgave = nyOppgave(SØKNAD, medTotrinnsvurdering = true)
        oppgave.sendTilBeslutter(saksbehandlerUtenTilgang)
        oppgave.sendIRetur(beslutter)
        oppgave.sendTilBeslutter(saksbehandlerUtenTilgang)
        inspektør(oppgave) {
            assertEquals(beslutter.toDto(), tildeltTil)
        }
    }

    @Test
    fun `Kaster exception dersom oppgave allerede er sendt i retur`() {
        val oppgave = nyOppgave(SØKNAD, medTotrinnsvurdering = true)
        oppgave.sendTilBeslutter(saksbehandlerUtenTilgang)
        oppgave.sendIRetur(beslutter)
        assertThrows<OppgaveAlleredeSendtIRetur> {
            oppgave.sendIRetur(beslutter)
        }
    }

    @Test
    fun `Kaster exception dersom beslutter er samme som opprinnelig saksbehandler ved retur`() {
        val oppgave = nyOppgave(SØKNAD, medTotrinnsvurdering = true)
        oppgave.sendTilBeslutter(saksbehandlerUtenTilgang)
        assertThrows<OppgaveKreverVurderingAvToSaksbehandlere> {
            oppgave.sendIRetur(saksbehandlerUtenTilgang)
        }
    }

    @Test
    fun `kan ikke ferdigstille en oppgave som ikke har vært behandlet av saksbehandler`() {
        val oppgave = nyOppgave(SØKNAD)
        oppgave.ferdigstill()
        inspektør(oppgave) {
            assertEquals(OppgaveDto.TilstandDto.AvventerSaksbehandler, this.tilstand)
        }
    }

    @Test
    fun `kan ikke invalidere en oppgave i ferdigstilt`() {
        val oppgave = nyOppgave(SØKNAD)
        oppgave.avventerSystem(SAKSBEHANDLER_IDENT, SAKSBEHANDLER_OID)
        oppgave.ferdigstill()
        oppgave.avbryt()
        inspektør(oppgave) {
            assertEquals(OppgaveDto.TilstandDto.Ferdigstilt, this.tilstand)
        }
    }

    @Test
    fun `kan ikke gå i avventer system når en oppgave er i ferdigstilt`() {
        val oppgave = nyOppgave(SØKNAD)
        oppgave.avventerSystem(SAKSBEHANDLER_IDENT, SAKSBEHANDLER_OID)
        oppgave.ferdigstill()
        oppgave.avventerSystem(SAKSBEHANDLER_IDENT, SAKSBEHANDLER_OID)
        inspektør(oppgave) {
            assertEquals(OppgaveDto.TilstandDto.Ferdigstilt, this.tilstand)
        }
    }

    @Test
    fun `kan ikke gå i avventer system når en oppgave er i invalidert`() {
        val oppgave = nyOppgave(SØKNAD)
        oppgave.avbryt()
        oppgave.avventerSystem(SAKSBEHANDLER_IDENT, SAKSBEHANDLER_OID)
        inspektør(oppgave) {
            assertEquals(OppgaveDto.TilstandDto.Invalidert, this.tilstand)
        }
    }

    @Test
    fun `kan invalidere en oppgave i avventer saksbehandler`() {
        val oppgave = nyOppgave(SØKNAD)
        oppgave.avbryt()
        inspektør(oppgave) {
            assertEquals(OppgaveDto.TilstandDto.Invalidert, this.tilstand)
        }
    }

    @Test
    fun `kan ferdigstille en oppgave i avventer saksbehandler`() {
        val oppgave = nyOppgave(SØKNAD)
        oppgave.avbryt()
        inspektør(oppgave) {
            assertEquals(OppgaveDto.TilstandDto.Invalidert, this.tilstand)
        }
    }

    @Test
    fun `kan ferdigstille en oppgave i avventer system`() {
        val oppgave = nyOppgave(SØKNAD)
        oppgave.avventerSystem(SAKSBEHANDLER_IDENT, SAKSBEHANDLER_OID)
        oppgave.avbryt()
        inspektør(oppgave) {
            assertEquals(OppgaveDto.TilstandDto.Invalidert, this.tilstand)
        }
    }

    @Test
    fun `sender ut oppgaveEndret når oppgave sendes til beslutter`() {
        val oppgave = nyOppgave(SØKNAD, medTotrinnsvurdering = true)
        oppgave.register(observer)
        oppgave.sendTilBeslutter(saksbehandlerUtenTilgang)

        assertEquals(1, observer.oppgaverEndret.size)
        assertEquals(oppgave, observer.oppgaverEndret[0])

        inspektør(oppgave) {
            assertEquals(OppgaveDto.TilstandDto.AvventerSaksbehandler, this.tilstand)
        }
    }

    @Test
    fun `sender ut oppgaveEndret når oppgave sendes i retur`() {
        val oppgave = nyOppgave(SØKNAD, medTotrinnsvurdering = true)
        oppgave.register(observer)
        oppgave.sendTilBeslutter(saksbehandlerUtenTilgang)
        oppgave.sendIRetur(beslutter)

        assertEquals(2, observer.oppgaverEndret.size)
        assertEquals(oppgave, observer.oppgaverEndret[0])
        assertEquals(oppgave, observer.oppgaverEndret[1])

        inspektør(oppgave) {
            assertEquals(OppgaveDto.TilstandDto.AvventerSaksbehandler, this.tilstand)
        }
    }

    @Test
    fun `legg til egenskap for gosys kun dersom den ikke finnes fra før`() {
        val oppgave = nyOppgave(SØKNAD)
        oppgave.leggTilGosys()
        oppgave.leggTilGosys()

        inspektør(oppgave) {
            assertEquals(1, egenskaper.filter { it == EgenskapDto.GOSYS }.size)
        }
    }

    @Test
    fun `legg på vent`() {
        val oppgave = nyOppgave(SØKNAD)
        oppgave.forsøkTildelingVedReservasjon(saksbehandlerUtenTilgang)
        oppgave.leggPåVent(true, saksbehandlerUtenTilgang)

        inspektør(oppgave) {
            assertEquals(true, påVent)
        }
    }

    @Test
    fun `legg på vent og skalTildeles`() {
        val oppgave = nyOppgave(SØKNAD)
        oppgave.leggPåVent(true, saksbehandlerUtenTilgang)

        inspektør(oppgave) {
            assertEquals(true, påVent)
            assertTrue(egenskaper.contains(EgenskapDto.PÅ_VENT))
            assertEquals(saksbehandlerUtenTilgang.toDto(), this.tildeltTil)
        }
    }

    @Test
    fun `legg på vent og skalTildeles ny saksbehandler`() {
        val oppgave = nyOppgave(SØKNAD)
        oppgave.forsøkTildelingVedReservasjon(saksbehandlerUtenTilgang)
        oppgave.leggPåVent(true, beslutter)

        inspektør(oppgave) {
            assertEquals(true, påVent)
            assertTrue(egenskaper.contains(EgenskapDto.PÅ_VENT))
            assertEquals(beslutter.toDto(), this.tildeltTil)
        }
    }

    @Test
    fun `legg på vent og !skalTildeles`() {
        val oppgave = nyOppgave(SØKNAD)
        oppgave.forsøkTildelingVedReservasjon(saksbehandlerUtenTilgang)
        oppgave.leggPåVent(false, saksbehandlerUtenTilgang)

        inspektør(oppgave) {
            assertEquals(true, påVent)
            assertTrue(egenskaper.contains(EgenskapDto.PÅ_VENT))
            assertNull(this.tildeltTil)
        }
    }

    @Test
    fun `fjern på vent`() {
        val oppgave = nyOppgave(SØKNAD)
        oppgave.leggPåVent(false, saksbehandlerUtenTilgang)
        oppgave.fjernFraPåVent()

        inspektør(oppgave) {
            assertEquals(false, påVent)
            assertTrue(egenskaper.none { it == EgenskapDto.PÅ_VENT })
            assertNull(this.tildeltTil)
        }
    }

    @Test
    fun `sender ut oppgaveEndret når oppgave sendes legges på vent`() {
        val oppgave = nyOppgave(SØKNAD)
        oppgave.register(observer)
        oppgave.forsøkTildelingVedReservasjon(saksbehandlerUtenTilgang)
        oppgave.leggPåVent(true, saksbehandlerUtenTilgang)

        assertEquals(2, observer.oppgaverEndret.size)
        assertEquals(oppgave, observer.oppgaverEndret[0])
        assertEquals(oppgave, observer.oppgaverEndret[1])

        inspektør(oppgave) {
            assertEquals(OppgaveDto.TilstandDto.AvventerSaksbehandler, this.tilstand)
        }
    }

    @Test
    fun `sender ut oppgaveEndret når oppgave ikke er på vent lenger`() {
        val oppgave = nyOppgave(SØKNAD)
        oppgave.register(observer)
        oppgave.forsøkTildelingVedReservasjon(saksbehandlerUtenTilgang)
        oppgave.leggPåVent(true, saksbehandlerUtenTilgang)
        oppgave.fjernFraPåVent()

        assertEquals(3, observer.oppgaverEndret.size)
        assertEquals(oppgave, observer.oppgaverEndret[0])
        assertEquals(oppgave, observer.oppgaverEndret[1])
        assertEquals(oppgave, observer.oppgaverEndret[2])

        inspektør(oppgave) {
            assertEquals(OppgaveDto.TilstandDto.AvventerSaksbehandler, this.tilstand)
        }
    }

    @EnumSource(value = OppgaveDto.TilstandDto::class)
    @ParameterizedTest
    fun `fra og til dto`(tilstand: OppgaveDto.TilstandDto) {
        val vedtaksperiodeId = UUID.randomUUID()
        val utbetalingId = UUID.randomUUID()
        val saksbehandlerDto =
            OppgaveDto(
                id = nextLong(),
                tilstand = tilstand,
                vedtaksperiodeId = vedtaksperiodeId,
                utbetalingId = utbetalingId,
                hendelseId = UUID.randomUUID(),
                kanAvvises = true,
                egenskaper = EgenskapDto.entries,
                totrinnsvurdering =
                    TotrinnsvurderingDto(
                        vedtaksperiodeId = vedtaksperiodeId,
                        erRetur = true,
                        saksbehandler = saksbehandler().toDto(),
                        beslutter = saksbehandler().toDto(),
                        utbetalingId = utbetalingId,
                        opprettet = LocalDateTime.now(),
                        oppdatert = LocalDateTime.now(),
                    ),
                ferdigstiltAvOid = UUID.randomUUID(),
                ferdigstiltAvIdent = "IDENT",
                tildeltTil = saksbehandler().toDto(),
            )

        assertEquals(saksbehandlerDto, saksbehandlerDto.gjenopprett(TilgangskontrollForTestHarIkkeTilgang).toDto())
    }

    @Test
    fun equals() {
        val gjenopptattOppgave =
            Oppgave.nyOppgave(
                1L,
                VEDTAKSPERIODE_ID,
                UTBETALING_ID,
                UUID.randomUUID(),
                true,
                setOf(OPPGAVETYPE),
            )
        val oppgave1 =
            Oppgave.nyOppgave(OPPGAVE_ID, VEDTAKSPERIODE_ID, UTBETALING_ID, UUID.randomUUID(), true, setOf(SØKNAD))
        val oppgave2 =
            Oppgave.nyOppgave(OPPGAVE_ID, VEDTAKSPERIODE_ID, UTBETALING_ID, UUID.randomUUID(), true, setOf(SØKNAD))
        val oppgave3 =
            Oppgave.nyOppgave(OPPGAVE_ID, UUID.randomUUID(), UTBETALING_ID, UUID.randomUUID(), true, setOf(SØKNAD))
        val oppgave4 =
            Oppgave.nyOppgave(OPPGAVE_ID, VEDTAKSPERIODE_ID, UTBETALING_ID, UUID.randomUUID(), true, setOf(STIKKPRØVE))
        assertEquals(oppgave1, oppgave2)
        assertEquals(oppgave1.hashCode(), oppgave2.hashCode())
        assertNotEquals(oppgave1, oppgave3)
        assertNotEquals(oppgave1.hashCode(), oppgave3.hashCode())
        assertNotEquals(oppgave1, oppgave4)
        assertNotEquals(oppgave1.hashCode(), oppgave4.hashCode())

        gjenopptattOppgave.avventerSystem(SAKSBEHANDLER_IDENT, SAKSBEHANDLER_OID)
        assertNotEquals(gjenopptattOppgave.hashCode(), oppgave2.hashCode())
        assertNotEquals(gjenopptattOppgave, oppgave2)
        assertNotEquals(gjenopptattOppgave, oppgave3)
        assertNotEquals(gjenopptattOppgave, oppgave4)

        oppgave2.avventerSystem("ANNEN_SAKSBEHANDLER", UUID.randomUUID())
        assertEquals(oppgave1, oppgave2)
        assertEquals(oppgave1.hashCode(), oppgave2.hashCode())
    }

    private fun nyOppgave(
        vararg egenskaper: Egenskap,
        medTotrinnsvurdering: Boolean = false,
    ): Oppgave {
        val totrinnsvurdering = if (medTotrinnsvurdering) totrinnsvurdering() else null
        return Oppgave.nyOppgave(
            OPPGAVE_ID,
            VEDTAKSPERIODE_ID,
            UTBETALING_ID,
            UUID.randomUUID(),
            true,
            egenskaper.toSet(),
            totrinnsvurdering,
        )
    }

    private fun totrinnsvurdering() =
        Totrinnsvurdering(
            vedtaksperiodeId = VEDTAKSPERIODE_ID,
            erRetur = false,
            saksbehandler = null,
            beslutter = null,
            utbetalingId = null,
            opprettet = LocalDateTime.now(),
            oppdatert = null,
        )

    private val observer =
        object : OppgaveObserver {
            val oppgaverEndret = mutableListOf<Oppgave>()

            override fun oppgaveEndret(oppgave: Oppgave) {
                oppgaverEndret.add(oppgave)
            }
        }
}
