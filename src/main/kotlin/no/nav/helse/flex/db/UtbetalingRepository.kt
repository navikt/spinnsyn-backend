package no.nav.helse.flex.db

import org.springframework.data.annotation.Id
import org.springframework.data.jdbc.repository.query.Modifying
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface UtbetalingRepository : CrudRepository<UtbetalingDbRecord, String> {
    fun findUtbetalingDbRecordsByFnr(fnr: String): List<UtbetalingDbRecord>
    fun existsByUtbetalingId(utbetalingId: String): Boolean

    @Query(
        """
        SELECT id
        FROM utbetaling utbetaling
        INNER JOIN (
            SELECT count(utbetaling_id) AS antall, utbetaling_id
            FROM vedtak_v2
            GROUP BY utbetaling_id
        ) vedtak ON vedtak.utbetaling_id = utbetaling.utbetaling_id
        WHERE vedtak.antall = utbetaling.antall_vedtak
        AND motatt_publisert IS NULL;
        """
    )
    fun utbetalingerKlarTilVarsling(): List<String>

    @Modifying
    @Query(
        """
        UPDATE utbetaling
        SET lest = :lest
        WHERE fnr = :fnr
        AND id = :id
        AND lest IS NULL
        """
    )
    fun updateLestByFnrAndId(lest: Instant, fnr: String, id: String): Boolean
}

@Table("utbetaling")
data class UtbetalingDbRecord(
    @Id
    val id: String? = null,
    val fnr: String,
    val utbetaling: String,
    val opprettet: Instant,
    val utbetalingId: String,
    val utbetalingType: String,
    val antallVedtak: Int,
    val lest: Instant? = null,
    val brukernotifikasjonSendt: Instant? = null,
    val brukernotifikasjonUtelatt: Instant? = null,
    val varsletMed: String? = null,
    val motattPublisert: Instant? = null,
)
