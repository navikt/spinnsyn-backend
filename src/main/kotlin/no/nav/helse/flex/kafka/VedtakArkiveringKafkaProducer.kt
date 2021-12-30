package no.nav.helse.flex.kafka

import no.nav.helse.flex.logger
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.springframework.stereotype.Component

const val VEDTAK_ARKIVERING_TOPIC = "flex.vedtak-arkivering"

@Component
class VedtakArkiveringKafkaProducer(
    private val producer: KafkaProducer<String, VedtakArkiveringDTO>
) {

    private val log = logger()

    fun produserMelding(vedtakArkiveringDTO: VedtakArkiveringDTO): RecordMetadata {
        try {
            return producer.send(
                ProducerRecord(VEDTAK_ARKIVERING_TOPIC, vedtakArkiveringDTO.id, vedtakArkiveringDTO)
            ).get()
        } catch (e: Throwable) {
            log.error(
                "Feil ved sending av vedtak for arkivering med id: ${vedtakArkiveringDTO.id} til " +
                    "topic: $VEDTAK_ARKIVERING_TOPIC.",
                e
            )
            throw e
        }
    }
}

data class VedtakArkiveringDTO(
    val id: String,
    val fnr: String,
)
