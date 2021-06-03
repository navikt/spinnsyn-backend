package no.nav.helse.flex

import no.nav.helse.flex.db.VedtakDAO
import no.nav.helse.flex.db.VedtakTestDAO
import no.nav.helse.flex.domene.VedtakDto
import no.nav.helse.flex.domene.VedtakDto.UtbetalingDto
import no.nav.helse.flex.domene.VedtakDto.UtbetalingDto.UtbetalingslinjeDto
import no.nav.helse.flex.kafka.SPORBAR_TOPIC
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
import org.springframework.test.web.client.MockRestServiceServer.createServer
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.client.RestTemplate
import java.time.LocalDate
import java.util.concurrent.TimeUnit

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class IntegrationTest : AbstractContainerBaseTest() {

    @Autowired
    lateinit var onpremKafkaProducer: KafkaProducer<String, String>

    @Autowired
    lateinit var vedtakDAO: VedtakDAO

    @Autowired
    lateinit var vedtakTestDAO: VedtakTestDAO

    @Autowired
    lateinit var restTemplate: RestTemplate

    @Value("\${on-prem-kafka.username}")
    lateinit var systembruker: String

    final val fnr = "123"
    final val fnr2 = "101001001"
    final val orgnummer = "999999999"
    val automatiskBehandletVedtak = VedtakDto(
        fom = LocalDate.now(),
        tom = LocalDate.now(),
        forbrukteSykedager = 1,
        gjenståendeSykedager = 2,
        organisasjonsnummer = orgnummer,
        utbetalinger = listOf(
            UtbetalingDto(
                mottaker = orgnummer,
                fagområde = "SPREF",
                totalbeløp = 42,
                utbetalingslinjer = listOf(
                    UtbetalingslinjeDto(
                        fom = LocalDate.now(),
                        tom = LocalDate.now(),
                        dagsats = 12,
                        beløp = 33,
                        grad = 100.0,
                        sykedager = 3
                    )
                )
            )
        ),
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
            vedtakTestDAO.finnVedtakEtterMigrering(fnr).isNotEmpty()
        }
    }

    @Test
    @Order(2)
    fun `vi henter vedtaket etter at det er satt mottatt før migrering`() {
        hentVedtak(fnr).shouldBeEmpty()
        val id = vedtakTestDAO.finnVedtakEtterMigrering(fnr).first().id
        vedtakTestDAO.merkVedtakMottattFørMigrering(id)

        val vedtak = hentVedtak(fnr)

        vedtak shouldHaveSize 1
        vedtak.first().lest `should be` false
    }

    @Test
    @Order(2)
    fun `vi kan ikke hente vedtaket uten token`() {
        mockMvc.perform(
            get("/api/v2/vedtak")
                .header("Authorization", "Bearer blabla")
                .contentType(MediaType.APPLICATION_JSON)
        ).andExpect(status().isUnauthorized)

        mockMvc.perform(
            get("/api/v2/vedtak")
                .contentType(MediaType.APPLICATION_JSON)
        ).andExpect(status().isUnauthorized)
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

    @Test
    @Order(7)
    fun `En veileder med tilgang kan hente vedtaket`() {

        val mockSyfotilgangscontrollServer = createServer(restTemplate)
        val veilederToken = veilederToken()
        mockSyfotilgangscontrollServer.mockTilgangskontrollResponse(
            tilgang = true,
            fnr = fnr,
            veilederToken = veilederToken
        )
        val vedtak = hentVedtakSomVeileder(fnr, veilederToken)

        vedtak shouldHaveSize 1
        vedtak.first().lest `should be` true
        mockSyfotilgangscontrollServer.verify()
    }

    @Test
    @Order(8)
    fun `En veileder uten tilgang kan ikke hente vedtaket`() {

        val mockSyfotilgangscontrollServer = createServer(restTemplate)
        val veilederToken = veilederToken()
        mockSyfotilgangscontrollServer.mockTilgangskontrollResponse(
            tilgang = false,
            fnr = fnr,
            veilederToken = veilederToken
        )

        mockMvc.perform(
            get("/api/v1/veileder/vedtak?fnr=$fnr")
                .header("Authorization", "Bearer $veilederToken")
                .contentType(MediaType.APPLICATION_JSON)
        ).andExpect(status().isForbidden).andReturn()

        mockSyfotilgangscontrollServer.verify()
    }
}
