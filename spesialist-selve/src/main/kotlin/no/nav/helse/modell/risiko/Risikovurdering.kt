package no.nav.helse.modell.risiko

import no.nav.helse.modell.automatisering.AutomatiseringValidering

class Risikovurdering private constructor(private val kanGodkjennesAutomatisk: Boolean) : AutomatiseringValidering {
    internal companion object {
        internal fun restore(kanGodkjennesAutomatisk: Boolean) = Risikovurdering(kanGodkjennesAutomatisk)
    }

    override fun måTilSaksbehandler() = !kanGodkjennesAutomatisk

    override fun forklaring() = "Vilkårsvurdering for arbeidsuførhet, aktivitetsplikt eller medvirkning er ikke oppfylt"
}
