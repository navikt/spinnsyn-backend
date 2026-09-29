package no.nav.helse.flex.kafka.producer

import no.nav.helse.flex.logger
import no.nav.helse.flex.serialisertTilString
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import java.net.URI
import java.time.Instant
import java.util.UUID

const val AUDIT_LOG_TOPIC = "flex.auditlogging"

@Component
class AuditLogProducer(
    @Qualifier("auditLogKafkaProducer")
    private val auditLogProducer: Producer<String, String>,
) {
    private val log = logger()

    fun publiser(auditEntry: AuditEntry) {
        try {
            val key = UUID.randomUUID().toString()
            val record = ProducerRecord(AUDIT_LOG_TOPIC, key, auditEntry.serialisertTilString())
            auditLogProducer.send(record).get()
        } catch (e: Exception) {
            log.error("Klarte ikke publisere audit-hendelse", e)
            throw e
        }
    }
}

data class AuditEntry(
    val appNavn: String,
    val utførtAv: String,
    val oppslagPå: String,
    val eventType: EventType,
    val forespørselTillatt: Boolean,
    val oppslagUtførtTid: Instant = Instant.now(),
    val beskrivelse: String,
    val requestUrl: URI,
    val requestMethod: String,
)

enum class EventType(
    val logString: String,
) {
    CREATE("audit:create"),
    READ("audit:access"),
    UPDATE("audit:update"),
    DELETE("audit:delete"),
}
