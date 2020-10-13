package no.nav.helse.flex.util

import no.nav.helse.flex.Environment
import no.nav.syfo.kafka.envOverrides
import no.nav.syfo.kafka.loadBaseConfig
import no.nav.syfo.kafka.toConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer

class KafkaClients(env: Environment) {

    val kafkaVedtakConsumer = getVedtakKafkaConsumer(env)

    private fun getVedtakKafkaConsumer(env: Environment): KafkaConsumer<String, String> {

        val config = loadBaseConfig(env, env.hentKafkaCredentials()).envOverrides()
        config["auto.offset.reset"] = "earliest"

        val properties = config.toConsumerConfig("spinnsyn-backend-consumer-v3", StringDeserializer::class)
        properties.let { it[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = "1" }

        val consumer = KafkaConsumer<String, String>(properties)
        consumer.subscribe(listOf("aapen-helse-sporbar"))

        return consumer
    }
}
