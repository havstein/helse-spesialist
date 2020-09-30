package no.nav.helse.mediator.kafka

internal class MiljøstyrtFeatureToggle(private val env: Map<String, String>) {
    internal fun risikovurdering() = env.getOrDefault("RISK_FEATURE_TOGGLE", "false").toBoolean()
}
