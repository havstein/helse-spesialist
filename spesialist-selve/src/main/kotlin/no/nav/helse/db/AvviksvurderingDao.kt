package no.nav.helse.db

import com.fasterxml.jackson.module.kotlin.readValue
import javax.sql.DataSource
import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.HelseDao
import no.nav.helse.modell.avviksvurdering.Avviksvurdering
import no.nav.helse.modell.avviksvurdering.AvviksvurderingDto
import no.nav.helse.modell.avviksvurdering.BeregningsgrunnlagDto
import no.nav.helse.modell.avviksvurdering.SammenligningsgrunnlagDto
import no.nav.helse.objectMapper
import org.intellij.lang.annotations.Language

class AvviksvurderingDao(private val dataSource: DataSource) : HelseDao(dataSource) {

    internal fun lagre(avviksvurdering: AvviksvurderingDto) {
        sessionOf(dataSource, returnGeneratedKey = true).use { session ->
            @Language("PostgreSQL")
            val opprettAvviksvurderingQuery = """
                INSERT INTO avviksvurdering(unik_id, fødselsnummer, skjæringstidspunkt, opprettet, avviksprosent, beregningsgrunnlag, sammenligningsgrunnlag_ref)
                VALUES (:unik_id, :fodselsnummer, :skjaeringstidspunkt, :opprettet, :avviksprosent, CAST(:beregningsgrunnlag as json), :sammenligningsgrunnlag_ref)
                ON CONFLICT (unik_id) DO NOTHING;
            """.trimIndent()

            @Language("PostgreSQL")
            val opprettSammenligningsgrunnlagQuery = """
                INSERT INTO sammenligningsgrunnlag(unik_id, fødselsnummer, skjæringstidspunkt, opprettet, sammenligningsgrunnlag)
                VALUES (:unik_id, :fodselsnummer, :skjaeringstidspunkt, :opprettet, CAST(:sammenligningsgrunnlag as json));
            """.trimIndent()

            @Language("PostgreSQL")
            val opprettKoblingTilVilkårsgrunnlag = """
                INSERT INTO vilkarsgrunnlag_per_avviksvurdering(avviksvurdering_ref, vilkårsgrunnlag_id)
                VALUES (:unik_id, :vilkarsgrunnlag_id) ON CONFLICT DO NOTHING;
            """.trimIndent()

            session.transaction { transactionalSession ->
                val sammenligningsgrunnlagRef = transactionalSession.run(
                    queryOf(
                        opprettSammenligningsgrunnlagQuery,
                        mapOf(
                            "unik_id" to avviksvurdering.sammenligningsgrunnlag.unikId,
                            "fodselsnummer" to avviksvurdering.fødselsnummer,
                            "skjaeringstidspunkt" to avviksvurdering.skjæringstidspunkt,
                            "opprettet" to avviksvurdering.opprettet,
                            "sammenligningsgrunnlag" to objectMapper.writeValueAsString(avviksvurdering.sammenligningsgrunnlag)
                        )
                    ).asUpdateAndReturnGeneratedKey
                )
                transactionalSession.run(
                    queryOf(
                        opprettAvviksvurderingQuery,
                        mapOf(
                            "unik_id" to avviksvurdering.unikId,
                            "fodselsnummer" to avviksvurdering.fødselsnummer,
                            "skjaeringstidspunkt" to avviksvurdering.skjæringstidspunkt,
                            "opprettet" to avviksvurdering.opprettet,
                            "avviksprosent" to avviksvurdering.avviksprosent,
                            "beregningsgrunnlag" to objectMapper.writeValueAsString(avviksvurdering.beregningsgrunnlag),
                            "sammenligningsgrunnlag_ref" to sammenligningsgrunnlagRef
                        )
                    ).asUpdate
                )
                transactionalSession.run(
                    queryOf(
                        opprettKoblingTilVilkårsgrunnlag,
                        mapOf(
                            "unik_id" to avviksvurdering.unikId,
                            "vilkarsgrunnlag_id" to avviksvurdering.vilkårsgrunnlagId,
                        )
                    ).asUpdate
                )
            }
        }
    }

    internal fun finnAvviksvurderinger(fødselsnummer: String): List<Avviksvurdering> = asSQL(
        """
            SELECT av.unik_id, vpa.vilkårsgrunnlag_id, av.fødselsnummer, av.skjæringstidspunkt, av.opprettet, avviksprosent, beregningsgrunnlag, sg.sammenligningsgrunnlag FROM avviksvurdering av 
            INNER JOIN sammenligningsgrunnlag sg ON av.sammenligningsgrunnlag_ref = sg.id
            INNER JOIN vilkarsgrunnlag_per_avviksvurdering vpa ON vpa.avviksvurdering_ref = av.unik_id
            WHERE av.fødselsnummer = :fodselsnummer;
        """.trimIndent(),
        mapOf(
            "fodselsnummer" to fødselsnummer,
        )
    ).list {
        Avviksvurdering(
            unikId = it.uuid("unik_id"),
            vilkårsgrunnlagId = it.uuid("vilkårsgrunnlag_id"),
            fødselsnummer = it.string("fødselsnummer"),
            skjæringstidspunkt = it.localDate("skjæringstidspunkt"),
            opprettet = it.localDateTime("opprettet"),
            avviksprosent = it.double("avviksprosent"),
            sammenligningsgrunnlag = objectMapper.readValue<SammenligningsgrunnlagDto>(it.string("sammenligningsgrunnlag")),
            beregningsgrunnlag = objectMapper.readValue<BeregningsgrunnlagDto>(it.string("beregningsgrunnlag")),
        )
    }
}
