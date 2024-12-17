package no.nav.helse.modell

import no.nav.helse.modell.hendelse.UtgåendeHendelse

class Meldingslogg {
    private val logg: MutableList<UtgåendeHendelse> = mutableListOf()

    fun nyHendelse(hendelse: UtgåendeHendelse) {
        logg.add(hendelse)
    }

    fun hendelser(): List<UtgåendeHendelse> = logg
}
