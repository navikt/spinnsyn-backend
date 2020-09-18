package no.nav.helse.flex.varsling.kafka

import no.nav.helse.flex.log
import no.nav.helse.flex.util.JacksonKafkaSerializer
import no.nav.helse.flex.varsling.domene.EnkeltVarsel
import no.nav.syfo.kafka.toProducerConfig
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import java.util.Properties

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
