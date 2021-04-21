package no.nav.helse.flex.vedtak.db
/*

import no.nav.helse.flex.db.DatabaseInterface
import no.nav.helse.flex.db.toList
import no.nav.helse.flex.vedtak.domene.AnnulleringDto
import no.nav.helse.flex.vedtak.domene.tilAnnulleringDto
import org.postgresql.util.PGobject
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID

fun DatabaseInterface.finnAnnullering(fnr: String): List<Annullering> =
    connection.use { connection ->
        return connection.prepareStatement(
            """
            SELECT id, fnr, annullering, opprettet
            FROM annullering
            WHERE fnr = ?;
            """
        ).use {
            it.setString(1, fnr)
            it.executeQuery().toList { toAnnullering() }
        }
    }

fun DatabaseInterface.finnAnnullering(fnr: String, id: String): Annullering? =
    connection.use { connection ->
        return connection.prepareStatement(
            """
            SELECT id, fnr, annullering, opprettet
            FROM annullering
            WHERE id = ?
            AND fnr = ?;
            """
        ).use {
            it.setString(1, id)
            it.setString(2, fnr)
            it.executeQuery()
                .toList { toAnnullering() }
                .firstOrNull()
        }
    }

fun DatabaseInterface.opprettAnnullering(id: UUID, fnr: String, annullering: String, opprettet: Instant): Annullering {
    finnAnnullering(id = id.toString(), fnr = fnr)?.let { return it }
    connection.use { connection ->
        connection.prepareStatement(
            """
            INSERT INTO ANNULLERING(id, fnr, annullering, opprettet) VALUES (?, ?, ?, ?)
        """
        ).use {
            it.setString(1, id.toString())
            it.setString(2, fnr)
            it.setObject(3, PGobject().also { it.type = "json"; it.value = annullering })
            it.setTimestamp(4, Timestamp.from(opprettet))

            it.executeUpdate()
        }

        connection.commit()
        return Annullering(id = id.toString(), fnr = fnr, annullering = annullering.tilAnnulleringDto(), opprettet = opprettet)
    }
}

fun DatabaseInterface.slettAnnulleringer(fnr: String) {
    connection.use { connection ->
        connection.prepareStatement(
            """
                DELETE FROM ANNULLERING
                WHERE fnr = ?;
            """
        ).use {
            it.setString(1, fnr)
            it.execute()
        }
        connection.commit()
    }
}

data class Annullering(
    val id: String,
    val fnr: String,
    val annullering: AnnulleringDto,
    val opprettet: Instant
)

private fun ResultSet.toAnnullering(): Annullering =
    Annullering(
        id = getString("id"),
        fnr = getString("fnr"),
        annullering = getString("annullering").tilAnnulleringDto(),
        opprettet = getObject("opprettet", OffsetDateTime::class.java).toInstant()
    )
*/
