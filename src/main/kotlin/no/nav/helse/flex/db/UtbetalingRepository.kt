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
    @Query("""select ID from UTBETALING where ANTALL_VEDTAK = -1 LIMIT :batchSize;""")
    fun utbetalingerUtenAntallVedtakSatt(@Param("batchSize") batchSize: Int): List<String>
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
    // val varsletMed: String,
)
