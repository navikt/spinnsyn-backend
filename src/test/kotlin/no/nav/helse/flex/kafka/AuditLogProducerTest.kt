package no.nav.helse.flex.kafka

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.kafka.producer.AUDIT_LOG_TOPIC
import no.nav.helse.flex.kafka.producer.AuditEntry
import no.nav.helse.flex.kafka.producer.AuditLogProducer
import no.nav.helse.flex.kafka.producer.EventType
import no.nav.helse.flex.objectMapper
import no.nav.helse.flex.ventPåRecords
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeNull
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.common.TopicPartition
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
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
        payload.appNavn shouldBeEqualTo "spinnsyn-backend"
        payload.utførtAv shouldBeEqualTo "Z999999"
        payload.oppslagPå shouldBeEqualTo "12345678901"
        payload.eventType shouldBeEqualTo EventType.READ
        payload.forespørselTillatt shouldBeEqualTo true
        payload.beskrivelse shouldBeEqualTo "Test auditmelding"
        payload.requestUrl shouldBeEqualTo URI.create("/api/v1/flex/vedtak/soknad")
        payload.requestMethod shouldBeEqualTo "POST"
        payload.oppslagUtførtTid.shouldNotBeNull()
    }
}
