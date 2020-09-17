package no.nav.syfo.varsling.kafka

import no.nav.syfo.kafka.toProducerConfig
import no.nav.syfo.log
import no.nav.syfo.util.JacksonKafkaSerializer
import no.nav.syfo.varsling.domene.EnkeltVarsel
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import java.util.* // ktlint-disable no-wildcard-imports

class EnkeltvarselKafkaProducer(
    private val kafkaproducer: KafkaProducer<String, EnkeltVarsel>
) {
    fun opprettEnkeltVarsel(enkeltVarsel: EnkeltVarsel) {
        try {
            kafkaproducer.send(
                ProducerRecord(
                    "aapen-syfo-enkeltsvarsel-v1",
                    enkeltVarsel.varselBestillingId,
                    enkeltVarsel
                )
            ).get()
        } catch (e: Exception) {
            log.error(
                "Noe gikk galt ved sending av enkeltvarsel med id {}: ${e.message}",
                enkeltVarsel.varselBestillingId
            )
            throw e
        }
    }
}

fun skapEnkeltvarselKafkaProducer(kafkaBaseConfig: Properties): EnkeltvarselKafkaProducer {
    val kafkaBrukernotifikasjonProducerConfig = kafkaBaseConfig.toProducerConfig(
        "spinnsyn", valueSerializer = JacksonKafkaSerializer::class
    )

    val kafkaproducer = KafkaProducer<String, EnkeltVarsel>(kafkaBrukernotifikasjonProducerConfig)
    return EnkeltvarselKafkaProducer(kafkaproducer = kafkaproducer)
}
