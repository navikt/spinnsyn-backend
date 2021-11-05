package no.nav.helse.flex.kafka

import no.nav.helse.flex.kafka.ConsumerStoppedEventExt.restart
import no.nav.helse.flex.logger
import no.nav.helse.flex.service.MottaUtbetalingService
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.TopicPartition
import org.springframework.context.event.EventListener
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.event.ConsumerStoppedEvent
import org.springframework.kafka.listener.ConsumerSeekAware
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.ZoneOffset

const val UTBETALING_TOPIC = "tbd.utbetaling"

@Component
class UtbetalingKafkaListener(
    private val mottaUtbetalingService: MottaUtbetalingService
) : ConsumerSeekAware {
    @KafkaListener(
        topics = [UTBETALING_TOPIC],
        containerFactory = "aivenKafkaListenerContainerFactory",
        properties = ["auto.offset.reset = latest"],
    )
    fun listen(cr: ConsumerRecord<String, String>, acknowledgment: Acknowledgment) {
        mottaUtbetalingService.handterMelding(cr)

        acknowledgment.acknowledge()
    }

    val log = logger()

    @EventListener
    fun eventHandler(event: ConsumerStoppedEvent) {
        event.restart()
    }

    override fun registerSeekCallback(callback: ConsumerSeekAware.ConsumerSeekCallback) {
        // register custom callback
        log.info("registerSeekCallback ${this.javaClass.simpleName}")
    }

    override fun onPartitionsAssigned(assignments: Map<TopicPartition, Long>, callback: ConsumerSeekAware.ConsumerSeekCallback) {
        // Seek all the assigned partition to a certain offset
        log.info("onPartitionsAssigned ${this.javaClass.simpleName}")

        val torsdag4nov = LocalDate.of(2021, 11, 4).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        callback.seekToTimestamp(assignments.keys, torsdag4nov)
        log.info("ferdig med seek seekToTimestamp ${this.javaClass.simpleName}")
    }
}
