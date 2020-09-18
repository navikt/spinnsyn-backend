package no.nav.helse.flex.testutil

import io.mockk.every
import no.nav.helse.flex.application.ApplicationState
import org.apache.kafka.clients.consumer.KafkaConsumer
import java.time.Duration

fun stopApplicationNårKafkaTopicErLest(
    kafkaConsumer: KafkaConsumer<String, String>,
    applicationState: ApplicationState
) {
    every { kafkaConsumer.poll(any<Duration>()) } answers {
        val cr = callOriginal()
        if (!cr.isEmpty) {
            applicationState.ready = false
        }
        cr
    }
}
