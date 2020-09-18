package no.nav.helse.flex.vedtak.kafka

import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import java.time.Duration

class VedtakConsumer(
    private val kafkaVedtakConsumer: KafkaConsumer<String, String>
) {
    fun poll(): ConsumerRecords<String, String> {
        return kafkaVedtakConsumer.poll(Duration.ofMillis(1000))
    }
}
