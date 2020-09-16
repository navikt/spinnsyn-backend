package no.nav.syfo.vedtak.db

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.toList
import no.nav.syfo.objectMapper
import org.postgresql.util.PGobject
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDateTime
import java.util.* // ktlint-disable no-wildcard-imports

fun DatabaseInterface.finnVedtak(fnr: String): List<Vedtak> =
    connection.use {
        return it.finnVedtak(fnr)
    }

fun DatabaseInterface.finnVedtak(fnr: String, vedtaksId: String): Vedtak? =
    connection.use {
        return it.finnVedtak(fnr, vedtaksId)
    }

fun DatabaseInterface.eierVedtak(fnr: String, vedtaksId: String): Boolean =
    connection.use {
        return it.eierVedtak(fnr, vedtaksId)
    }

fun DatabaseInterface.lesVedtak(fnr: String, vedtaksId: String): Boolean =
    connection.use {
        return it.lesVedtak(fnr, vedtaksId)
    }

fun DatabaseInterface.opprettVedtak(id: UUID, vedtak: String, fnr: String): Vedtak =
    connection.use {
        it.finnVedtak(fnr = fnr, vedtaksId = id.toString())?.let { return it }
        return it.opprettVedtak(
            id = id,
            vedtak = vedtak,
            fnr = fnr
        )
    }

fun DatabaseInterface.hentVedtakForVarsling(): List<InternVedtak> =
    connection.use {
        return it.hentVedtakForVarsling()
    }

fun DatabaseInterface.hentVedtakForRevarsling(dagerBakITid: Long = 7): List<InternVedtak> =
    connection.use {
        return it.hentVedtakForRevarsling(dagerBakITid)
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

private fun Connection.opprettVedtak(id: UUID, vedtak: String, fnr: String): Vedtak {

    val now = Instant.now()
    this.prepareStatement(
        """
            INSERT INTO VEDTAK(id, fnr, vedtak, opprettet) VALUES (?, ?, ?, ?) 
        """
    ).use {
        it.setString(1, id.toString())
        it.setString(2, fnr)
        it.setObject(3, PGobject().also { it.type = "json"; it.value = vedtak })
        it.setTimestamp(4, Timestamp.from(now))

        it.executeUpdate()
    }

    this.commit()
    return Vedtak(id = id.toString(), vedtak = vedtak, lest = false, opprettet = now)
}

private fun Connection.finnVedtak(fnr: String): List<Vedtak> =
    this.prepareStatement(
        """
            SELECT id, vedtak, lest, opprettet
            FROM vedtak
            WHERE fnr = ?;
            """
    ).use {
        it.setString(1, fnr)
        it.executeQuery().toList { toVedtak() }
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

private fun Connection.lesVedtak(fnr: String, vedtaksId: String): Boolean {
    val retur = this.prepareStatement(
        """
           UPDATE vedtak
           SET lest = ?
           WHERE fnr = ?
           AND id = ?
           AND lest is null
        """
    ).use {
        it.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()))
        it.setString(2, fnr)
        it.setString(3, vedtaksId)
        it.executeUpdate()
        it.updateCount > 0
    }
    this.commit()
    return retur
}

private fun Connection.hentVedtakForVarsling(): List<InternVedtak> =
    this.prepareStatement(
        """
            SELECT id, fnr, lest, opprettet, varslet, revarslet
            FROM vedtak
            WHERE lest IS NULL
            AND varslet IS NULL
            AND revarslet IS NULL
        """
    ).use {
        it.executeQuery().toList {
            toInternVedtak()
        }
    }

private fun Connection.hentVedtakForRevarsling(dagerBakITid: Long): List<InternVedtak> =
    this.prepareStatement(
        """
            SELECT id, fnr, lest, opprettet, varslet, revarslet
            FROM vedtak
            WHERE lest IS NULL
            AND revarslet IS NULL
            AND varslet IS NOT NULL
            AND varslet < ?
        """
    ).use {
        it.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now().minusDays(dagerBakITid)))
        it.executeQuery().toList {
            toInternVedtak()
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
        it.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()))
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
        it.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()))
        it.setString(2, vedtaksId)
        it.executeUpdate()
    }
    this.commit()
}

private fun ResultSet.toVedtak(): Vedtak =
    Vedtak(
        id = getString("id"),
        lest = getObject("lest", Timestamp::class.java) != null,
        vedtak = objectMapper.readValue(getString("vedtak")),
        opprettet = getObject("opprettet", Timestamp::class.java).toInstant()
    )

private fun ResultSet.toInternVedtak(): InternVedtak =
    InternVedtak(
        id = getString("id"),
        fnr = getString("fnr"),
        lest = getObject("lest", Timestamp::class.java)?.toInstant(),
        opprettet = getObject("opprettet", Timestamp::class.java).toInstant(),
        varslet = getObject("varslet", Timestamp::class.java)?.toInstant(),
        revarslet = getObject("revarslet", Timestamp::class.java)?.toInstant()
    )

data class Vedtak(
    val id: String,
    val lest: Boolean,
    val vedtak: Any,
    val opprettet: Instant
)

data class InternVedtak(
    val id: String,
    val fnr: String,
    val lest: Instant?,
    val opprettet: Instant,
    val varslet: Instant?,
    val revarslet: Instant?
)
