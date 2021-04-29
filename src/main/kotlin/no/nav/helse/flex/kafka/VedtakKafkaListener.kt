package no.nav.helse.flex.kafka

import no.nav.helse.flex.vedtak.service.MottaVedtakService
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

const val VEDTAK_TOPIC = "tbd.vedtak"

@Component
class VedtakKafkaListener(
    private val mottaVedtakService: MottaVedtakService
) {

    @KafkaListener(
        topics = [VEDTAK_TOPIC],
        containerFactory = "aivenKafkaListenerContainerFactory"
    )
    fun listen(cr: ConsumerRecord<String, String>, acknowledgment: Acknowledgment) {
        mottaVedtakService.handterMelding(cr)

        acknowledgment.acknowledge()
    }
}
