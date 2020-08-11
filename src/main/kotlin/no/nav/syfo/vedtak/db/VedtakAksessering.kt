package no.nav.syfo.vedtak.db

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.db.toList
import no.nav.syfo.objectMapper
import java.sql.Connection
import java.sql.ResultSet

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
           SET lest = true
           WHERE fnr = ?
           AND id = ?
           AND lest = false
        """
    ).use {
        it.setString(1, fnr)
        it.setString(2, vedtaksId)
        it.executeUpdate()
        it.updateCount > 0
    }
    this.commit()
    return retur
}

fun ResultSet.toVedtak(): Vedtak =
    Vedtak(
        id = getString("id"),
        lest = getBoolean("lest"),
        vedtak = objectMapper.readValue(getString("vedtak"))
    )

data class Vedtak(
    val id: String,
    val lest: Boolean,
    val vedtak: Any
)
