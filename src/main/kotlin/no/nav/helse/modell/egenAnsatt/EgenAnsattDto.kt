package no.nav.helse.modell.egenAnsatt

import java.time.LocalDateTime

class EgenAnsattDto(
    val fødselsnummer: String,
    val erEgenAnsatt: Boolean,
    val opprettet: LocalDateTime
)
