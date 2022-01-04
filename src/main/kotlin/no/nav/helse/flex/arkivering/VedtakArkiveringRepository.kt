package no.nav.helse.flex.arkivering

import no.nav.helse.flex.logger
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet

@Repository
class VedtakArkiveringRepository(
    private val namedParameterJdbcTemplate: NamedParameterJdbcTemplate,
) {

    private val log = logger()

    fun hentRetroVedtak(batchSize: Int): List<VedtakArkiveringDTO> {
        val params = MapSqlParameterSource("batchSize", batchSize)
        val sql = """
            SELECT id, fnr
            FROM vedtak 
            WHERE arkivert IS FALSE
            LIMIT :batchSize
            """
        return namedParameterJdbcTemplate.query(sql, params, vedtakRowMapper)
    }

    fun settRetroVedtakTilArkivert(vedtak: List<String>) {
        val sql = """
            UPDATE vedtak 
            SET arkivert = TRUE 
            WHERE id = :id
            """

        val vedtakSomArray = vedtak.map { id -> mapOf("id" to id) }.toTypedArray()
        namedParameterJdbcTemplate.batchUpdate(sql, vedtakSomArray)

        log.info("Satt ${vedtak.size} retro vedtak til arkivert.")
    }

    fun hentUtbetalinger(batchSize: Int): List<VedtakArkiveringDTO> {
        val params = MapSqlParameterSource("batchSize", batchSize)
        val sql = """
            SELECT id, fnr
            FROM utbetaling
            WHERE arkivert IS FALSE
            LIMIT :batchSize
            """
        return namedParameterJdbcTemplate.query(sql, params, vedtakRowMapper)
    }

    fun settUtbetalingerTilArkivert(vedtak: List<String>) {
        val sql = """
            UPDATE utbetaling 
            SET arkivert = TRUE 
            WHERE id = :id
            """

        val utbetalingerSomArray = vedtak.map { id -> mapOf("id" to id) }.toTypedArray()
        namedParameterJdbcTemplate.batchUpdate(sql, utbetalingerSomArray)

        log.info("Satt ${vedtak.size} utbetalinger til arkivert.")
    }

    private val vedtakRowMapper = { rs: ResultSet, _: Int ->
        VedtakArkiveringDTO(rs.getString("id"), rs.getString("fnr"))
    }
}

data class VedtakArkiveringDTO(
    val id: String,
    val fnr: String,
)
