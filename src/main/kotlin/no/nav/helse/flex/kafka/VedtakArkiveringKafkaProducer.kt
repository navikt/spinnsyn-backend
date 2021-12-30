package no.nav.helse.flex.kafka

import no.nav.helse.flex.logger
import org.apache.kafka.clients.producer.Callback
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.springframework.stereotype.Component

const val VEDTAK_ARKIVERING_TOPIC = "flex.vedtak-arkivering"

@Component
class VedtakArkiveringKafkaProducer(
    private val producer: KafkaProducer<String, ArkiveringDTO>
) {

    private val log = logger()

    fun produserMelding(arkiveringDTO: ArkiveringDTO) {
        val callback = Callback { _, exception ->
            if (exception != null) {
                log.error("Feil ved produksjon av vedtak for arkivering med id: ${arkiveringDTO.id}", exception)
            }
        }
        try {
            producer.send(
                ProducerRecord(VEDTAK_ARKIVERING_TOPIC, arkiveringDTO.id, arkiveringDTO),
                callback
            )
        } catch (e: Throwable) {
            log.error(
                "Feil ved sending av vedtak for arkivering med id: ${arkiveringDTO.id} til " +
                    "topic: $VEDTAK_ARKIVERING_TOPIC.",
                e
            )
            throw e
        }
    }
}

data class ArkiveringDTO(
    val id: String,
    val fnr: String,
)
