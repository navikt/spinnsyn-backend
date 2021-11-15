package no.nav.helse.flex.kafka

import no.nav.helse.flex.domene.VedtakStatusDTO
import no.nav.helse.flex.logger
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.springframework.stereotype.Component

const val VEDTAK_STATUS_TOPIC = "flex.vedtak-status"

@Component
class VedtakStatusKafkaProducer(
    private val producer: KafkaProducer<String, VedtakStatusDTO>
) {

    private val log = logger()

    fun produserMelding(vedtakStatusDTO: VedtakStatusDTO): RecordMetadata {
        try {
            return producer.send(
                ProducerRecord(VEDTAK_STATUS_TOPIC, vedtakStatusDTO.id, vedtakStatusDTO)
            ).get()
        } catch (e: Throwable) {
            log.error(
                "Feil ved sending av vedtak status[id=${vedtakStatusDTO.id}] til [topic=$VEDTAK_STATUS_TOPIC].",
                e
            )
            throw e
        }
    }
}
