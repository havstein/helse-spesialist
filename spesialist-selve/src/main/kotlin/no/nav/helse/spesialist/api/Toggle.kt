package no.nav.helse.spesialist.api

abstract class Toggle(private var _enabled: Boolean) {
    private constructor(key: String, default: Boolean = false) : this(System.getenv()[key]?.toBoolean() ?: default)

    val enabled get() = _enabled

    object BehandleEnOgEnPeriode : Toggle("BEHANDLE_EN_OG_EN_PERIODE", false)
}
