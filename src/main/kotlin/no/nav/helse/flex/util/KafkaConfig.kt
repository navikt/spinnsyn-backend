package no.nav.helse.flex.util

import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig
import io.confluent.kafka.serializers.KafkaAvroSerializer
import no.nav.helse.flex.Environment
import no.nav.helse.flex.varsling.domene.EnkeltVarsel
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer

private fun commonConfig(env: Environment): Map<String, String> {
    return mapOf(
        CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG to env.kafkaBootstrapServers,
        CommonClientConfigs.SECURITY_PROTOCOL_CONFIG to env.kafkaSecurityProtocol,
        SaslConfigs.SASL_JAAS_CONFIG to "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"${env.serviceuserUsername}\" password=\"${env.serviceuserPassword}\";",
        SaslConfigs.SASL_MECHANISM to "PLAIN"
    )
}

fun skapVedtakKafkaConsumer(env: Environment): KafkaConsumer<String, String> {

    val config = mapOf(
        ConsumerConfig.GROUP_ID_CONFIG to "spinnsyn-backend-consumer-v3",
        ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to env.kafkaAutoOffsetReset,
        ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
        ConsumerConfig.MAX_POLL_RECORDS_CONFIG to "1"
    ) + commonConfig(env)

    return KafkaConsumer(config)
}

private fun commonProducerConfig(
    env: Environment,
    keySerializer: Class<*>,
    valueSerializer: Class<*>
): Map<String, Any> {
    return mapOf(
        ProducerConfig.ACKS_CONFIG to "all",
        ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG to "true",
        ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION to "1",
        ProducerConfig.MAX_BLOCK_MS_CONFIG to "15000",
        ProducerConfig.RETRIES_CONFIG to "100000",
        ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to valueSerializer,
        ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to keySerializer
    ) + commonConfig(env)
}

fun skapEnkeltvarselKafkaProducer(env: Environment): KafkaProducer<String, EnkeltVarsel> =
    KafkaProducer(
        commonProducerConfig(
            env = env,
            keySerializer = StringSerializer::class.java,
            valueSerializer = JacksonKafkaSerializer::class.java
        )
    )

fun <K, V> skapBrukernotifikasjonKafkaProducer(env: Environment): KafkaProducer<K, V> =
    KafkaProducer(
        commonProducerConfig(
            env = env,
            keySerializer = KafkaAvroSerializer::class.java,
            valueSerializer = KafkaAvroSerializer::class.java
        ) + mapOf(
            AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG to env.kafkaSchemaRegistryUrl
        )
    )
