package no.nav.helse.modell.oppgave

import no.nav.helse.spesialist.domain.oppgave.Egenskap
import no.nav.helse.spesialist.domain.oppgave.Oppgave
import no.nav.helse.spesialist.domain.tilgangskontroll.Brukerrolle
import no.nav.helse.spesialist.domain.tilgangskontroll.Brukerroller
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class OppgaveTilgangskontrollTest {
    @ParameterizedTest(name = "egenskap={0}")
    @EnumSource(value = Egenskap::class, names = ["STRENGT_FORTROLIG_ADRESSE"])
    fun `saksbehandler har ikke tilgang til oppgaveegenskap selv med alle brukerroller`(egenskap: Egenskap) {
        assertFalse(
            Oppgave.harTilgangTilEgenskap(
                egenskap = egenskap,
                brukerroller = Brukerroller(),
            ),
        )
    }

    @ParameterizedTest(name = "egenskap={0}, roller={1}")
    @MethodSource("kombinasjonerSomGirTilgang")
    fun `saksbehandler har tilgang til oppgaveegenskap`(
        egenskap: Egenskap,
        roller: Brukerroller,
    ) {
        assertTrue(
            Oppgave.harTilgangTilEgenskap(
                egenskap = egenskap,
                brukerroller = roller,
            ),
        )
    }

    @ParameterizedTest(name = "egenskap={0}, roller={1}")
    @MethodSource("kombinasjonerSomIkkeGirTilgang")
    fun `saksbehandler har ikke tilgang til oppgaveegenskap`(
        egenskap: Egenskap,
        roller: Brukerroller,
    ) {
        assertFalse(
            Oppgave.harTilgangTilEgenskap(
                egenskap = egenskap,
                brukerroller = roller,
            ),
        )
    }

    companion object {
        @JvmStatic
        private fun kombinasjonerSomGirTilgang(): Stream<Arguments> =
            Stream.of(
                Arguments.of(
                    Egenskap.FORTROLIG_ADRESSE,
                    Brukerroller(Brukerrolle.Kode7),
                ),
                Arguments.of(
                    Egenskap.EGEN_ANSATT,
                    Brukerroller(Brukerrolle.EgenAnsatt),
                ),
            )

        @JvmStatic
        private fun kombinasjonerSomIkkeGirTilgang(): Stream<Arguments> =
            Stream.of(
                Arguments.of(
                    Egenskap.FORTROLIG_ADRESSE,
                    Brukerroller(),
                ),
                Arguments.of(
                    Egenskap.EGEN_ANSATT,
                    Brukerroller(),
                ),
                Arguments.of(
                    Egenskap.FORTROLIG_ADRESSE,
                    Brukerroller((Brukerrolle.entries - Brukerrolle.Kode7).toSet()),
                ),
                Arguments.of(
                    Egenskap.EGEN_ANSATT,
                    Brukerroller((Brukerrolle.entries - Brukerrolle.EgenAnsatt).toSet()),
                ),
            )
    }
}
