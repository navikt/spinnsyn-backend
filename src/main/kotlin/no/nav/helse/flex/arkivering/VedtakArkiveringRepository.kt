package no.nav.helse.flex.arkivering

import no.nav.helse.flex.cronjob.LeaderElection
import no.nav.helse.flex.logger
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.util.concurrent.TimeUnit

@Repository
class VedtakArkiveringRepository(
    private val jdbcTemplate: JdbcTemplate,
    private val namedParameterJdbcTemplate: NamedParameterJdbcTemplate,
    val leaderElection: LeaderElection,
) {

    private val log = logger()

    @Scheduled(initialDelay = 30L, fixedDelay = 180L, timeUnit = TimeUnit.SECONDS)
    fun resetRetroVedtak() {
        log.info("Kjører reset av arkiverte vedtak.")
        if (leaderElection.isLeader()) {
            log.info("Er leder, resetter arkiverte vedtak.")
            try {
                val sql = """
                UPDATE vedtak    
                SET arkivert = FALSE
                """
                jdbcTemplate.update(sql)
            } catch (e: Exception) {
                log.error("Feil ved reset av arkiverte retro vedtak: ", e)
            }
        } else {
            log.info("Er ikke leader.")
        }
    }

    fun hent100RetroVedtak(): List<VedtakArkiveringDTO> {
        val sql = """
            SELECT id, fnr
            FROM vedtak 
            WHERE arkivert IS FALSE
            LIMIT 100
            """
        return jdbcTemplate.query(sql, vedtakRowMapper)
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

    fun hent100Utbetalinger(): List<VedtakArkiveringDTO> {
        val sql = """
            SELECT utbetaling_id AS id, fnr 
            FROM utbetaling 
            WHERE arkivert IS FALSE
            LIMIT 100
            """
        return jdbcTemplate.query(sql, vedtakRowMapper)
    }

    fun settUtbetalingerTilArkivert(vedtak: List<String>) {
        val sql = """
            UPDATE utbetaling 
            SET arkivert = TRUE 
            WHERE utbetaling_id = :id
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
