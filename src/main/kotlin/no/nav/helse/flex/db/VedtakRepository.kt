package no.nav.helse.flex.db

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface VedtakRepository : CrudRepository<VedtakDbRecord, String> {
    fun findVedtakDbRecordsByFnr(fnr: String): List<VedtakDbRecord>
    fun existsByUtbetalingId(utbetalingId: String): Boolean
    fun findByLestIsNullAndBrukernotifikasjonSendtIsNullAndUtbetalingIdIsNotNullAndBrukernotifikasjonUtelattIsNull(): List<VedtakDbRecord>
}

@Table("vedtak_v2")
data class VedtakDbRecord(
    @Id
    val id: String? = null,
    val fnr: String,
    val vedtak: String,
    val opprettet: Instant,
    val brukernotifikasjonSendt: Instant? = null,
    val brukernotifikasjonUtelatt: Instant? = null,
    val utbetalingId: String?,
    val lest: Instant? = null,
)
