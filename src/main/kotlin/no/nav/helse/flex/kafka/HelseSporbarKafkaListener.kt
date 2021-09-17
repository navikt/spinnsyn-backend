package no.nav.helse.flex.kafka

import no.nav.helse.flex.logger
import no.nav.helse.flex.service.RetroMottaVedtakService
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.TopicPartition
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.listener.ConsumerSeekAware
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.ZoneOffset

const val SPORBAR_TOPIC = "aapen-helse-sporbar"

@Component
class HelseSporbarKafkaListener(
    private val retroMottaVedtakService: RetroMottaVedtakService
) : ConsumerSeekAware {
    private val log = logger()

    @KafkaListener(
        topics = [SPORBAR_TOPIC],
        properties = ["auto.offset.reset = latest"],
        containerFactory = "onPremKafkaListenerContainerFactory",
    )
    fun listen(cr: ConsumerRecord<String, String>, acknowledgment: Acknowledgment) {
        retroMottaVedtakService.handterMelding(cr)

        acknowledgment.acknowledge()
    }

    override fun registerSeekCallback(callback: ConsumerSeekAware.ConsumerSeekCallback) {
        // register custom callback
        log.info("registerSeekCallback ${this.javaClass.simpleName}")
    }

    override fun onPartitionsAssigned(assignments: Map<TopicPartition, Long>, callback: ConsumerSeekAware.ConsumerSeekCallback) {
        // Seek all the assigned partition to a certain offset
        log.info("onPartitionsAssigned ${this.javaClass.simpleName}")

        val fredag17sept = LocalDate.of(2021, 9, 17).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        callback.seekToTimestamp(assignments.keys, fredag17sept)
        log.info("ferdig med seek seekToTimestamp ${this.javaClass.simpleName}")
    }
}
