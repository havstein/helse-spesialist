package no.nav.helse.mediator

internal class MiljøstyrtFeatureToggle(private val env: Map<String, String>) {
    internal fun risikovurdering() = env.getOrDefault("RISK_FEATURE_TOGGLE", "false").toBoolean()
    internal fun automatisering() = env.getOrDefault("AUTOMATISERING_FEATURE_TOGGLE", "false").toBoolean()
    internal fun egenAnsatt() = env.getOrDefault("EGEN_ANSATT_FEATURE_TOGGLE", "false").toBoolean()
}

internal class FeatureToggle {
    companion object {
        const val godkjenningsEvent: Boolean = false
    }
}
