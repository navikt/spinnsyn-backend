package no.nav.helse.flex.db

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface UtbetalingMigreringRepository : CrudRepository<UtbetalingMigreringDbRecord, Int> {
    fun findFirst500ByStatus(status: MigrertStatus): List<UtbetalingMigreringDbRecord>

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
