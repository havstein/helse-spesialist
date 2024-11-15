package no.nav.helse.db

import no.nav.helse.HelseDao.Companion.asSQL
import no.nav.helse.modell.vedtak.AvslagDto
import no.nav.helse.modell.vedtak.SaksbehandlerVurderingDto
import no.nav.helse.spesialist.api.graphql.mutation.Avslagsdata
import java.util.UUID
import javax.sql.DataSource

class AvslagDao(queryRunner: QueryRunner) : QueryRunner by queryRunner {
    constructor(dataSource: DataSource) : this(MedDataSource(dataSource))

    private fun lagreBegrunnelse(
        avslagsdata: Avslagsdata,
        saksbehandlerOid: UUID,
    ) = asSQL(
        """
        INSERT INTO begrunnelse(tekst, type, saksbehandler_ref) VALUES (:tekst, :type, :saksbehandler_ref)
        """.trimIndent(),
        "tekst" to avslagsdata.begrunnelse,
        "type" to avslagsdata.type.toString(),
        "saksbehandler_ref" to saksbehandlerOid,
    ).updateAndReturnGeneratedKey()

    internal fun lagreAvslag(
        oppgaveId: Long,
        avslagsdata: Avslagsdata,
        saksbehandlerOid: UUID,
    ) = asSQL(
        """
        SELECT v.vedtaksperiode_id, svg.id AS generasjon_id
        FROM vedtak v
        INNER JOIN oppgave o on v.id = o.vedtak_ref
        INNER JOIN selve_vedtaksperiode_generasjon svg ON svg.unik_id = o.generasjon_ref
        WHERE o.id = :oppgaveId 
        """.trimIndent(),
        "oppgaveId" to oppgaveId,
    ).singleOrNull { Pair(it.uuid("vedtaksperiode_id"), it.long("generasjon_id")) }?.let { (vedtaksperiodeId, generasjonId) ->
        lagreBegrunnelse(avslagsdata, saksbehandlerOid).let { begrunnelseId ->
            asSQL(
                """
                INSERT INTO avslag (vedtaksperiode_id, begrunnelse_ref, generasjon_ref) VALUES (:vedtaksperiodeId, :begrunnelseId, :generasjonId)
                """.trimIndent(),
                "vedtaksperiodeId" to vedtaksperiodeId,
                "begrunnelseId" to begrunnelseId,
                "generasjonId" to generasjonId,
            ).update()
        }
    }

    internal fun invaliderAvslag(oppgaveId: Long) =
        asSQL(
            """
            WITH t as ( 
                SELECT v.vedtaksperiode_id, svg.id 
                FROM vedtak v
                INNER JOIN oppgave o ON v.id = o.vedtak_ref
                INNER JOIN selve_vedtaksperiode_generasjon svg ON o.generasjon_ref = svg.unik_id
                WHERE o.id = :oppgaveId
            )
            UPDATE avslag a
            SET invalidert = true
            FROM t
            WHERE a.vedtaksperiode_id = t.vedtaksperiode_id AND a.generasjon_ref = t.id
            """.trimIndent(),
            "oppgaveId" to oppgaveId,
        ).update()

    internal fun finnAvslag(
        vedtaksperiodeId: UUID,
        generasjonId: Long,
    ) = asSQL(
        """
        SELECT begrunnelse_ref FROM avslag 
        WHERE vedtaksperiode_id = :vedtaksperiodeId 
        AND generasjon_ref = :generasjonId 
        AND invalidert = false 
        ORDER BY opprettet DESC LIMIT 1
        """.trimIndent(),
        "vedtaksperiodeId" to vedtaksperiodeId,
        "generasjonId" to generasjonId,
    ).singleOrNull {
        it.longOrNull("begrunnelse_ref")?.let { begrunnelseRef ->
            asSQL(
                """
                SELECT type, tekst FROM begrunnelse WHERE id = :begrunnelseRef
                """.trimIndent(),
                "begrunnelseRef" to begrunnelseRef,
            ).singleOrNull { avslag ->
                AvslagDto(enumValueOf(avslag.string("type")), avslag.string("tekst"))
            }
        }
    }

    // TODO: Tabell avslag bør endre navn og mye av denne klassen skal skrives om når vi kommer dit
    internal fun finnVurdering(
        vedtaksperiodeId: UUID,
        generasjonId: Long,
    ): SaksbehandlerVurderingDto? {
        return asSQL(
            """
            SELECT begrunnelse_ref FROM avslag 
            WHERE vedtaksperiode_id = :vedtaksperiodeId 
            AND generasjon_ref = :generasjonId 
            AND invalidert = false 
            ORDER BY opprettet DESC LIMIT 1
            """.trimIndent(),
            "vedtaksperiodeId" to vedtaksperiodeId,
            "generasjonId" to generasjonId,
        ).singleOrNull {
            it.longOrNull("begrunnelse_ref")?.let { begrunnelseRef ->
                asSQL(
                    """
                    SELECT type, tekst FROM begrunnelse WHERE id = :begrunnelseRef
                    """.trimIndent(),
                    "begrunnelseRef" to begrunnelseRef,
                ).singleOrNull { avslag ->
                    val vurdering = SaksbehandlerVurderingDto.VurderingDto.valueOf(avslag.string("type"))
                    val begrunnelse = avslag.string("tekst")
                    when (vurdering) {
                        SaksbehandlerVurderingDto.VurderingDto.AVSLAG -> SaksbehandlerVurderingDto.Avslag(begrunnelse)
                        SaksbehandlerVurderingDto.VurderingDto.DELVIS_AVSLAG -> SaksbehandlerVurderingDto.DelvisAvslag(begrunnelse)
                        SaksbehandlerVurderingDto.VurderingDto.INNVILGELSE -> throw RuntimeException(
                            "Innvilgelse bør ikke forekomme før alt dette er skrevet om.",
                        )
                    }
                }
            }
        }
    }

    internal fun finnAlleAvslag(
        vedtaksperiodeId: UUID,
        utbetalingId: UUID,
    ): Set<no.nav.helse.spesialist.api.graphql.schema.Avslag> =
        asSQL(
            """
            SELECT b.type, b.tekst, a.opprettet, s.ident, a.invalidert FROM avslag a 
            INNER JOIN selve_vedtaksperiode_generasjon svg ON a.generasjon_ref = svg.id 
            INNER JOIN begrunnelse b ON b.id = a.begrunnelse_ref
            INNER JOIN saksbehandler s ON s.oid = b.saksbehandler_ref
            WHERE a.vedtaksperiode_id = :vedtaksperiodeId AND svg.utbetaling_id = :utbetalingId 
            ORDER BY opprettet DESC
            """.trimIndent(),
            "vedtaksperiodeId" to vedtaksperiodeId,
            "utbetalingId" to utbetalingId,
        ).list { avslag ->
            no.nav.helse.spesialist.api.graphql.schema.Avslag(
                enumValueOf(avslag.string("type")),
                avslag.string("tekst"),
                avslag.localDateTime("opprettet"),
                avslag.string("ident"),
                avslag.boolean("invalidert"),
            )
        }.toSet()
}
