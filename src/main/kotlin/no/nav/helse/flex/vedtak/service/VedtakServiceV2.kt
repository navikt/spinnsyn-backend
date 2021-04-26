package no.nav.helse.flex.vedtak.service

import no.nav.helse.flex.logger
import no.nav.helse.flex.vedtak.db.VedtakDbRecord
import no.nav.helse.flex.vedtak.db.VedtakRepository
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class VedtakServiceV2(
    private val vedtakRepository: VedtakRepository,
) {
    val log = logger()

    fun handterMelding(cr: ConsumerRecord<String, String>) {
        val vedtak = vedtakRepository.save(
            VedtakDbRecord(
                fnr = cr.key(),
                vedtak = cr.value(),
                opprettet = Instant.now()
            )
        )

        log.info("Opprettet vedtak med database id: ${vedtak.id}")
    }
}
