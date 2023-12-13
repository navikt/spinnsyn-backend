package no.nav.helse.flex.kafka

import no.nav.helse.flex.service.MottaUtbetaling
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

const val UTBETALING_TOPIC = "tbd.utbetaling"

@Component
class UtbetalingKafkaListener(
    private val mottaUtbetaling: MottaUtbetaling,
) {
    @KafkaListener(
        topics = [UTBETALING_TOPIC],
        containerFactory = "aivenKafkaListenerContainerFactory",
    )
    fun listen(
        cr: ConsumerRecord<String, String>,
        acknowledgment: Acknowledgment,
    ) {
        mottaUtbetaling.handterMelding(cr)
        acknowledgment.acknowledge()
    }
}
