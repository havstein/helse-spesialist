package no.nav.helse.modell.command.nyny

import no.nav.helse.modell.VedtakDao
import no.nav.helse.modell.vedtak.Saksbehandleroppgavetype
import java.util.*

internal class PersisterVedtaksperiodetypeCommand(
    private val hendelseId: UUID,
    private val vedtaksperiodetype: Saksbehandleroppgavetype?,
    private val vedtakDao: VedtakDao
) :
    Command {
    override fun execute(context: CommandContext): Boolean {
        vedtaksperiodetype?.let { type ->
            vedtakDao.leggTilVedtaksperiodetype(hendelseId, type)
        }
        return true
    }
}
