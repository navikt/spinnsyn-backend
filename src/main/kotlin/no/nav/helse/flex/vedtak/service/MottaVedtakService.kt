package no.nav.helse.flex.vedtak.service

import no.nav.helse.flex.logger
import no.nav.helse.flex.vedtak.db.VedtakDbRecord
import no.nav.helse.flex.vedtak.db.VedtakRepository
import no.nav.helse.flex.vedtak.domene.tilVedtakFattetForEksternDto
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class MottaVedtakService(
    private val vedtakRepository: VedtakRepository,
) {
    val log = logger()

    fun handterMelding(cr: ConsumerRecord<String, String>) {

        val vedtakSerialisert = try {
            cr.value().tilVedtakFattetForEksternDto()
        } catch (e: Exception) {
            throw RuntimeException("Kunne ikke deserialisere vedtak", e)
        }

        val vedtak = vedtakRepository.save(
            VedtakDbRecord(
                fnr = cr.key(),
                vedtak = cr.value(),
                opprettet = Instant.now(),
                utbetalingId = vedtakSerialisert.utbetalingId,
                lest = Instant.EPOCH
            )
        )

        log.info("Opprettet vedtak med database id: ${vedtak.id} for utbetaling id ${vedtak.utbetalingId}")
    }
}
