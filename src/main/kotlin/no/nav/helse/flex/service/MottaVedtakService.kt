package no.nav.helse.flex.service

import no.nav.helse.flex.db.VedtakDbRecord
import no.nav.helse.flex.db.VedtakRepository
import no.nav.helse.flex.domene.tilVedtakFattetForEksternDto
import no.nav.helse.flex.logger
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class MottaVedtakService(
    private val vedtakRepository: VedtakRepository,
) {
    val log = logger()

    fun handterMelding(cr: ConsumerRecord<String, String>) {
        mottaVedtak(
            fnr = cr.key(),
            vedtak = cr.value(),
            timestamp = Instant.ofEpochMilli(cr.timestamp())
        )
    }

    fun mottaVedtak(fnr: String, vedtak: String, timestamp: Instant) {
        val vedtakSerialisert = try {
            vedtak.tilVedtakFattetForEksternDto()
        } catch (e: Exception) {
            throw RuntimeException("Kunne ikke deserialisere vedtak", e)
        }

        vedtakSerialisert.utbetalingId?.let {
            if (vedtakRepository.existsByUtbetalingId(it)) {
                log.warn("Vedtak med utbetaling id $it eksisterer allerede")
                return
            }
        }

        val vedtakDB = vedtakRepository.save(
            VedtakDbRecord(
                fnr = fnr,
                vedtak = vedtak,
                opprettet = Instant.now(),
                utbetalingId = vedtakSerialisert.utbetalingId,
                lest = null
            )
        )

        log.info("Opprettet vedtak med database id: ${vedtakDB.id} for utbetaling id ${vedtakDB.utbetalingId}")
    }
}
