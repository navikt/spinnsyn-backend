package no.nav.helse.flex.brukernotifkasjon

import no.nav.brukernotifikasjon.schemas.Done
import no.nav.brukernotifikasjon.schemas.Nokkel
import no.nav.brukernotifikasjon.schemas.Oppgave
import no.nav.helse.flex.logger
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.springframework.stereotype.Component

@Component
class BrukernotifikasjonKafkaProdusent(
    private val kafkaproducerOppgave: Producer<Nokkel, Oppgave>,
    private val kafkaproducerDone: Producer<Nokkel, Done>
) {
    val log = logger()

    fun opprettBrukernotifikasjonOppgave(nokkel: Nokkel, oppgave: Oppgave) {
        try {
            kafkaproducerOppgave.send(ProducerRecord(OPPGAVE_TOPIC, nokkel, oppgave)).get()
        } catch (e: Exception) {
            log.error("Noe gikk galt ved sending av oppgave med id ${nokkel.getEventId()}", e)
            throw e
        }
    }

    fun sendDonemelding(nokkel: Nokkel, done: Done) {
        try {
            kafkaproducerDone.send(ProducerRecord(DONE_TOPIC, nokkel, done)).get()
        } catch (e: Exception) {
            log.error("Noe gikk galt ved ferdigstilling av oppgave med id ${nokkel.getEventId()}", e)
            throw e
        }
    }
}

const val OPPGAVE_TOPIC = "aapen-brukernotifikasjon-nyOppgave-v1"
const val DONE_TOPIC = "aapen-brukernotifikasjon-done-v1"
