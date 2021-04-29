package no.nav.helse.flex.vedtak.db

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface VedtakRepository : CrudRepository<VedtakDbRecord, String> {
    fun findVedtakDbRecordsByFnr(fnr: String): List<VedtakDbRecord>
}

@Table("vedtak_v2")
data class VedtakDbRecord(
    @Id
    val id: String? = null,
    val fnr: String,
    val vedtak: String,
    val opprettet: Instant,
    val utbetalingId: String?,
    val lest: Instant? = null,
)
