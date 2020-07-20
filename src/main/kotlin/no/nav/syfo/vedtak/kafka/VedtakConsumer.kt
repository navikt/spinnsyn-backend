package no.nav.syfo.vedtak.kafka

import java.time.Duration
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer

class VedtakConsumer(
    private val kafkaVedtakConsumer: KafkaConsumer<String, String>
) {
    fun poll(): ConsumerRecords<String, String> {
        return kafkaVedtakConsumer.poll(Duration.ofMillis(0))
    }
}
