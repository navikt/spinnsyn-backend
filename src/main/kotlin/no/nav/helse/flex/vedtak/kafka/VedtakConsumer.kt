package no.nav.helse.flex.vedtak.kafka

import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import java.time.Duration

class VedtakConsumer(
    private val kafkaVedtakConsumer: KafkaConsumer<String, String>,
    private val topics: List<String>
) {
    fun subscribe() {
        kafkaVedtakConsumer.subscribe(topics)
    }

    fun unsubscribe() {
        kafkaVedtakConsumer.unsubscribe()
    }

    fun poll(): ConsumerRecords<String, String> {
        return kafkaVedtakConsumer.poll(Duration.ofMillis(1000))
    }

    fun commitSync() {
        kafkaVedtakConsumer.commitSync()
    }
}
