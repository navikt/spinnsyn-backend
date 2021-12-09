package no.nav.helse.flex.kafka

import no.nav.helse.flex.kafka.ConsumerStoppedEventExt.restart
import no.nav.helse.flex.service.RetroMottaVedtakService
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.context.event.EventListener
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.event.ConsumerStoppedEvent
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

const val SPORBAR_TOPIC = "aapen-helse-sporbar"

@Component
class HelseSporbarKafkaListener(
    private val retroMottaVedtakService: RetroMottaVedtakService
) {

    @KafkaListener(
        topics = [SPORBAR_TOPIC],
        containerFactory = "onPremKafkaListenerContainerFactory"
    )
    fun listen(cr: ConsumerRecord<String, String>, acknowledgment: Acknowledgment) {
        retroMottaVedtakService.handterMelding(cr)
        acknowledgment.acknowledge()
    }

    @EventListener
    fun eventHandler(event: ConsumerStoppedEvent) {
        event.restart()
    }
}
