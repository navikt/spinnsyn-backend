package no.nav.helse.flex.varsling.kafka
/*

import no.nav.helse.flex.Environment
import no.nav.helse.flex.log
import no.nav.helse.flex.util.skapEnkeltvarselKafkaProducer
import no.nav.helse.flex.varsling.domene.EnkeltVarsel
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

class EnkeltvarselKafkaProdusent(
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

fun skapEnkeltvarselKafkaProdusent(env: Environment): EnkeltvarselKafkaProdusent {
    val kafkaproducer = skapEnkeltvarselKafkaProducer(env)
    return EnkeltvarselKafkaProdusent(kafkaproducer = kafkaproducer)
}
*/
