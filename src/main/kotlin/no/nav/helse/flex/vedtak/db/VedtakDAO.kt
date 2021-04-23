package no.nav.helse.flex.vedtak.db

import no.nav.helse.flex.vedtak.domene.tilVedtakDto
import org.postgresql.util.PGobject
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID

@Transactional
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
            """,
            MapSqlParameterSource()
                .addValue("fnr", fnr)
        ) { resultSet, _ ->
            resultSet.toVedtak()
        }
    }

    fun opprettVedtak(id: UUID, vedtak: String, fnr: String, opprettet: Instant): Vedtak {
        val vedtakJSON = PGobject().also { it.type = "json"; it.value = vedtak }
        val nå = Timestamp.from(Instant.now())

        namedParameterJdbcTemplate.update(
            """
            INSERT INTO VEDTAK(id, fnr, vedtak, opprettet, varslet, revarslet) 
            VALUES (:id, :fnr, :vedtak, :opprettet, :varslet, :revarslet)
        """,
            MapSqlParameterSource()
                .addValue("id", id)
                .addValue("fnr", fnr)
                .addValue("vedtak", vedtakJSON)
                .addValue("opprettet", Timestamp.from(opprettet))
                .addValue("varslet", nå)
                .addValue("revarslet", nå)
        )

        return Vedtak(
            id = id.toString(),
            vedtak = vedtak.tilVedtakDto(),
            lest = false,
            lestDato = null,
            opprettet = opprettet
        )
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

private fun ResultSet.toVedtak(): Vedtak =
    Vedtak(
        id = getString("id"),
        lest = getObject("lest", OffsetDateTime::class.java) != null,
        lestDato = getObject("lest", OffsetDateTime::class.java),
        vedtak = getString("vedtak").tilVedtakDto(),
        opprettet = getObject("opprettet", OffsetDateTime::class.java).toInstant()
    )
