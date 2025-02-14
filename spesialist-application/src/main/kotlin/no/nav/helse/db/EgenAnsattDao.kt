package no.nav.helse.db

import java.time.LocalDateTime

interface EgenAnsattDao {
    fun erEgenAnsatt(fødselsnummer: String): Boolean?

    fun lagre(
        fødselsnummer: String,
        erEgenAnsatt: Boolean,
        opprettet: LocalDateTime,
    )
}
