package no.nav.syfo.vedtak.db

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.db.toList
import no.nav.syfo.objectMapper
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.LocalDateTime

fun Connection.finnVedtak(fnr: String): List<Vedtak> =
    this.prepareStatement(
        """
            SELECT id, vedtak, lest
            FROM vedtak
            WHERE fnr = ?;
            """
    ).use {
        it.setString(1, fnr)
        it.executeQuery().toList { toVedtak() }
    }

fun Connection.finnVedtak(fnr: String, vedtaksId: String): Vedtak? {
    return this.prepareStatement(
        """
            SELECT id, vedtak, lest
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

fun Connection.eierVedtak(fnr: String, vedtaksId: String): Boolean =
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

fun Connection.lesVedtak(fnr: String, vedtaksId: String): Boolean {
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

fun ResultSet.toVedtak(): Vedtak =
    Vedtak(
        id = getString("id"),
        lest = getObject("lest", Timestamp::class.java) != null,
        vedtak = objectMapper.readValue(getString("vedtak"))
    )

data class Vedtak(
    val id: String,
    val lest: Boolean,
    val vedtak: Any
)
