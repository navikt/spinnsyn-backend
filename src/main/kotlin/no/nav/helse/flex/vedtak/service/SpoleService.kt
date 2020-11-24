package no.nav.helse.flex.vedtak.service

import kotlinx.coroutines.delay
import no.nav.helse.flex.Environment
import no.nav.helse.flex.application.ApplicationState
import no.nav.helse.flex.db.DatabaseInterface
import no.nav.helse.flex.log
import no.nav.helse.flex.util.PodLeaderCoordinator
import no.nav.helse.flex.vedtak.db.finnVedtak
import no.nav.helse.flex.vedtak.domene.tilVedtakDto
import no.nav.syfo.kafka.envOverrides
import no.nav.syfo.kafka.loadBaseConfig
import no.nav.syfo.kafka.toConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.StringDeserializer
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

class SpoleService(
    private val podLeaderCoordinator: PodLeaderCoordinator,
    private val database: DatabaseInterface,
    private val applicationState: ApplicationState,
    private val env: Environment,
    private val delayStart: Long = 100_000L,
    private val topicName: String = "aapen-helse-sporbar"
) {
    suspend fun start() {
        try {
            // Venter til leader er overført
            log.info("SpoleService venter $delayStart ms før start")
            delay(delayStart)

            if (!podLeaderCoordinator.isLeader()) {
                log.info("SpoleService kjører bare for podLeader")
                return
            } else {
                log.info("Jeg er SpoleService leader!")
                val consumer = consumer()
                seek(consumer)
                job(consumer)
                log.info("Avslutter SpoleService")
            }
        } catch (e: Exception) {
            log.info("SpoleService exception: ${e.message}", e)
        }
    }

    private fun consumer(): KafkaConsumer<String, String> {
        val config = loadBaseConfig(env, env.hentKafkaCredentials()).envOverrides()
        config["auto.offset.reset"] = "none"
        val properties = config.toConsumerConfig("spinnsyn-backend-spole-consumer", StringDeserializer::class)
        properties.let {
            it[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = "1"
        }
        return KafkaConsumer(properties)
    }

    private fun seek(consumer: KafkaConsumer<String, String>) {
        // TargetTime in ms
        val targetTime = ZonedDateTime.of(
            2020, 11, 2, 0, 0, 0, 0,
            ZoneId.of("Europe/Oslo")
        ).toInstant().toEpochMilli().also { log.info("SpoleService targetTime = $it") }
        // Get the list of partitions
        val partitionInfos = consumer.partitionsFor(topicName)
        // Transform PartitionInfo into TopicPartition
        val topicPartitionList: List<TopicPartition> = partitionInfos
            .map { info -> TopicPartition(topicName, info.partition()) }
        // Assign the consumer to these partitions
        consumer.assign(topicPartitionList)
        // Look for offsets based on timestamp
        val partitionTimestampMap: Map<TopicPartition, Long> = topicPartitionList
            .map { tp -> tp to targetTime }
            .toMap()
        // Find earliest offset whose timestamp is greater than or equal to the given timestamp
        val partitionOffsetMap = consumer
            .offsetsForTimes(partitionTimestampMap)
            .also { log.info("SpoleService partitionOffsetMap $it") }
        // Force the consumer to seek for those offsets
        partitionOffsetMap.forEach { (tp, offsetAndTimestamp) ->
            consumer.seek(tp, offsetAndTimestamp?.offset() ?: 0)
        }
    }

    private fun job(consumer: KafkaConsumer<String, String>) {
        var loop = true
        var poll = consumer.poll(Duration.ofMillis(1000))
        log.info("SpoleService starter fra timestamp: ${poll.first().timestamp()}, topic: ${poll.first().topic()}, partition: ${poll.first().partition()}")

        while (applicationState.ready && loop) {
            poll.forEach { cr ->
                if (cr.headers().any { it.key() == "type" && String(it.value()) == "Vedtak" }) {
                    cr.value().runCatching {
                        tilVedtakDto()
                    }.onFailure { e ->
                        log.info("SpoleService kunne ikke deserialisere vedtak, fortsetter", e)
                    }.onSuccess { vedtakDto ->
                        database
                            .finnVedtak(fnr = cr.key())
                            .firstOrNull { it.vedtak == vedtakDto }
                            ?: run {
                                val id = UUID.nameUUIDFromBytes("${cr.partition()}-${cr.offset()}".toByteArray())
                                log.info("SpoleService finner ikke vedtak id: $id, partition: ${cr.partition()}, offset: ${cr.offset()}")
                            }
                    }
                    consumer.commitSync()
                }
            }

            poll = consumer.poll(Duration.ofMillis(1000))
            if (poll.isEmpty) {
                loop = false
                consumer.commitSync()
            }
        }
    }
}