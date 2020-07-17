package no.nav.syfo.application.util

import no.nav.syfo.Environment
import no.nav.syfo.VaultSecrets
import no.nav.syfo.kafka.envOverrides
import no.nav.syfo.kafka.loadBaseConfig
import no.nav.syfo.kafka.toConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer

class KafkaClients(env: Environment, vaultSecrets: VaultSecrets) {

    val kafkaVedtakConsumer = getVedtakKafkaConsumer(vaultSecrets, env)

    private fun getVedtakKafkaConsumer(vaultSecrets: VaultSecrets, env: Environment): KafkaConsumer<String, String> {
        val config = loadBaseConfig(env, vaultSecrets).envOverrides()
        config["auto.offset.reset"] = "latest"

        val properties = config.toConsumerConfig("${env.applicationName}-consumer", StringDeserializer::class)
        properties.let { it[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = "1" }

        val consumer = KafkaConsumer<String, String>(properties)
        consumer.subscribe(listOf("aapen-helse-sporbar"))

        return consumer
    }
}
