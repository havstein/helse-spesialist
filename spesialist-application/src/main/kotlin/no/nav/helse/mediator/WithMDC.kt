package no.nav.helse.mediator

import org.slf4j.MDC

fun <T> withMDC(
    context: Map<String, String>,
    block: () -> T,
): T {
    val contextMap = MDC.getCopyOfContextMap() ?: emptyMap()
    try {
        MDC.setContextMap(contextMap + context)
        return block()
    } finally {
        MDC.setContextMap(contextMap)
    }
}
