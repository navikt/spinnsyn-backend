package no.nav.syfo.application.util

import no.nav.syfo.Environment
import no.nav.syfo.VaultSecrets
import no.nav.syfo.application.vedtak.model.Utbetaling
import no.nav.syfo.kafka.envOverrides
import no.nav.syfo.kafka.loadBaseConfig
import no.nav.syfo.kafka.toConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer

class KafkaClients(env: Environment, vaultSecrets: VaultSecrets) {

    val kafkaUtbetalingConsumer = getKafkaUtbetalingConsumer(vaultSecrets, env)

    private fun getKafkaUtbetalingConsumer(vaultSecrets: VaultSecrets, env: Environment): KafkaConsumer<String, Utbetaling> {
        val config = loadBaseConfig(env, vaultSecrets).envOverrides()
        config["auto.offset.reset"] = "latest"

        val properties = config.toConsumerConfig("${env.applicationName}-consumer", JacksonKafkaDeserializer::class)
        properties.let { it[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = "1" }

        val consumer = KafkaConsumer<String, Utbetaling>(properties, StringDeserializer(), JacksonKafkaDeserializer(Utbetaling::class))
        consumer.subscribe(listOf(env.utbetalingTopic))

        return consumer
    }
}
