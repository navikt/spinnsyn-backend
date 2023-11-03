package no.nav.helse.flex.kafka

import no.nav.helse.flex.service.MottaVedtak
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.TopicPartition
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.listener.ConsumerSeekAware
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component
import java.time.OffsetDateTime

const val VEDTAK_TOPIC = "tbd.vedtak"

@Component
class VedtakKafkaListener(
    private val mottaVedtak: MottaVedtak
) : ConsumerSeekAware {

    @KafkaListener(
        topics = [VEDTAK_TOPIC],
        containerFactory = "aivenKafkaListenerContainerFactory"
    )
    fun listen(cr: ConsumerRecord<String, String>, acknowledgment: Acknowledgment) {
        mottaVedtak.handterMelding(cr)
        acknowledgment.acknowledge()
    }

    override fun onPartitionsAssigned(
        assignments: MutableMap<TopicPartition, Long>,
        callback: ConsumerSeekAware.ConsumerSeekCallback
    ) {
        super.onPartitionsAssigned(assignments, callback)
        assignments.forEach { (topicPartition, _) ->

            // seek to timestamp nov 1 2023 start of day
            val timestamp = OffsetDateTime.of(2023, 11, 1, 0, 0, 0, 0, OffsetDateTime.now().offset)
            callback.seekToTimestamp(
                topicPartition.topic(),
                topicPartition.partition(),
                timestamp.toInstant().toEpochMilli()
            )
        }
    }
}
