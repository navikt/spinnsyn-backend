package no.nav.helse.flex.db

import org.springframework.data.annotation.Id
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface UtbetalingRepository : CrudRepository<UtbetalingDbRecord, String> {
    fun findUtbetalingDbRecordsByFnr(fnr: String): List<UtbetalingDbRecord>
    fun existsByUtbetalingId(utbetalingId: String): Boolean
    fun findByLestIsNullAndBrukernotifikasjonSendtIsNullAndUtbetalingIdIsNotNullAndBrukernotifikasjonUtelattIsNull(): List<UtbetalingDbRecord>
    @Query(
        """
        select id
        from utbetaling utbetaling
        inner join (
            select count(utbetaling_id) as antall, utbetaling_id
            from vedtak_v2
            group by utbetaling_id
        ) vedtak on vedtak.utbetaling_id = utbetaling.utbetaling_id
        where vedtak.antall = utbetaling.antall_vedtak
        and motatt_publisert is null;
        """
    )
    fun utbetalingerKlarTilVarsling(): List<String>

    @Query(
        """
        select id
        from utbetaling
        where motatt_publisert is null 
        limit 1000;
        """
    )
    fun findIdByMotattPublisertIsNull(): List<String>
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
