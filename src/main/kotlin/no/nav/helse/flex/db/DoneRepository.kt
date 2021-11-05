package no.nav.helse.flex.db

import org.springframework.data.annotation.Id
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface DoneRepository : CrudRepository<SkalDonesDbRecord, String> {
    @Query(
        """
        SELECT id, fnr FROM utbetaling 
        WHERE brukernotifikasjon_sendt > '2021-11-05 06:30:30+00' 
          AND brukernotifikasjon_sendt < '2021-11-05 09:00:00+00' 
        """
    )
    fun findAlleSomSkalDones(): List<SkalDonesDbRecord>
}

@Table("utbetaling")
data class SkalDonesDbRecord(
    @Id
    val id: String,
    val fnr: String,
)
