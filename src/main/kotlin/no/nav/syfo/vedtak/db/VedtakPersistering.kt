package no.nav.syfo.vedtak.db

import org.postgresql.util.PGobject
import java.sql.Connection
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

fun Connection.opprettVedtak(vedtak: String, fnr: String): String {
    val id = UUID.randomUUID().toString()
    use { connection ->
        connection.prepareStatement(
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

        connection.commit()
        return id
    }
}
