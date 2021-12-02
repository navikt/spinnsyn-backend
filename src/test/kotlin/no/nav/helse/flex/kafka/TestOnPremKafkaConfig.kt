package no.nav.helse.flex.kafka

import io.confluent.kafka.schemaregistry.avro.AvroSchema
import io.confluent.kafka.schemaregistry.client.MockSchemaRegistryClient
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig
import io.confluent.kafka.serializers.KafkaAvroDeserializer
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig
import io.confluent.kafka.serializers.KafkaAvroSerializer
import io.confluent.kafka.serializers.KafkaAvroSerializerConfig
import no.nav.brukernotifikasjon.schemas.Done
import no.nav.brukernotifikasjon.schemas.Nokkel
import no.nav.helse.flex.brukernotifkasjon.DONE_TOPIC
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.serialization.Deserializer
import org.apache.kafka.common.serialization.Serializer
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.DefaultKafkaProducerFactory

@Configuration
class TestOnPremKafkaConfig(
    private val onPremKafkaConfig: OnPremKafkaConfig,
) {

    @Bean
    fun onpremSoknadKafkaProducer(): KafkaProducer<String, String> {
        val config = mapOf(
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
            ProducerConfig.ACKS_CONFIG to "all",
            ProducerConfig.RETRIES_CONFIG to 10,
            ProducerConfig.RETRY_BACKOFF_MS_CONFIG to 100
        ) + onPremKafkaConfig.commonConfig()
        return KafkaProducer(config)
    }

    private fun avroProducerConfig(): Map<String, Any> {
        return mapOf(
            ProducerConfig.ACKS_CONFIG to "all",
            ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG to "true",
            ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION to "1",
            ProducerConfig.MAX_BLOCK_MS_CONFIG to "15000",
            ProducerConfig.RETRIES_CONFIG to "100000",
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to KafkaAvroSerializer::class.java,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to KafkaAvroSerializer::class.java,
            KafkaAvroSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG to "http://ikke.i.bruk.nav",
            SaslConfigs.SASL_MECHANISM to "PLAIN"
        ) + onPremKafkaConfig.commonConfig()
    }

    @Bean
    fun mockSchemaRegistryClient(): MockSchemaRegistryClient {
        val mockSchemaRegistryClient = MockSchemaRegistryClient()

        mockSchemaRegistryClient.register("$DONE_TOPIC-value", AvroSchema(Done.`SCHEMA$`))
        mockSchemaRegistryClient.register("$DONE_TOPIC-value", AvroSchema(Nokkel.`SCHEMA$`))
        return mockSchemaRegistryClient
    }

    fun kafkaAvroDeserializer(): KafkaAvroDeserializer {
        val config = HashMap<String, Any>()
        config[AbstractKafkaSchemaSerDeConfig.AUTO_REGISTER_SCHEMAS] = false
        config[KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG] = true
        config[KafkaAvroDeserializerConfig.SCHEMA_REGISTRY_URL_CONFIG] = "http://ikke.i.bruk.nav"
        return KafkaAvroDeserializer(mockSchemaRegistryClient(), config)
    }

    fun testConsumerProps(groupId: String) = mapOf(
        ConsumerConfig.GROUP_ID_CONFIG to groupId,
        ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
        ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false,
        ConsumerConfig.MAX_POLL_RECORDS_CONFIG to "1",
        KafkaAvroDeserializerConfig.SCHEMA_REGISTRY_URL_CONFIG to "http://ikke.i.bruk.nav",
        KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG to true,
    ) + onPremKafkaConfig.commonConfig()

    @Bean
    fun doneOppgaveKafkaConsumer(): Consumer<Nokkel, Done> {
        @Suppress("UNCHECKED_CAST")
        return DefaultKafkaConsumerFactory(
            testConsumerProps("done-konsumer"),
            kafkaAvroDeserializer() as Deserializer<Nokkel>,
            kafkaAvroDeserializer() as Deserializer<Done>
        ).createConsumer()
    }

    @Bean
    fun doneKafkaProducer(mockSchemaRegistryClient: MockSchemaRegistryClient): Producer<Nokkel, Done> {
        val kafkaAvroSerializer = KafkaAvroSerializer(mockSchemaRegistryClient)
        @Suppress("UNCHECKED_CAST")
        return DefaultKafkaProducerFactory(
            avroProducerConfig(),
            kafkaAvroSerializer as Serializer<Nokkel>,
            kafkaAvroSerializer as Serializer<Done>
        ).createProducer()
    }

    @Bean
    fun statusKafkaConsumer(): Consumer<String, String> {
        return DefaultKafkaConsumerFactory(
            testConsumerProps("varsling-consumer"),
            StringDeserializer(),
            StringDeserializer(),
        ).createConsumer()
    }
}
