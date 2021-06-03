package no.nav.helse.flex.db

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Transactional
@Repository
class VedtakTestDAO(
    private val namedParameterJdbcTemplate: NamedParameterJdbcTemplate
) {
    fun finnVedtakEtterMigrering(fnr: String): List<Vedtak> {
        return namedParameterJdbcTemplate.query(
            """
            SELECT id, vedtak, lest, opprettet
            FROM vedtak
            WHERE fnr = :fnr
            AND mottatt_etter_migrering = True
            """,
            MapSqlParameterSource()
                .addValue("fnr", fnr)
        ) { resultSet, _ ->
            resultSet.toVedtak()
        }
    }

    fun merkVedtakMottattFørMigrering(vedtaksId: String): Boolean {
        val update = namedParameterJdbcTemplate.update(
            """
                   UPDATE vedtak
                   SET mottatt_etter_migrering = false
                   WHERE id = :id
                   AND lest is null
                """,
            MapSqlParameterSource()
                .addValue("id", vedtaksId)
        )

        return update > 0
    }
}
