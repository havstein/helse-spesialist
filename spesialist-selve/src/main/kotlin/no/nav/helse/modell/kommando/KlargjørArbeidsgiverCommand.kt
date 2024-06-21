package no.nav.helse.modell.kommando

import no.nav.helse.db.AvviksvurderingDao
import no.nav.helse.modell.arbeidsgiver.ArbeidsgiverDao
import java.time.LocalDate

internal class KlargjørArbeidsgiverCommand(
    fødselsnummer: String,
    orgnummere: List<String>,
    skjæringstidspunkt: LocalDate,
    arbeidsgiverDao: ArbeidsgiverDao,
    avviksvurderingDao: AvviksvurderingDao,
) : MacroCommand() {
    private val arbeidsgivere = orgnummere.distinct()
    override val commands: List<Command> =
        listOf(
            OpprettArbeidsgiverCommand(arbeidsgivere, arbeidsgiverDao),
            OppdaterArbeidsgiverCommand(fødselsnummer, arbeidsgivere, skjæringstidspunkt, arbeidsgiverDao, avviksvurderingDao),
        )
}
