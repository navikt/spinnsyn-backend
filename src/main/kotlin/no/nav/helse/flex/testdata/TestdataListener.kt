package no.nav.helse.flex.testdata

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.config.EnvironmentToggles
import no.nav.helse.flex.logger
import no.nav.helse.flex.objectMapper
import no.nav.helse.flex.service.MottaUtbetaling
import no.nav.helse.flex.service.MottaVedtak
import no.nav.helse.flex.service.SendVedtakStatus
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.context.annotation.Profile
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component
import java.time.Instant

@Component
@Profile("testdata")
class TestdataListener(
    val environmentToggles: EnvironmentToggles,
    val mottaVedtak: MottaVedtak,
    val mottaUtbetaling: MottaUtbetaling,
    val sendVedtakStatus: SendVedtakStatus,
) {
    val log = logger()

    data class VedtakV2(val vedtak: String, val utbetaling: String?)

    @KafkaListener(
        topics = [TESTDATA_TOPIC],
        containerFactory = "aivenKafkaListenerContainerFactory",
        properties = ["auto.offset.reset = latest"],
    )
    fun listen(
        cr: ConsumerRecord<String, String>,
        acknowledgment: Acknowledgment,
    ) {
        try {
            val fnr = cr.key()
            val vedtakV2: VedtakV2 = objectMapper.readValue(cr.value())

            if (environmentToggles.isProduction()) {
                throw IllegalStateException("Dette apiet er ikke på i produksjon")
            }

            mottaVedtak.mottaVedtak(
                fnr = fnr,
                vedtak = vedtakV2.vedtak,
                timestamp = Instant.now(),
            )
            log.info("Opprettet vedtak fra testadata for periode på fnr: $fnr")

            if (vedtakV2.utbetaling != null) {
                mottaUtbetaling.mottaUtbetaling(
                    fnr = fnr,
                    utbetaling = vedtakV2.utbetaling,
                    opprettet = Instant.now(),
                )
                log.info("Opprettet utbetaling fra testadata for periode på fnr: $fnr")
            }

            sendVedtakStatus.prosesserUtbetalinger()
        } catch (e: Exception) {
            log.error("Feil ved mottak av vedtak testdata", e)
        } finally {
            acknowledgment.acknowledge()
        }
    }
}

const val TESTDATA_TOPIC = "flex.spinnsyn-testdata"
