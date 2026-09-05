package no.nav.helse.flex.kafka

import no.nav.helse.flex.testdata.TESTDATA_RESET_TOPIC
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.TopicBuilder
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.KafkaAdmin

@Configuration
class TestKafkaConfig(
    private val aivenKafkaConfig: AivenKafkaConfig,
) {
    // Oppretter topics før tester listenere subscribes og repartisjonering gjøres for å sikret at tester ikke
    // publiserer meldinger før de har en partisjon.
    @Bean
    fun kafkaAdmin(): KafkaAdmin = KafkaAdmin(aivenKafkaConfig.commonConfig())

    @Bean
    fun testTopics(): KafkaAdmin.NewTopics =
        KafkaAdmin.NewTopics(
            *listOf(
                VEDTAK_TOPIC,
                UTBETALING_TOPIC,
                VEDTAK_STATUS_TOPIC,
                FLEX_SYKEPENGESOKNAD_TOPIC,
                TESTDATA_RESET_TOPIC,
            ).map {
                TopicBuilder
                    .name(it)
                    .partitions(1)
                    .replicas(1)
                    .build()
            }.toTypedArray(),
        )

    @Bean
    fun producer(): KafkaProducer<String, String> {
        val config =
            mapOf(
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
                ProducerConfig.ACKS_CONFIG to "all",
                ProducerConfig.RETRIES_CONFIG to 10,
                ProducerConfig.RETRY_BACKOFF_MS_CONFIG to 100,
            ) + aivenKafkaConfig.commonConfig()
        return KafkaProducer(config)
    }

    fun testConsumerProps(groupId: String) =
        mapOf(
            ConsumerConfig.GROUP_ID_CONFIG to groupId,
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
            ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false,
            ConsumerConfig.MAX_POLL_RECORDS_CONFIG to "1",
        ) + aivenKafkaConfig.commonConfig()

    @Bean
    fun statusKafkaConsumer(): Consumer<String, String> =
        DefaultKafkaConsumerFactory(
            testConsumerProps("varsling-consumer"),
            StringDeserializer(),
            StringDeserializer(),
        ).createConsumer()

    @Bean
    fun vedtakKafkaConsumer(): Consumer<String, String> =
        DefaultKafkaConsumerFactory(
            testConsumerProps("spinnsyn-consumer"),
            StringDeserializer(),
            StringDeserializer(),
        ).createConsumer()
}
