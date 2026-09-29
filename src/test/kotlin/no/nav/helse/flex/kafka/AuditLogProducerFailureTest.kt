package no.nav.helse.flex.kafka

import no.nav.helse.flex.kafka.producer.AuditEntry
import no.nav.helse.flex.kafka.producer.AuditLogProducer
import no.nav.helse.flex.kafka.producer.EventType
import org.apache.kafka.clients.producer.MockProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.serialization.StringSerializer
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.net.URI
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException

class AuditLogProducerFailureTest {
    private class FeilendeProducer : MockProducer<String, String>(true, StringSerializer(), StringSerializer()) {
        override fun send(record: ProducerRecord<String, String>?): CompletableFuture<RecordMetadata> {
            val failedFuture = CompletableFuture<RecordMetadata>()
            failedFuture.completeExceptionally(RuntimeException("simulert kafka-feil"))
            return failedFuture
        }
    }

    @Test
    fun `kaster videre ved feil under kafka-publisering`() {
        val producer = FeilendeProducer()

        val auditLogProducer = AuditLogProducer(producer)

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

        assertThrows(ExecutionException::class.java) {
            auditLogProducer.publiser(entry)
        }
    }
}
