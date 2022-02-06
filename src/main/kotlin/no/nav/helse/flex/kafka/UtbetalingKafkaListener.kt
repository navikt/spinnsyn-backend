package no.nav.helse.flex.kafka

import no.nav.helse.flex.kafka.ConsumerStoppedEventExt.restart
import no.nav.helse.flex.service.MottaUtbetaling
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.context.event.EventListener
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.event.ConsumerStoppedEvent
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

const val UTBETALING_TOPIC = "tbd.utbetaling"

@Component
class UtbetalingKafkaListener(
    private val mottaUtbetaling: MottaUtbetaling
) {

    @KafkaListener(
        topics = [UTBETALING_TOPIC],
        containerFactory = "aivenKafkaListenerContainerFactory"
    )
    fun listen(cr: ConsumerRecord<String, String>, acknowledgment: Acknowledgment) {
        mottaUtbetaling.handterMelding(cr)
        acknowledgment.acknowledge()
    }

    @EventListener
    fun eventHandler(event: ConsumerStoppedEvent) {
        event.restart()
    }
}
