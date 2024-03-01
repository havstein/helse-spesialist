package no.nav.helse.modell.person

import java.util.UUID
import no.nav.helse.mediator.meldinger.Personmelding
import no.nav.helse.modell.arbeidsgiver.ArbeidsgiverDao
import no.nav.helse.modell.kommando.Command
import no.nav.helse.modell.kommando.MacroCommand
import no.nav.helse.modell.kommando.OpprettMinimalArbeidsgiverCommand
import no.nav.helse.modell.kommando.OpprettMinimalPersonCommand
import no.nav.helse.rapids_rivers.JsonMessage

internal class SøknadSendt private constructor(
    override val id: UUID,
    private val fødselsnummer: String,
    val aktørId: String,
    val organisasjonsnummer: String,
    private val json: String,
) : Personmelding {
    override fun fødselsnummer() = fødselsnummer
    override fun toJson() = json

    internal companion object {
        fun søknadSendt(packet: JsonMessage) = SøknadSendt(
            id = UUID.fromString(packet["@id"].asText()),
            fødselsnummer = packet["fnr"].asText(),
            aktørId = packet["aktorId"].asText(),
            organisasjonsnummer = packet["arbeidsgiver.orgnummer"].asText(),
            json = packet.toJson()
        )

        fun søknadSendtArbeidsledig(packet: JsonMessage): SøknadSendt {
            return SøknadSendt(
                id = UUID.fromString(packet["@id"].asText()),
                fødselsnummer = packet["fnr"].asText(),
                aktørId = packet["aktorId"].asText(),
                organisasjonsnummer = packet["tidligereArbeidsgiverOrgnummer"].asText(),
                json = packet.toJson()
            )
        }
    }
}

internal class SøknadSendtCommand(
    fødselsnummer: String,
    aktørId: String,
    organisasjonsnummer: String,
    personDao: PersonDao,
    arbeidsgiverDao: ArbeidsgiverDao
): MacroCommand() {
    override val commands: List<Command> = listOf(
        OpprettMinimalPersonCommand(fødselsnummer, aktørId, personDao),
        OpprettMinimalArbeidsgiverCommand(organisasjonsnummer, arbeidsgiverDao),
    )
}