package no.nav.helse.flex.retro

import no.nav.helse.flex.domene.tilVedtakDto
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.OffsetDateTime

@Repository
class VedtakDAO(
    private val namedParameterJdbcTemplate: NamedParameterJdbcTemplate
) {
    fun finnVedtak(fnr: String): List<Vedtak> {
        return namedParameterJdbcTemplate.query(
            """
            SELECT id, vedtak, lest, opprettet
            FROM vedtak
            WHERE fnr = :fnr
            AND mottatt_etter_migrering = False
            """,
            MapSqlParameterSource()
                .addValue("fnr", fnr)
        ) { resultSet, _ ->
            resultSet.toVedtak()
        }
    }
}

fun ResultSet.toVedtak(): Vedtak =
    Vedtak(
        id = getString("id"),
        lest = getObject("lest", OffsetDateTime::class.java) != null,
        lestDato = getObject("lest", OffsetDateTime::class.java),
        vedtak = getString("vedtak").tilVedtakDto(),
        opprettet = getObject("opprettet", OffsetDateTime::class.java).toInstant()
    )
