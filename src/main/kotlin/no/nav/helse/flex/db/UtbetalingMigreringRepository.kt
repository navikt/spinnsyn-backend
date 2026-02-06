package no.nav.helse.flex.db

import org.springframework.data.annotation.Id
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface UtbetalingMigreringRepository : CrudRepository<UtbetalingMigreringDbRecord, Int> {
    @Query(
        """
        SELECT *
        FROM temp_utbetalinger_migrering
        WHERE status = :status
        FOR UPDATE SKIP LOCKED
        LIMIT :batchSize
    """,
    )
    fun findNextBatch(
        @Param("status") status: MigrertStatus = MigrertStatus.IKKE_MIGRERT,
        @Param("batchSize") batchSize: Int = 500,
    ): List<UtbetalingMigreringDbRecord>

    fun findByUtbetalingIdIn(utbetalingIder: List<String>): List<UtbetalingMigreringDbRecord>
}

@Table("temp_utbetalinger_migrering")
data class UtbetalingMigreringDbRecord(
    @Id
    val Id: Int? = null,
    val utbetalingId: String,
    val status: MigrertStatus,
)

enum class MigrertStatus {
    IKKE_MIGRERT,
    MIGRERT,
    FEILET,
}
