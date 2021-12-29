package no.nav.helse.flex.db

import no.nav.helse.flex.domene.tilVedtakDto
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
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

    fun lesVedtak(fnr: String, vedtaksId: String): Boolean {
        val update = namedParameterJdbcTemplate.update(
            """
                   UPDATE vedtak
                   SET lest = :lest
                   WHERE fnr = :fnr
                   AND id = :id
                   AND lest is null
                """,
            MapSqlParameterSource()
                .addValue("lest", Timestamp.from(Instant.now()))
                .addValue("fnr", fnr)
                .addValue("id", vedtaksId)
        )

        return update > 0
    }

    fun slettVedtak(vedtakId: String, fnr: String) {
        namedParameterJdbcTemplate.update(
            """
                DELETE FROM vedtak
                WHERE id = :id
                AND fnr = :fnr;
            """,
            MapSqlParameterSource()
                .addValue("fnr", fnr)
                .addValue("id", vedtakId)
        )
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
