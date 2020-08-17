package no.nav.syfo.vedtak.db

import no.nav.syfo.db.DatabaseInterface
import org.postgresql.util.PGobject
import java.sql.Connection
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

fun DatabaseInterface.opprettVedtak(vedtak: String, fnr: String): String =
    connection.use {
        return it.opprettVedtak(vedtak = vedtak, fnr = fnr)
    }

private fun Connection.opprettVedtak(vedtak: String, fnr: String): String {
    val id = UUID.randomUUID().toString()

    this.prepareStatement(
        """
                    INSERT INTO VEDTAK(id, fnr, vedtak, opprettet) VALUES (?, ?, ?, ?)
                """
    ).use {
        it.setString(1, id)
        it.setString(2, fnr)
        it.setObject(3, PGobject().also { it.type = "json"; it.value = vedtak })
        it.setTimestamp(4, Timestamp.from(Instant.now()))

        it.executeUpdate()
    }

    this.commit()
    return id
}
