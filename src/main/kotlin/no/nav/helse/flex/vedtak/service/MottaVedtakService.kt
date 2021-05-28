package no.nav.helse.flex.vedtak.service

import no.nav.helse.flex.config.EnvironmentToggles
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
    private val environmentToggles: EnvironmentToggles
) {
    val log = logger()

    fun handterMelding(cr: ConsumerRecord<String, String>) {
        mottaVedtak(
            fnr = cr.key(),
            vedtak = cr.value(),
        )
    }

    fun mottaVedtak(fnr: String, vedtak: String) {
        val vedtakSerialisert = try {
            vedtak.tilVedtakFattetForEksternDto()
        } catch (e: Exception) {
            throw RuntimeException("Kunne ikke deserialisere vedtak", e)
        }

        val lest = if (environmentToggles.isProduction()) {
            Instant.EPOCH
        } else {
            null
        }

        val vedtakDB = vedtakRepository.save(
            VedtakDbRecord(
                fnr = fnr,
                vedtak = vedtak,
                opprettet = Instant.now(),
                utbetalingId = vedtakSerialisert.utbetalingId,
                lest = lest
            )
        )
        log.info("Toggle sier ${environmentToggles.isProduction()} . lest settes til $lest for vedtak ${vedtakDB.id}")

        log.info("Opprettet vedtak med database id: ${vedtakDB.id} for utbetaling id ${vedtakDB.utbetalingId}")
    }
}
