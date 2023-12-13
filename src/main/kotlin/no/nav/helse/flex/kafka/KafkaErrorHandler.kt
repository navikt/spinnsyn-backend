package no.nav.helse.flex.kafka

import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.kafka.listener.MessageListenerContainer
import org.springframework.stereotype.Component
import org.springframework.util.backoff.ExponentialBackOff
import no.nav.helse.flex.logger as slf4jLogger

@Component
class KafkaErrorHandler : DefaultErrorHandler(
    ExponentialBackOff(1000L, 1.5).apply {
        // 8 minutter, som er mindre enn max.poll.interval.ms på 10 minutter.
        maxInterval = 60_000L * 8
    },
) {
    // Bruker aliased logger for unngå kollisjon med CommonErrorHandler.logger(): LogAccessor.
    val log = slf4jLogger()

    override fun handleRemaining(
        thrownException: java.lang.Exception,
        records: MutableList<ConsumerRecord<*, *>>,
        consumer: Consumer<*, *>,
        container: MessageListenerContainer,
    ) {
        records.forEach { record ->
            log.error(
                "Feil i prossessering av record med offset: ${record.offset()}, key: ${record.key()} på topic ${record.topic()}",
                thrownException,
            )
        }
        if (records.isEmpty()) {
            log.error("Feil i listener uten noen records", thrownException)
        }
        super.handleRemaining(thrownException, records, consumer, container)
    }
}
