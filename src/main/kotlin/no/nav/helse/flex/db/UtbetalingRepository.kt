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

    @Query(
        """
        SELECT *
        FROM utbetaling
        WHERE fnr in (:identer)
        """
    )
    fun findUtbetalingDbRecordsByIdent(identer: List<String>): List<UtbetalingDbRecord>
    fun existsByUtbetalingId(utbetalingId: String): Boolean

    @Query(
        """
        SELECT id, utbetaling_id, antall_vedtak, fnr
        FROM utbetaling
        WHERE skal_vises_til_bruker IS NULL
        AND motatt_publisert IS NULL;
        """
    )
    fun utbetalingerKlarTilVarsling(): List<UtbetalingerKlarTilVarsling>

    @Modifying
    @Query(
        """
        UPDATE utbetaling
        SET lest = :lest
        WHERE id = :id
        AND fnr in (:identer)
        AND lest IS NULL
        """
    )
    fun updateLestByIdentAndId(lest: Instant, identer: List<String>, id: String): Boolean

    @Modifying
    @Query(
        """
        UPDATE utbetaling
        SET motatt_publisert = :motattPublisert, skal_vises_til_bruker = :skalVisesTilBruker
        WHERE id = :id
        """
    )
    fun settSkalVisesOgMotattPublisert(motattPublisert: Instant?, skalVisesTilBruker: Boolean?, id: String): Boolean
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
    val motattPublisert: Instant? = null,
    val skalVisesTilBruker: Boolean? = null
)

data class UtbetalingerKlarTilVarsling(
    val id: String,
    val utbetalingId: String,
    val antallVedtak: Int,
    val fnr: String
)
