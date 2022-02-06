package no.nav.helse.flex.kafka

import org.apache.kafka.clients.CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG
import org.apache.kafka.clients.CommonClientConfigs.SECURITY_PROTOCOL_CONFIG
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.serialization.StringDeserializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
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
}
