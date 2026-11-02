package no.nav.helse.flex.kafka

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.kafka.producer.AUDIT_LOG_TOPIC
import no.nav.helse.flex.kafka.producer.AuditEntry
import no.nav.helse.flex.kafka.producer.AuditLogProducer
import no.nav.helse.flex.kafka.producer.EventType
import no.nav.helse.flex.objectMapper
import no.nav.helse.flex.ventPåRecords
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.common.TopicPartition
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import tools.jackson.module.kotlin.readValue
import java.net.URI
import java.util.UUID

class AuditLogProducerTest : FellesTestOppsett() {
    @Autowired
    lateinit var auditLogProducer: AuditLogProducer

    @Autowired
    lateinit var auditKafkaConsumer: Consumer<String, String>

    @Test
    fun `publiserer auditmelding med forventet felter`() {
        auditLogProducer.publiser(
            AuditEntry(
                appNavn = "spinnsyn-backend",
                utførtAv = "Z999999",
                oppslagPå = "oppvarming",
                eventType = EventType.READ,
                forespørselTillatt = true,
                beskrivelse = "Oppretter audit-topic før consumer-posisjonering",
                requestUrl = URI.create("/internal/test"),
                requestMethod = "POST",
            ),
        )

        val partition = TopicPartition(AUDIT_LOG_TOPIC, 0)
        auditKafkaConsumer.assign(listOf(partition))
        auditKafkaConsumer.seekToEnd(listOf(partition))
        auditKafkaConsumer.position(partition)

        val entry =
            AuditEntry(
                appNavn = "spinnsyn-backend",
                utførtAv = "Z999999",
                oppslagPå = "12345678901",
                eventType = EventType.READ,
                forespørselTillatt = true,
                beskrivelse = "Test auditmelding",
                requestUrl = URI.create("/api/v1/flex/vedtak/soknad"),
                requestMethod = "POST",
            )

        auditLogProducer.publiser(entry)

        val records = auditKafkaConsumer.ventPåRecords(1)
        val record = records.first()
        UUID.fromString(record.key()).shouldNotBeNull()

        val payload: AuditEntry = objectMapper.readValue(record.value())
        payload.appNavn shouldBe "spinnsyn-backend"
        payload.utførtAv shouldBe "Z999999"
        payload.oppslagPå shouldBe "12345678901"
        payload.eventType shouldBe EventType.READ
        payload.forespørselTillatt shouldBe true
        payload.beskrivelse shouldBe "Test auditmelding"
        payload.requestUrl shouldBe URI.create("/api/v1/flex/vedtak/soknad")
        payload.requestMethod shouldBe "POST"
        payload.oppslagUtførtTid.shouldNotBeNull()
    }
}
