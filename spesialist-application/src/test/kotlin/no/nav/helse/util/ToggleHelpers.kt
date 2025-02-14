package no.nav.helse.util

import no.nav.helse.modell.Toggle

internal object ToggleHelpers {

    internal fun Toggle.enable() = setPrivateValue(true)

    internal fun Toggle.disable() = setPrivateValue(false)

    private fun Toggle.setPrivateValue(value: Boolean) {
        this.javaClass.superclass.getDeclaredField("_enabled").let { field ->
            field.isAccessible = true
            field.set(this, value)
        }
    }
}
