package no.nav.helse.flex.vedtak.db

import no.nav.helse.flex.db.DatabaseInterface
import no.nav.helse.flex.db.toList
import no.nav.helse.flex.vedtak.domene.VedtakDto
import no.nav.helse.flex.vedtak.domene.tilVedtakDto
import org.postgresql.util.PGobject
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

fun DatabaseInterface.finnVedtak(fnr: String): List<Vedtak> =
    connection.use {
        return it.finnVedtak(fnr)
    }

fun DatabaseInterface.finnInternVedtak(fnr: String, vedtaksId: String): InternVedtak? =
    connection.use {
        return it.finnInternVedtak(fnr, vedtaksId)
    }

fun DatabaseInterface.eierVedtak(fnr: String, vedtaksId: String): Boolean =
    connection.use {
        return it.eierVedtak(fnr, vedtaksId)
    }

fun DatabaseInterface.lesVedtak(fnr: String, vedtaksId: String): Boolean =
    connection.use {
        return it.lesVedtak(fnr, vedtaksId)
    }

fun DatabaseInterface.opprettVedtak(id: UUID, vedtak: String, fnr: String, lest: Boolean, opprettet: Instant): Vedtak =
    connection.use {
        return it.opprettVedtak(
            id = id,
            vedtak = vedtak,
            fnr = fnr,
            lest = lest,
            opprettet = opprettet
        )
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

private fun Connection.opprettVedtak(id: UUID, vedtak: String, fnr: String, lest: Boolean, opprettet: Instant): Vedtak {

    this.prepareStatement(
        """
            INSERT INTO VEDTAK(id, fnr, vedtak, opprettet, lest) VALUES (?, ?, ?, ?, ?) 
        """
    ).use {
        it.setString(1, id.toString())
        it.setString(2, fnr)
        it.setObject(3, PGobject().also { it.type = "json"; it.value = vedtak })
        it.setTimestamp(4, Timestamp.from(opprettet))
        if (lest) {
            it.setTimestamp(5, Timestamp.from(opprettet))
        } else {
            it.setTimestamp(5, null)
        }

        it.executeUpdate()
    }

    this.commit()
    return Vedtak(id = id.toString(), vedtak = vedtak.tilVedtakDto(), lest = lest, opprettet = opprettet)
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
        it.setTimestamp(1, Timestamp.from(Instant.now()))
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

private fun ResultSet.toVedtak(): Vedtak =
    Vedtak(
        id = getString("id"),
        lest = getObject("lest", OffsetDateTime::class.java) != null,
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

data class Vedtak(
    val id: String,
    val lest: Boolean,
    val vedtak: VedtakDto,
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
