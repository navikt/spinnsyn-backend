package no.nav.helse.flex

import no.nav.helse.flex.kafka.SPORBAR_TOPIC
import no.nav.helse.flex.vedtak.db.VedtakDAO
import no.nav.helse.flex.vedtak.domene.VedtakDto
import org.amshove.kluent.`should be`
import org.amshove.kluent.shouldBeEmpty
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldHaveSize
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.header.internals.RecordHeader
import org.awaitility.Awaitility
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate
import java.util.concurrent.TimeUnit

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class IntegrationTest : AbstractContainerBaseTest() {

    @Autowired
    lateinit var onpremKafkaProducer: KafkaProducer<String, String>

    @Autowired
    lateinit var vedtakDAO: VedtakDAO

    @Value("\${on-prem-kafka.username}") lateinit var systembruker: String

    val fnr = "123"
    val fnr2 = "101001001"
    val automatiskBehandletVedtak = VedtakDto(
        fom = LocalDate.now(),
        tom = LocalDate.now(),
        forbrukteSykedager = 1,
        gjenståendeSykedager = 2,
        utbetalinger = emptyList(),
        dokumenter = emptyList(),
        automatiskBehandling = true
    )

    @Test
    @Order(1)
    fun `mottar vedtak`() {
        onpremKafkaProducer.send(
            ProducerRecord(
                SPORBAR_TOPIC,
                null,
                fnr,
                automatiskBehandletVedtak.serialisertTilString(),
                listOf(RecordHeader("type", "Vedtak".toByteArray()))
            )
        ).get()

        Awaitility.await().atMost(5, TimeUnit.SECONDS).until {
            vedtakDAO.finnVedtak(fnr).isNotEmpty()
        }

        val id = vedtakDAO.finnVedtak(fnr).first().id

        val oppgaver = oppgaveKafkaConsumer.ventPåRecords(antall = 1)
        doneKafkaConsumer.ventPåRecords(antall = 0)

        oppgaver.shouldHaveSize(1)

        val nokkel = oppgaver[0].key()
        nokkel.getSystembruker() shouldBeEqualTo systembruker

        val oppgave = oppgaver[0].value()

        oppgave.getFodselsnummer() shouldBeEqualTo fnr
        oppgave.getSikkerhetsnivaa() shouldBeEqualTo 4
        oppgave.getTekst() shouldBeEqualTo "Sykepengene dine er beregnet - se resultatet"
        oppgave.getLink() shouldBeEqualTo "blah/vedtak/$id"
        oppgave.getGrupperingsId() shouldBeEqualTo id
        oppgave.getEksternVarsling() shouldBeEqualTo true
    }

    @Test
    @Order(2)
    fun `henter vedtak`() {
        val vedtak = hentVedtak(fnr)

        vedtak shouldHaveSize 1
        vedtak.first().lest `should be` false
    }

    @Test
    @Order(3)
    fun `les vedtak`() {
        val vedtaksId = hentVedtak(fnr).first().id
        val bleLest = lesVedtak(fnr, vedtaksId)

        bleLest shouldBeEqualTo "Leste vedtak $vedtaksId"

        val oppdatertVedtak = hentVedtak(fnr)
        oppdatertVedtak.first().lest shouldBeEqualTo true

        val dones = doneKafkaConsumer.ventPåRecords(antall = 1)
        oppgaveKafkaConsumer.ventPåRecords(antall = 0)
        dones.shouldHaveSize(1)

        val nokkel = dones[0].key()
        nokkel.getEventId() shouldBeEqualTo vedtaksId
        nokkel.getSystembruker() shouldBeEqualTo systembruker

        val done = dones[0].value()
        done.getFodselsnummer() shouldBeEqualTo fnr
        done.getGrupperingsId() shouldBeEqualTo vedtaksId
    }

    @Test
    @Order(4)
    fun `leser vedtak på nytt og ingenting skjer`() {
        val vedtaksId = hentVedtak(fnr).first().id
        val bleLest = lesVedtak(fnr, vedtaksId)

        bleLest shouldBeEqualTo "Vedtak $vedtaksId er allerede lest"

        doneKafkaConsumer.ventPåRecords(antall = 0)
    }

    @Test
    @Order(5)
    fun `leser vedtak som ikke finnes`() {
        mockMvc.perform(
            post("/api/v1/vedtak/finnes-ikke/les")
                .header("Authorization", "Bearer ${jwt(fnr)}")
                .contentType(MediaType.APPLICATION_JSON)
        ).andExpect(status().isNotFound)
    }

    @Test
    @Order(6)
    fun `Får ikke opp andre personers vedtak`() {
        hentVedtak(fnr2).shouldBeEmpty()
    }
}
