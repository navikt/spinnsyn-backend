package no.nav.helse.flex.kafka

import no.nav.helse.flex.vedtak.service.VedtakServiceV2
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

const val VEDTAK_TOPIC = "tbd.vedtak"

@Component
class VedtakKafkaListener(
    private val vedtakServiceV2: VedtakServiceV2
) {

    @KafkaListener(
        topics = [VEDTAK_TOPIC],
        containerFactory = "aivenKafkaListenerContainerFactory"
    )
    fun listen(cr: ConsumerRecord<String, String>, acknowledgment: Acknowledgment) {
        vedtakServiceV2.handterMelding(cr)

        acknowledgment.acknowledge()
    }
}
