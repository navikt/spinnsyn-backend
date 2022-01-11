package no.nav.helse.flex.done

import no.nav.brukernotifikasjon.schemas.Done
import no.nav.brukernotifikasjon.schemas.Nokkel
import no.nav.helse.flex.AbstractContainerBaseTest
import no.nav.helse.flex.brukernotifkasjon.DONE_TOPIC
import no.nav.helse.flex.hentProduserteRecords
import no.nav.helse.flex.subscribeHvisIkkeSubscribed
import no.nav.helse.flex.ventPåRecords
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.shouldBeEmpty
import org.apache.kafka.clients.consumer.Consumer
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import java.util.concurrent.TimeUnit

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class DoneUlesteVedtakTest() : AbstractContainerBaseTest() {

    @Autowired
    private lateinit var doneOppgaveKafkaConsumer: Consumer<Nokkel, Done>

    @Autowired
    private lateinit var doneUlesteVedtakJob: DoneUlesteVedtakJob

    @BeforeAll
    fun subscribeTilTopic() {
        doneOppgaveKafkaConsumer.subscribeHvisIkkeSubscribed(DONE_TOPIC)
        doneOppgaveKafkaConsumer.hentProduserteRecords().shouldBeEmpty()
    }

    @Test
    @Order(1)
    fun `send done-melding for uleste vedtak`() {
        opprettUlestVedtak("uuid-1", "fnr-1")
        opprettUlestVedtak("uuid-2", "fnr-1")
        opprettUlestVedtak("uuid-3", "fnr-2")

        doneUlesteVedtakJob.doneUlesteVedtak()

        val records = doneOppgaveKafkaConsumer.ventPåRecords(3)
        records.size `should be equal to` 3

        records[0].value().get("fodselsnummer") `should be equal to` "fnr-1"
        records[0].value().get("grupperingsId") `should be equal to` "uuid-1"
        records[1].value().get("fodselsnummer") `should be equal to` "fnr-1"
        records[1].value().get("grupperingsId") `should be equal to` "uuid-2"
        records[2].value().get("fodselsnummer") `should be equal to` "fnr-2"
        records[2].value().get("grupperingsId") `should be equal to` "uuid-3"

        await().atMost(5, TimeUnit.SECONDS).until {
            tellVedtakMedTidspunktForDoneMelding() == 3
        }
    }

    @Test
    @Order(2)
    fun `det skal ikke finnes flere vedtak å sende done-melding for`() {
        doneUlesteVedtakJob.doneUlesteVedtak()
        await().atMost(5, TimeUnit.SECONDS).until { doneOppgaveKafkaConsumer.ventPåRecords(0).isEmpty() }
    }

    private fun opprettUlestVedtak(id: String, fnr: String) {
        namedParameterJdbcTemplate.update(
            """
            INSERT INTO done_vedtak (id, fnr, type) 
            VALUES (:id, :fnr, :type)
        """,
            MapSqlParameterSource()
                .addValue("id", id)
                .addValue("fnr", fnr)
                .addValue("type", "UTBETALING")
        )
    }

    private fun tellVedtakMedTidspunktForDoneMelding(): Int {
        return jdbcTemplate.queryForObject(
            """
            SELECT count(id) 
            FROM done_vedtak 
            WHERE done_sendt IS NOT NULL
            """,
            Int::class.java
        )!!
    }
}
