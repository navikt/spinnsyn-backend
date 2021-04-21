package no.nav.helse.flex.testutil
/*

import io.mockk.every
import no.nav.helse.flex.application.ApplicationState
import org.apache.kafka.clients.consumer.KafkaConsumer
import java.time.Duration

fun stopApplicationNårAntallKafkaMeldingerErLest(
    kafkaConsumer: KafkaConsumer<String, String>,
    applicationState: ApplicationState,
    antallKafkaMeldinger: Int
) {
    var i = antallKafkaMeldinger
    every { kafkaConsumer.poll(any<Duration>()) } answers {
        val cr = callOriginal()
        i -= cr.count()
        if (i <= 0) {
            applicationState.ready = false
        }
        cr
    }
}

fun stopApplicationNårAntallKafkaPollErGjort(
    kafkaConsumer: KafkaConsumer<String, String>,
    applicationState: ApplicationState,
    antallKafkaPoll: Int
) {
    var i = antallKafkaPoll
    every { kafkaConsumer.poll(any<Duration>()) } answers {
        val cr = callOriginal()
        i -= 1
        if (i <= 0) {
            applicationState.ready = false
        }
        cr
    }
}
*/
