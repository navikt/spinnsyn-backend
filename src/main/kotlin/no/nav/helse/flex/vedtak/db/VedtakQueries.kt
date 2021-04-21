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
}
/*

fun DatabaseInterface.finnInternVedtak(fnr: String, vedtaksId: String): InternVedtak? =
    connection.use {
        return it.finnInternVedtak(fnr, vedtaksId)
    }

fun DatabaseInterface.eierVedtak(fnr: String, vedtaksId: String): Boolean =
    connection.use {
        return it.eierVedtak(fnr, vedtaksId)
    }


fun DatabaseInterface.hentVedtakForVarsling(): List<InternVedtak> =
    connection.use {
        return it.hentVedtakForVarsling()
    }

fun DatabaseInterface.hentVedtakForRevarsling(): List<InternVedtak> =
    connection.use {
        return it.hentVedtakForRevarsling()
    }

fun DatabaseInterface.settVedtakVarslet(vedtaksId: String) {
    connection.use {
        return it.settVedtakVarslet(vedtaksId)
    }
}

fun DatabaseInterface.settVedtakRevarslet(vedtaksId: String) {
    connection.use {
        return it.settVedtakRevarslet(vedtaksId)
    }
}

fun DatabaseInterface.hentVedtakEldreEnnTolvMnd(): List<InternVedtak> =
    connection.use {
        return it.hentVedtakEldreEnnTolvMnd()
    }

fun DatabaseInterface.slettVedtak(vedtakId: String, fnr: String) {
    connection.use {
        return it.slettVedtak(vedtakId, fnr)
    }
}

private fun Connection.finnVedtak(fnr: String, vedtaksId: String): Vedtak? {
    return this.prepareStatement(
        """
            SELECT id, vedtak, lest, opprettet
            FROM vedtak
            WHERE fnr = ?
            AND id = ?;
        """
    ).use {
        it.setString(1, fnr)
        it.setString(2, vedtaksId)
        it.executeQuery()
            .toList { toVedtak() }
            .firstOrNull()
    }
}

private fun Connection.finnInternVedtak(fnr: String, vedtaksId: String): InternVedtak? {
    return this.prepareStatement(
        """
            SELECT id, fnr, lest, opprettet, varslet, revarslet
            FROM vedtak
            WHERE fnr = ?
            AND id = ?;
            """
    ).use {
        it.setString(1, fnr)
        it.setString(2, vedtaksId)
        it.executeQuery()
            .toList { toInternVedtak() }
            .firstOrNull()
    }
}

private fun Connection.eierVedtak(fnr: String, vedtaksId: String): Boolean =
    this.prepareStatement(
        """
            SELECT id
            FROM vedtak
            WHERE fnr = ?
            AND id = ?;
        """
    ).use {
        it.setString(1, fnr)
        it.setString(2, vedtaksId)
        it.executeQuery().toList {
            getString("id")
        }.size > 0
    }

private fun Connection.hentVedtakForVarsling(): List<InternVedtak> =
    this.prepareStatement(
        """
            SELECT id, fnr, lest, opprettet, varslet, revarslet
            FROM vedtak
            WHERE lest IS NULL
            AND varslet IS NULL
            AND revarslet IS NULL
            AND opprettet < ?
        """
    ).use {
        it.setTimestamp(
            1,
            Timestamp.from(
                ZonedDateTime.ofInstant(Instant.now(), ZoneId.of("Europe/Oslo")).withHour(0).withMinute(0).toInstant()
            )
        )
        it.executeQuery().toList {
            toInternVedtak()
        }
    }

private fun Connection.hentVedtakForRevarsling(): List<InternVedtak> {
    return this.prepareStatement(
        """
            SELECT id, fnr, lest, opprettet, varslet, revarslet
            FROM vedtak
            WHERE lest IS NULL
            AND revarslet IS NULL
            AND varslet IS NOT NULL
            AND varslet < ?
        """
    ).use {
        it.setTimestamp(1, Timestamp.from(Instant.now().minus(7, ChronoUnit.DAYS)))
        it.executeQuery().toList {
            toInternVedtak()
        }
    }
}

private fun Connection.settVedtakVarslet(vedtaksId: String) {
    this.prepareStatement(
        """
        UPDATE vedtak
        SET varslet = ?
        WHERE id = ?
        AND varslet IS NULL
        """
    ).use {
        it.setTimestamp(1, Timestamp.from(Instant.now()))
        it.setString(2, vedtaksId)
        it.executeUpdate()
    }
    this.commit()
}

private fun Connection.settVedtakRevarslet(vedtaksId: String) {
    this.prepareStatement(
        """
        UPDATE vedtak
        SET revarslet = ?
        WHERE id = ?
        AND varslet IS NOT NULL
        AND revarslet IS NULL
        """
    ).use {
        it.setTimestamp(1, Timestamp.from(Instant.now()))
        it.setString(2, vedtaksId)
        it.executeUpdate()
    }
    this.commit()
}

private fun Connection.hentVedtakEldreEnnTolvMnd(): List<InternVedtak> =
    this.prepareStatement(
        """
            SELECT id, fnr, lest, opprettet, varslet, revarslet
            FROM vedtak
            WHERE opprettet < ?
        """
    ).use {
        it.setTimestamp(1, Timestamp.from(Instant.now().minus(365, ChronoUnit.DAYS)))
        it.executeQuery().toList {
            toInternVedtak()
        }
    }

private fun Connection.slettVedtak(vedtakId: String, fnr: String) {
    this.prepareStatement(
        """
                DELETE FROM vedtak
                WHERE id = ?
                AND fnr = ?;
            """
    ).use {
        it.setString(1, vedtakId)
        it.setString(2, fnr)
        it.execute()
    }
    this.commit()
}

 */

private fun ResultSet.toVedtak(): Vedtak =
    Vedtak(
        id = getString("id"),
        lest = getObject("lest", OffsetDateTime::class.java) != null,
        lestDato = getObject("lest", OffsetDateTime::class.java),
        vedtak = getString("vedtak").tilVedtakDto(),
        opprettet = getObject("opprettet", OffsetDateTime::class.java).toInstant()
    )

private fun ResultSet.toInternVedtak(): InternVedtak =
    InternVedtak(
        id = getString("id"),
        fnr = getString("fnr"),
        lest = getObject("lest", OffsetDateTime::class.java)?.toInstant(),
        opprettet = getObject("opprettet", OffsetDateTime::class.java).toInstant(),
        varslet = getObject("varslet", OffsetDateTime::class.java)?.toInstant(),
        revarslet = getObject("revarslet", OffsetDateTime::class.java)?.toInstant()
    )
