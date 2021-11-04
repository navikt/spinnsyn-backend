package no.nav.helse.flex.db

import org.springframework.data.annotation.Id
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface UtbetalingRepository : CrudRepository<UtbetalingDbRecord, String> {
    fun findUtbetalingDbRecordsByFnr(fnr: String): List<UtbetalingDbRecord>
    fun existsByUtbetalingId(utbetalingId: String): Boolean
    @Query(
        """
        select id
        from utbetaling ut
        inner join (
            select count(utbetaling_id) as antall, utbetaling_id
            from vedtak_v2
            group by utbetaling_id
        ) vedtak on vedtak.utbetaling_id = ut.utbetaling_id
        where vedtak.antall = ut.antall_vedtak
        and varslet_med is null 
        limit 1000;
        """
    )
    fun utbetalingerSomSkalProsesseres(@Param("batchSize") batchSize: Int): List<String>
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
)
