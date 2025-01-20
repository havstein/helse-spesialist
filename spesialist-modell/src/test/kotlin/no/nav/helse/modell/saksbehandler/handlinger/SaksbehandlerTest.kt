package no.nav.helse.modell.saksbehandler.handlinger

import no.nav.helse.modell.januar
import no.nav.helse.modell.melding.SubsumsjonEvent
import no.nav.helse.modell.saksbehandler.LagtPåVentEvent
import no.nav.helse.modell.saksbehandler.MinimumSykdomsgradVurdertEvent
import no.nav.helse.modell.saksbehandler.OverstyrtArbeidsforholdEvent
import no.nav.helse.modell.saksbehandler.OverstyrtInntektOgRefusjonEvent
import no.nav.helse.modell.saksbehandler.OverstyrtTidslinjeEvent
import no.nav.helse.modell.saksbehandler.Saksbehandler
import no.nav.helse.modell.saksbehandler.Saksbehandler.Companion.gjenopprett
import no.nav.helse.modell.saksbehandler.Saksbehandler.Companion.toDto
import no.nav.helse.modell.saksbehandler.SaksbehandlerDto
import no.nav.helse.modell.saksbehandler.SaksbehandlerObserver
import no.nav.helse.modell.vilkårsprøving.Lovhjemmel
import no.nav.helse.modell.vilkårsprøving.Subsumsjon
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

internal class SaksbehandlerTest {

    @Test
    fun `håndtering av OverstyrtTidslinje medfører utgående event`() {
        var observert = false
        val observer = object : SaksbehandlerObserver {
            override fun tidslinjeOverstyrt(fødselsnummer: String, event: OverstyrtTidslinjeEvent) {
                observert = true
            }
        }

        val saksbehandler = saksbehandler()
        saksbehandler.register(observer)
        saksbehandler.håndter(
            OverstyrtTidslinje(
                vedtaksperiodeId = UUID.randomUUID(),
                aktørId = "123",
                fødselsnummer = "1234",
                organisasjonsnummer = "12345",
                dager = emptyList(),
                begrunnelse = "begrunnelse",
            )
        )
        assertEquals(true, observert)
    }

    @Test
    fun `lager subsumsjoner ved håndtering av OverstyrtTidslinje`() {
        val subsumsjoner = mutableListOf<SubsumsjonEvent>()
        val observer = object : SaksbehandlerObserver {
            override fun nySubsumsjon(fødselsnummer: String, subsumsjonEvent: SubsumsjonEvent) {
                subsumsjoner.add(subsumsjonEvent)
            }
        }

        val saksbehandler = saksbehandler()
        val vedtaksperiodeId = UUID.randomUUID()
        val overstyringId = UUID.randomUUID()
        saksbehandler.register(observer)
        saksbehandler.håndter(
            OverstyrtTidslinje(
                id = overstyringId,
                vedtaksperiodeId = vedtaksperiodeId,
                aktørId = "123",
                fødselsnummer = "1234",
                organisasjonsnummer = "12345",
                dager = overstyrteDager(),
                begrunnelse = "begrunnelse",
            )
        )

        assertEquals(2, subsumsjoner.size)
        assertEquals(
            SubsumsjonEvent(
                id = subsumsjoner[0].id,
                fødselsnummer = "1234",
                paragraf = "22-13",
                ledd = "7",
                bokstav = null,
                lovverk = "folketrygdloven",
                lovverksversjon = "2019-06-21",
                utfall = Subsumsjon.Utfall.VILKAR_BEREGNET.name,
                output = mapOf(
                    "dager" to listOf(
                        mapOf(
                            "dato" to 1.januar,
                            "type" to "Sykedag",
                            "fraType" to "Sykedag",
                            "grad" to 100,
                            "fraGrad" to 100,
                        ),
                        mapOf(
                            "dato" to 2.januar,
                            "type" to "Sykedag",
                            "fraType" to "Sykedag",
                            "grad" to 100,
                            "fraGrad" to 100,
                        ),
                    )
                ),
                input = mapOf("begrunnelseFraSaksbehandler" to "begrunnelse"),
                sporing = mapOf(
                    "organisasjonsnummer" to listOf("12345"),
                    "vedtaksperiode" to listOf(vedtaksperiodeId.toString()),
                    "saksbehandler" to listOf("epost@nav.no"),
                    "overstyrtidslinje" to listOf(overstyringId.toString()),
                ),
                tidsstempel = subsumsjoner[0].tidsstempel,
                kilde = "spesialist",
            ),
            subsumsjoner[0]
        )
        assertEquals(
            SubsumsjonEvent(
                id = subsumsjoner[1].id,
                fødselsnummer = "1234",
                paragraf = "ANNEN PARAGRAF",
                ledd = "ANNET LEDD",
                bokstav = "EN BOKSTAV",
                lovverk = "ANNEN LOV",
                lovverksversjon = "1970-01-01",
                utfall = Subsumsjon.Utfall.VILKAR_BEREGNET.name,
                output = mapOf(
                    "dager" to listOf(
                        mapOf(
                            "dato" to 3.januar,
                            "type" to "Sykedag",
                            "fraType" to "Sykedag",
                            "grad" to 100,
                            "fraGrad" to 100,
                        ),
                    )
                ),
                input = mapOf("begrunnelseFraSaksbehandler" to "begrunnelse"),
                sporing = mapOf(
                    "organisasjonsnummer" to listOf("12345"),
                    "vedtaksperiode" to listOf(vedtaksperiodeId.toString()),
                    "saksbehandler" to listOf("epost@nav.no"),
                    "overstyrtidslinje" to listOf(overstyringId.toString()),
                ),
                tidsstempel = subsumsjoner[1].tidsstempel,
                kilde = "spesialist",
            ),
            subsumsjoner[1]
        )
    }

