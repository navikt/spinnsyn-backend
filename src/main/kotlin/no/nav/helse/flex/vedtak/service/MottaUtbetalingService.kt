package no.nav.helse.flex.vedtak.service

import no.nav.helse.flex.logger
import no.nav.helse.flex.vedtak.db.UtbetalingDbRecord
import no.nav.helse.flex.vedtak.db.UtbetalingRepository
import no.nav.helse.flex.vedtak.domene.tilUtbetalingUtbetalt
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.*

@Service
class MottaUtbetalingService(
    private val utbetalingRepository: UtbetalingRepository,
) {
    val log = logger()

    fun handterMelding(cr: ConsumerRecord<String, String>) {
        mottaUtbetaling(
            fnr = cr.key(),
            utbetaling = cr.value(),
            opprettet = Instant.now()
        )
    }

    fun mottaUtbetaling(fnr: String, utbetaling: String, opprettet: Instant) {
        val utbetalingSerialisert = try {
            utbetaling.tilUtbetalingUtbetalt()
        } catch (e: Exception) {
            throw RuntimeException("Kunne ikke deserialisere utbetaling", e)
        }

        val utbetalingDB = utbetalingRepository.save(
            UtbetalingDbRecord(
                fnr = fnr,
                utbetalingType = utbetalingSerialisert.type,
                utbetaling = utbetaling,
                opprettet = opprettet,
                utbetalingId = utbetalingSerialisert.utbetalingId
            )
        )

        log.info("Opprettet utbetaling med database id: ${utbetalingDB.id} og utbetaling id ${utbetalingDB.utbetalingId}")
    }
}
