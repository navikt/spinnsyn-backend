package no.nav.helse.flex.db

import org.springframework.data.annotation.Id
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface VedtakRepository : CrudRepository<VedtakDbRecord, String> {
    fun findVedtakDbRecordsByFnr(fnr: String): List<VedtakDbRecord>

    fun existsByUtbetalingId(utbetalingId: String): Boolean

    @Query(
        """
            SELECT *
            FROM vedtak_v2
            WHERE fnr in (:identer)
        """,
    )
    fun findVedtakDbRecordsByIdenter(identer: List<String>): List<VedtakDbRecord>

    @Query(
        """
        SELECT utbetaling_id
        FROM vedtak_v2
        WHERE utbetaling_id in (:utbetalingIder)
        """,
    )
    fun hentUtbetalingIdForVedtakMedUtbetalingId(utbetalingIder: List<String>): List<String>

    fun findByUtbetalingIdIn(utbetalingIds: List<String>): List<VedtakDbRecord>
}

@Table("vedtak_v2")
data class VedtakDbRecord(
    @Id
    val id: String? = null,
    val fnr: String,
    val vedtak: String,
    val opprettet: Instant,
    val utbetalingId: String?,
)
