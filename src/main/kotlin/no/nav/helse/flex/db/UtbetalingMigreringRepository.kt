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
            (id BETWEEN 1 AND 10) OR
            (id BETWEEN 500000 AND 500010) OR
            (id BETWEEN 1000000 AND 1000010) OR
            (id BETWEEN 1500000 AND 1500010) OR
            (id BETWEEN 2000000 AND 2000010) OR
            (id BETWEEN 2500000 AND 2500010) OR
            (id BETWEEN 3000000 AND 3000010) OR
            (id BETWEEN 3500000 AND 3500010) OR
            (id BETWEEN 4000000 AND 4000010) OR
            (id BETWEEN 4500000 AND 4500010) OR
            (id BETWEEN 5000000 AND 5000010) OR
            (id BETWEEN 5500000 AND 5500010) OR
            (id BETWEEN 6000000 AND 6000010) OR
            (id BETWEEN 6500000 AND 6500010) OR
            (id BETWEEN 7000000 AND 7000010) OR
            (id BETWEEN 7500000 AND 7500010) OR
            (id BETWEEN 8000000 AND 8000010) OR
            (id BETWEEN 8500000 AND 8500010)
        )
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
