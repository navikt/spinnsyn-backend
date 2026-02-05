package no.nav.helse.flex.db

import org.springframework.data.annotation.Id
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface UtbetalingMigreringRepository : CrudRepository<UtbetalingMigreringDbRecord, Int> {
    fun findFirst500ByStatus(status: MigrertStatus): List<UtbetalingMigreringDbRecord>

    fun findByUtbetalingIdIn(utbetalingIder: List<String>): List<UtbetalingMigreringDbRecord>

    @Query(
        """
        SELECT * FROM temp_utbetalinger_migrering
        WHERE status = 'IKKE_MIGRERT'
        AND (
            (id BETWEEN 1 AND 30) OR
            (id BETWEEN 500000 AND 500030) OR
            (id BETWEEN 3000000 AND 3000030) OR
            (id BETWEEN 1500000 AND 1500030) OR
            (id BETWEEN 2000000 AND 2000030) OR
            (id BETWEEN 2500000 AND 2500030) OR
            (id BETWEEN 3000000 AND 3000030) OR
            (id BETWEEN 3500000 AND 3500030) OR
            (id BETWEEN 4000000 AND 4000030) OR
            (id BETWEEN 4500000 AND 4500030) OR
            (id BETWEEN 5000000 AND 5000030) OR
            (id BETWEEN 5500000 AND 5500030) OR
            (id BETWEEN 6000000 AND 6000030) OR
            (id BETWEEN 6500000 AND 6500030) OR
            (id BETWEEN 7000000 AND 7000030) OR
            (id BETWEEN 7500000 AND 7500030) OR
            (id BETWEEN 8000000 AND 8000030) OR
            (id BETWEEN 8500000 AND 8500030)
        )
        LIMIT 500;
        """,
    )
    fun hentUtdragForDryRun(): List<UtbetalingMigreringDbRecord>
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
