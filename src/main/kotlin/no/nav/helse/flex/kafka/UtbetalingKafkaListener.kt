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
import java.time.OffsetDateTime
import java.time.ZoneOffset

const val UTBETALING_TOPIC = "tbd.utbetaling"

@Component
class UtbetalingKafkaListener(
    private val mottaUtbetalingService: MottaUtbetalingService
) : ConsumerSeekAware {
    @KafkaListener(
        topics = [UTBETALING_TOPIC],
        containerFactory = "aivenKafkaListenerContainerFactory"
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

    override fun onPartitionsAssigned(
        assignments: Map<TopicPartition, Long>,
        callback: ConsumerSeekAware.ConsumerSeekCallback
    ) {
        assignments.forEach { partition ->
            val timestamp = OffsetDateTime.of(2021, 6, 3, 5, 35, 0, 0, ZoneOffset.UTC)
            log.info("Søker til $timestamp for topic ${partition.key.topic()} partition: ${partition.key.partition()}")
            callback.seekToTimestamp(partition.key.topic(), partition.key.partition(), timestamp.toInstant().toEpochMilli())
        }
    }
}
