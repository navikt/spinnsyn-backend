package no.nav.helse.flex.kafka

import no.nav.helse.flex.vedtak.service.RetroVedtakService
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

const val SPORBAR_TOPIC = "aapen-helse-sporbar"

@Component
class HelseSporbarKafkaListener(
    private val retroVedtakService: RetroVedtakService
) {

    @KafkaListener(
        topics = [SPORBAR_TOPIC],
        containerFactory = "onPremKafkaListenerContainerFactory"
    )
    fun listen(cr: ConsumerRecord<String, String>, acknowledgment: Acknowledgment) {
        retroVedtakService.handterMelding(cr)

        acknowledgment.acknowledge()
    }
}
