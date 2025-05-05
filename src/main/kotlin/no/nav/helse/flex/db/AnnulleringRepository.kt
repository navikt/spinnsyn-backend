package no.nav.helse.flex.db

import no.nav.helse.flex.domene.AnnulleringDto
import no.nav.helse.flex.domene.tilAnnulleringDto
import org.postgresql.util.PGobject
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.time.OffsetDateTime
import java.util.*

@Transactional
@Repository
class AnnulleringDAO(
    private val namedParameterJdbcTemplate: NamedParameterJdbcTemplate,
) {
    fun finnAnnullering(fnr: String): List<Annullering> =
        namedParameterJdbcTemplate.query(
            """
            SELECT id, fnr, annullering, opprettet, kilde
            FROM annullering
            WHERE fnr = :fnr
            """,
            MapSqlParameterSource()
                .addValue("fnr", fnr),
        ) { resultSet, _ ->
            resultSet.toAnnullering()
        }

    fun finnAnnulleringMedIdent(fnr: List<String>): List<Annullering> =
        namedParameterJdbcTemplate.query(
            """
            SELECT id, fnr, annullering, opprettet, kilde
            FROM annullering
            WHERE fnr in (:fnr)
            """,
            MapSqlParameterSource()
                .addValue("fnr", fnr),
        ) { resultSet, _ ->
            resultSet.toAnnullering()
        }

    fun opprettAnnullering(
        id: UUID,
        fnr: String,
        annullering: String,
        opprettet: Instant,
        kilde: String,
    ) {
        val annulleringJSON =
            PGobject().also {
                it.type = "json"
                it.value = annullering
            }

        namedParameterJdbcTemplate.update(
            """
            INSERT INTO ANNULLERING(id, fnr, annullering, opprettet, kilde)
            VALUES (:id, :fnr, :annullering, :opprettet, :kilde)
        """,
            MapSqlParameterSource()
                .addValue("id", id)
                .addValue("fnr", fnr)
                .addValue("annullering", annulleringJSON)
                .addValue("opprettet", Timestamp.from(opprettet))
                .addValue("kilde", kilde),
        )
    }

    fun slettAnnulleringer(fnr: String) {
        namedParameterJdbcTemplate.update(
            """
                DELETE FROM ANNULLERING
                WHERE fnr = :fnr;
            """,
            MapSqlParameterSource()
                .addValue("fnr", fnr),
        )
    }
}

data class Annullering(
    val id: String,
    val fnr: String,
    val annullering: AnnulleringDto,
    val opprettet: Instant,
    val kilde: String,
)

private fun ResultSet.toAnnullering(): Annullering =
    Annullering(
        id = getString("id"),
        fnr = getString("fnr"),
        annullering = getString("annullering").tilAnnulleringDto(),
        opprettet = getObject("opprettet", OffsetDateTime::class.java).toInstant(),
        kilde = getString("kilde"),
    )
