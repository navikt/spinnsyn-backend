package no.nav.helse.flex.done

import no.nav.helse.flex.logger
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet

@Repository
class DoneUlesteVedtakRepository(
    private val namedParameterJdbcTemplate: NamedParameterJdbcTemplate,
) {

    private val log = logger()

    fun hentUleste(batchSize: Int): List<UlesteVedtakDTO> {
        val params = MapSqlParameterSource("batchSize", batchSize)
        val sql = """
            SELECT id, fnr FROM done_vedtak
            WHERE done_sendt IS NULL
            LIMIT :batchSize
            """
        return namedParameterJdbcTemplate.query(sql, params, ulesteRowMapper)
    }

    fun settDoneTidspunkt(vedtakMedDoneTidspunkt: List<String>) {
        val sql = """
            UPDATE done_vedtak
            SET done_sendt = CURRENT_TIMESTAMP 
            WHERE id = :id
            """

        val vedtakSomArray = vedtakMedDoneTidspunkt.map { id -> mapOf("id" to id) }.toTypedArray()
        namedParameterJdbcTemplate.batchUpdate(sql, vedtakSomArray)

        log.info("Satt done-tidspunkt for ${vedtakMedDoneTidspunkt.size} uleste vedtak.")
    }

    private val ulesteRowMapper = { rs: ResultSet, _: Int ->
        UlesteVedtakDTO(rs.getString("id"), rs.getString("fnr"))
    }
}

data class UlesteVedtakDTO(
    val id: String,
    val fnr: String,
)