    @Test
    fun `håndtering av OverstyrtInntektOgRefusjon medfører utgående event`() {
        var observert = false
        val observer = object : SaksbehandlerObserver {
            override fun inntektOgRefusjonOverstyrt(fødselsnummer: String, event: OverstyrtInntektOgRefusjonEvent) {
                observert = true
            }
        }

        val saksbehandler = saksbehandler()
        saksbehandler.register(observer)
        saksbehandler.håndter(
            OverstyrtInntektOgRefusjon(
                aktørId = "123",
                fødselsnummer = "1234",
                skjæringstidspunkt = 1.januar,
                arbeidsgivere = emptyList(),
                vedtaksperiodeId = UUID.randomUUID(),
            )
        )
        assertEquals(true, observert)
    }

    @Test
    fun `håndtering av OverstyrtArbeidsforhold medfører utgående event`() {
        var observert = false
        val observer = object : SaksbehandlerObserver {
            override fun arbeidsforholdOverstyrt(fødselsnummer: String, event: OverstyrtArbeidsforholdEvent) {
                observert = true
            }
        }

        val saksbehandler = saksbehandler()
        saksbehandler.register(observer)
        saksbehandler.håndter(
            OverstyrtArbeidsforhold(
                aktørId = "123",
                fødselsnummer = "1234",
                skjæringstidspunkt = 1.januar,
                overstyrteArbeidsforhold = emptyList(),
                vedtaksperiodeId = UUID.randomUUID(),
            )
        )
        assertEquals(true, observert)
    }

    @Test
    fun `håndtering av MinimumSykdomsgrad medfører utgående event`() {
        var observert = false
        val observer = object : SaksbehandlerObserver {
            override fun minimumSykdomsgradVurdert(fødselsnummer: String, event: MinimumSykdomsgradVurdertEvent) {
                observert = true
            }
        }

        val saksbehandler = saksbehandler()
        saksbehandler.register(observer)
        saksbehandler.håndter(
            MinimumSykdomsgrad(
                aktørId = "123",
                fødselsnummer = "1234",
                begrunnelse = "begrunnelse",
                initierendeVedtaksperiodeId = UUID.randomUUID(),
                perioderVurdertOk = emptyList(),
                perioderVurdertIkkeOk = listOf(MinimumSykdomsgradPeriode(1.januar, 31.januar)),
                arbeidsgivere = listOf(MinimumSykdomsgradArbeidsgiver(organisasjonsnummer = "12345", berørtVedtaksperiodeId = UUID.randomUUID())),
            )
        )
        assertEquals(true, observert)
    }

    @Test
    fun `håndtering av lagtPåVent medfører utgående event`() {
        var observert = false
        val observer = object : SaksbehandlerObserver {
            override fun lagtPåVent(fødselsnummer: String, event: LagtPåVentEvent) {
                observert = true
            }
        }

        val saksbehandler = saksbehandler()
        saksbehandler.register(observer)
        saksbehandler.håndter(
            LeggPåVent(
                fødselsnummer = "1234",
                oppgaveId = "12345".toLong(),
                frist = LocalDate.now(),
                behandlingId = UUID.randomUUID(),
                skalTildeles = true,
                notatTekst = "en tekst",
                årsaker = listOf(PåVentÅrsak("key", "arsak"))
            )
        )
        assertEquals(true, observert)
    }

