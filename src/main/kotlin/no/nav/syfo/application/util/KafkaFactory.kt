package no.nav.syfo.application.util

import io.confluent.kafka.serializers.KafkaAvroSerializer
import no.nav.brukernotifikasjon.schemas.Done
import no.nav.brukernotifikasjon.schemas.Nokkel
import no.nav.brukernotifikasjon.schemas.Oppgave
import no.nav.syfo.brukernotifkasjon.BrukernotifikasjonKafkaProducer
import no.nav.syfo.kafka.toProducerConfig
import org.apache.kafka.clients.producer.KafkaProducer
import java.util.* // ktlint-disable no-wildcard-imports

class KafkaFactory private constructor() {
    companion object {

        fun getBrukernotifikasjonKafkaProducer(kafkaBaseConfig: Properties): BrukernotifikasjonKafkaProducer {
            val kafkaBrukernotifikasjonProducerConfig = kafkaBaseConfig.toProducerConfig(
                "spinnsyn", valueSerializer = KafkaAvroSerializer::class, keySerializer = KafkaAvroSerializer::class
            )

            val kafkaproducerOppgave = KafkaProducer<Nokkel, Oppgave>(kafkaBrukernotifikasjonProducerConfig)
            val kafkaproducerDone = KafkaProducer<Nokkel, Done>(kafkaBrukernotifikasjonProducerConfig)
            return BrukernotifikasjonKafkaProducer(
                kafkaproducerOppgave = kafkaproducerOppgave,
                kafkaproducerDone = kafkaproducerDone
            )
        }
    }
}
