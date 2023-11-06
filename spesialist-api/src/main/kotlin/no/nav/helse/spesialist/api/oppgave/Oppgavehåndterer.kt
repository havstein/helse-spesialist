package no.nav.helse.spesialist.api.oppgave

import no.nav.helse.spesialist.api.graphql.schema.AntallOppgaver
import no.nav.helse.spesialist.api.graphql.schema.BehandletOppgave
import no.nav.helse.spesialist.api.graphql.schema.Filtrering
import no.nav.helse.spesialist.api.graphql.schema.OppgaverTilBehandling
import no.nav.helse.spesialist.api.graphql.schema.Oppgavesortering
import no.nav.helse.spesialist.api.saksbehandler.SaksbehandlerFraApi

interface Oppgavehåndterer {
    fun sendTilBeslutter(oppgaveId: Long, behandlendeSaksbehandler: SaksbehandlerFraApi)
    fun sendIRetur(oppgaveId: Long, besluttendeSaksbehandler: SaksbehandlerFraApi)
    fun harBlittEgenAnsatt(fødselsnummer: String)
    fun venterPåSaksbehandler(oppgaveId: Long): Boolean
    fun erRiskoppgave(oppgaveId: Long): Boolean
    fun oppgaver(saksbehandlerFraApi: SaksbehandlerFraApi, offset: Int, limit: Int, sortering: List<Oppgavesortering>, filtrering: Filtrering): OppgaverTilBehandling
    fun antallOppgaver(saksbehandlerFraApi: SaksbehandlerFraApi): AntallOppgaver
    fun behandledeOppgaver(saksbehandlerFraApi: SaksbehandlerFraApi): List<BehandletOppgave>
}