    @Test
    fun `referential equals`() {
        val saksbehandler = saksbehandler()
        assertEquals(saksbehandler, saksbehandler)
        assertEquals(saksbehandler.hashCode(), saksbehandler.hashCode())
    }

    @Test
    fun `structural equals`() {
        val oid = UUID.randomUUID()
        val saksbehandler1 = saksbehandler(oid = oid)
        val saksbehandler2 = saksbehandler(oid = oid)
        assertEquals(saksbehandler1, saksbehandler2)
        assertEquals(saksbehandler1.hashCode(), saksbehandler2.hashCode())
    }

    @Test
    fun `not equals - epost`() {
        val oid = UUID.randomUUID()
        val saksbehandler1 = saksbehandler(epost = "epost1@nav.no", oid = oid)
        val saksbehandler2 = saksbehandler(epost = "epost2@nav.no", oid = oid)
        Assertions.assertNotEquals(saksbehandler1, saksbehandler2)
        Assertions.assertNotEquals(saksbehandler1.hashCode(), saksbehandler2.hashCode())
    }

    @Test
    fun `not equals - oid`() {
        val saksbehandler1 = saksbehandler()
        val saksbehandler2 = saksbehandler()
        Assertions.assertNotEquals(saksbehandler1, saksbehandler2)
        Assertions.assertNotEquals(saksbehandler1.hashCode(), saksbehandler2.hashCode())
    }

    @Test
    fun `not equals - navn`() {
        val oid = UUID.randomUUID()
        val saksbehandler1 = saksbehandler(navn = "navn 1", oid = oid)
        val saksbehandler2 = saksbehandler(navn = "navn 2", oid = oid)
        Assertions.assertNotEquals(saksbehandler1, saksbehandler2)
        Assertions.assertNotEquals(saksbehandler1.hashCode(), saksbehandler2.hashCode())
    }

    @Test
    fun `not equals - ident`() {
        val oid = UUID.randomUUID()
        val saksbehandler1 = saksbehandler(ident = "X999999", oid = oid)
        val saksbehandler2 = saksbehandler(ident = "Y999999", oid = oid)
        Assertions.assertNotEquals(saksbehandler1, saksbehandler2)
        Assertions.assertNotEquals(saksbehandler1.hashCode(), saksbehandler2.hashCode())
    }

    @Test
    fun `fra og til dto`() {
        val saksbehandlerDto = SaksbehandlerDto(
            epostadresse = "saksbehandler@nav.no",
            oid = UUID.randomUUID(),
            navn = "Kim Saksbehandler",
            ident = "X999999",
        )

        assertEquals(saksbehandlerDto, saksbehandlerDto.gjenopprett(TilgangskontrollForTestHarIkkeTilgang).toDto())
    }

    private fun overstyrteDager(): List<OverstyrtTidslinjedag> = listOf(
        OverstyrtTidslinjedag(
            dato = 1.januar,
            type = "Sykedag",
            fraType = "Sykedag",
            grad = 100,
            fraGrad = 100,
            lovhjemmel = Lovhjemmel(
                paragraf = "22-13",
                ledd = "7",
                lovverk = "folketrygdloven",
                lovverksversjon = "2019-06-21",
            )
        ),
        OverstyrtTidslinjedag(
            dato = 2.januar,
            type = "Sykedag",
            fraType = "Sykedag",
            grad = 100,
            fraGrad = 100,
            lovhjemmel = Lovhjemmel(
                paragraf = "22-13",
                ledd = "7",
                lovverk = "folketrygdloven",
                lovverksversjon = "2019-06-21",
            )
        ),
        OverstyrtTidslinjedag(
            dato = 3.januar,
            type = "Sykedag",
            fraType = "Sykedag",
            grad = 100,
            fraGrad = 100,
            lovhjemmel = Lovhjemmel(
                paragraf = "ANNEN PARAGRAF",
                ledd = "ANNET LEDD",
                bokstav = "EN BOKSTAV",
                lovverk = "ANNEN LOV",
                lovverksversjon = "1970-01-01",
            )
        ),
        OverstyrtTidslinjedag(
            dato = 4.januar,
            type = "Feriedag",
            fraType = "Sykedag",
            grad = 100,
            fraGrad = 100,
            lovhjemmel = null,
        )
    )

    private fun saksbehandler(
        epost: String = "epost@nav.no",
        oid: UUID = UUID.randomUUID(),
        navn: String = "navn",
        ident: String = "Z999999",
    ) = Saksbehandler(
        epostadresse = epost,
        oid = oid,
        navn = navn,
        ident = ident,
        tilgangskontroll = TilgangskontrollForTestHarIkkeTilgang,
    )
}
