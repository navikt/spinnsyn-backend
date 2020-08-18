package no.nav.syfo.vedtak.db

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.toList
import no.nav.syfo.objectMapper
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.LocalDate
import java.time.LocalDateTime

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

private fun ResultSet.toVedtak(): Vedtak =
    Vedtak(
        id = getString("id"),
        lest = getObject("lest", Timestamp::class.java) != null,
        vedtak = objectMapper.readValue(getString("vedtak")),
        opprettet = getObject("opprettet", Timestamp::class.java).toLocalDateTime().toLocalDate()
    )

data class Vedtak(
    val id: String,
    val lest: Boolean,
    val vedtak: Any,
    val opprettet: LocalDate
)
