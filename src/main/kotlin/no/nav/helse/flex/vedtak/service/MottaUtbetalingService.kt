package no.nav.helse.flex.vedtak.service

import no.nav.helse.flex.logger
import no.nav.helse.flex.vedtak.db.UtbetalingDbRecord
import no.nav.helse.flex.vedtak.db.UtbetalingRepository
import no.nav.helse.flex.vedtak.domene.tilUtbetalingUtbetalt
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class MottaUtbetalingService(
    private val utbetalingRepository: UtbetalingRepository,
) {
    val log = logger()

    fun handterMelding(cr: ConsumerRecord<String, String>) {

        val utbetalingSerialisert = try {
            cr.value().tilUtbetalingUtbetalt()
        } catch (e: Exception) {
            throw RuntimeException("Kunne ikke deserialisere utbetaling", e)
        }

        val utbetaling = utbetalingRepository.save(
            UtbetalingDbRecord(
                fnr = cr.key(),
                utbetalingType = utbetalingSerialisert.type,
                utbetaling = cr.value(),
                opprettet = Instant.now(),
                utbetalingId = utbetalingSerialisert.utbetalingId
            )
        )

        log.info("Opprettet utbetaling med database id: ${utbetaling.id} og utbetaling id ${utbetaling.utbetalingId}")
    }
}
