package no.nav.helse.flex.brukernotifkasjon

import io.confluent.kafka.serializers.KafkaAvroSerializer
import no.nav.brukernotifikasjon.schemas.Done
import no.nav.brukernotifikasjon.schemas.Nokkel
import no.nav.brukernotifikasjon.schemas.Oppgave
import no.nav.helse.flex.log
import no.nav.syfo.kafka.toProducerConfig
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import java.util.Properties

class BrukernotifikasjonKafkaProducer(
    private val kafkaproducerOppgave: KafkaProducer<Nokkel, Oppgave>,
    private val kafkaproducerDone: KafkaProducer<Nokkel, Done>
) {
    fun opprettBrukernotifikasjonOppgave(nokkel: Nokkel, oppgave: Oppgave) {
        try {
            kafkaproducerOppgave.send(ProducerRecord("aapen-brukernotifikasjon-nyOppgave-v1", nokkel, oppgave)).get()
        } catch (e: Exception) {
            log.error("Noe gikk galt ved sending av oppgave med id {}: ${e.message}", nokkel.getEventId())
            throw e
        }
    }

    fun sendDonemelding(nokkel: Nokkel, done: Done) {
        try {
            kafkaproducerDone.send(ProducerRecord("aapen-brukernotifikasjon-done-v1", nokkel, done)).get()
        } catch (e: Exception) {
            log.error("Noe gikk galt ved ferdigstilling av oppgave med id {}: ${e.message}", nokkel.getEventId())
            throw e
        }
    }
}

fun skapBrukernotifikasjonKafkaProducer(kafkaBaseConfig: Properties): BrukernotifikasjonKafkaProducer {
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
