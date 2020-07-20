package no.nav.syfo

import io.ktor.util.KtorExperimentalAPI
import io.mockk.spyk
import kotlinx.coroutines.runBlocking
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.kafka.toProducerConfig
import no.nav.syfo.testutil.TestDB
import no.nav.syfo.testutil.stopApplicationNårTopicErLest
import no.nav.syfo.vedtak.db.finnVedtak
import no.nav.syfo.vedtak.kafka.VedtakConsumer
import no.nav.syfo.vedtak.service.VedtakService
import org.amshove.kluent.`should be equal to`
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.header.internals.RecordHeader
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.containers.Network
import java.util.*

@KtorExperimentalAPI
object VedtakSpek : Spek({
    describe("Mottak av melding paa kafka") {

        val testDb = TestDB()
        val kafka = KafkaContainer().withNetwork(Network.newNetwork())
        kafka.start()

        val kafkaConfig = Properties()
        kafkaConfig.let {
            it["bootstrap.servers"] = kafka.bootstrapServers
            it[ConsumerConfig.GROUP_ID_CONFIG] = "groupId"
            it[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
            it[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
            it[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
        }
        val consumerProperties = kafkaConfig.toConsumerConfig(
            "consumer", valueDeserializer = StringDeserializer::class
        )
        val producerProperties = kafkaConfig.toProducerConfig(
            "producer", valueSerializer = StringSerializer::class
        )

        val vedtakKafkaProducer = KafkaProducer<String, String>(producerProperties)

        val vedtakKafkaConsumer = spyk(KafkaConsumer<String, String>(consumerProperties))
        vedtakKafkaConsumer.subscribe(listOf("aapen-helse-sporbar"))


        val applicationState = ApplicationState()
        applicationState.ready = true
        applicationState.alive = true

        val vedtakConsumer = VedtakConsumer(vedtakKafkaConsumer)
        val vedtakService = VedtakService(
            database = testDb,
            applicationState = applicationState,
            vedtakConsumer = vedtakConsumer
        )



        it("Vedtak mottas og lagres i db") {
            val fnr = "13068700000"

            val vedtak = testDb.connection.finnVedtak(fnr)
            vedtak.size `should be equal to` 0

            vedtakKafkaProducer.send(
                ProducerRecord(
                    "aapen-helse-sporbar",
                    null,
                    fnr,
                    "{ \"vedtak\": 123}",
                    listOf(RecordHeader("type", "Vedtak".toByteArray()))
                )
            )

            stopApplicationNårTopicErLest(vedtakKafkaConsumer, applicationState)

            runBlocking {
                vedtakService.start()
            }

            val vedtakEtter = testDb.connection.finnVedtak(fnr)
            vedtakEtter.size `should be equal to` 1
        }

    }
})
