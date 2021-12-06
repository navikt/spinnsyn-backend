package no.nav.helse.flex.kafka

import io.confluent.kafka.serializers.KafkaAvroSerializer
import io.confluent.kafka.serializers.KafkaAvroSerializerConfig
import no.nav.brukernotifikasjon.schemas.Done
import no.nav.brukernotifikasjon.schemas.Nokkel
import no.nav.brukernotifikasjon.schemas.Oppgave
import org.apache.kafka.clients.CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG
import org.apache.kafka.clients.CommonClientConfigs.SECURITY_PROTOCOL_CONFIG
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.serialization.StringDeserializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.listener.ContainerProperties
import java.io.Serializable
import java.time.Duration

@Configuration
class OnPremKafkaConfig(
    @Value("\${on-prem-kafka.bootstrap-servers}") private val kafkaBootstrapServers: String,
    @Value("\${on-prem-kafka.security-protocol}") private val kafkaSecurityProtocol: String,
    @Value("\${on-prem-kafka.username}") private val serviceuserUsername: String,
    @Value("\${on-prem-kafka.password}") private val serviceuserPassword: String,
    @Value("\${on-prem-kafka.auto-offset-reset:none}") private val kafkaAutoOffsetReset: String,
    @Value("\${on-prem-kafka.schema-registry-url}") private val kafkaSchemaRegistryUrl: String
) {

    fun commonConfig(): Map<String, String> {
        return mapOf(
            BOOTSTRAP_SERVERS_CONFIG to kafkaBootstrapServers,
            SECURITY_PROTOCOL_CONFIG to kafkaSecurityProtocol,
            SaslConfigs.SASL_JAAS_CONFIG to "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"${serviceuserUsername}\" password=\"${serviceuserPassword}\";",
            SaslConfigs.SASL_MECHANISM to "PLAIN"
        )
    }

    fun skapKafkaConsumerConfig(): Map<String, Serializable> {

        return mapOf(
            ConsumerConfig.GROUP_ID_CONFIG to "spinnsyn-backend-consumer-v3",
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to kafkaAutoOffsetReset,
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            ConsumerConfig.MAX_POLL_RECORDS_CONFIG to "1"
        ) + commonConfig()
    }

    @Bean
    fun onPremKafkaListenerContainerFactory(
        kafkaErrorHandler: KafkaErrorHandler
    ): ConcurrentKafkaListenerContainerFactory<String, String> {
        val factory = ConcurrentKafkaListenerContainerFactory<String, String>()
        factory.consumerFactory = DefaultKafkaConsumerFactory(skapKafkaConsumerConfig())
        factory.containerProperties.ackMode = ContainerProperties.AckMode.MANUAL_IMMEDIATE
        factory.containerProperties.setAuthExceptionRetryInterval(Duration.ofSeconds(2))
        factory.setCommonErrorHandler(kafkaErrorHandler)
        return factory
    }

    fun producerConfig(
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
        ) + commonConfig()
    }

    private fun <K, V> skapBrukernotifikasjonKafkaProducer(): KafkaProducer<K, V> =
        KafkaProducer(
            producerConfig(
                keySerializer = KafkaAvroSerializer::class.java,
                valueSerializer = KafkaAvroSerializer::class.java
            ) + mapOf(
                KafkaAvroSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG to kafkaSchemaRegistryUrl
            )
        )

    @Bean
    @Profile("default")
    fun oppgaveKafkaProducer(): KafkaProducer<Nokkel, Oppgave> {
        return skapBrukernotifikasjonKafkaProducer()
    }

    @Bean
    @Profile("default")
    fun doneKafkaProducer(): KafkaProducer<Nokkel, Done> {
        return skapBrukernotifikasjonKafkaProducer()
    }
}
