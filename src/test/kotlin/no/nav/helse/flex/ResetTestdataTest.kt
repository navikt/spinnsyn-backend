package no.nav.helse.flex

import no.nav.helse.flex.domene.UtbetalingUtbetalt
import no.nav.helse.flex.domene.VedtakFattetForEksternDto
import no.nav.helse.flex.testdata.TESTDATA_RESET_TOPIC
import org.amshove.kluent.`should be`
import org.amshove.kluent.shouldBeEmpty
import org.amshove.kluent.shouldHaveSize
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.awaitility.Awaitility
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.client.RestTemplate
import java.time.LocalDate
import java.util.*
import java.util.concurrent.TimeUnit

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class ResetTestdataTest : AbstractContainerBaseTest() {

    @Autowired
    lateinit var restTemplate: RestTemplate

    @Autowired
    lateinit var kafkaProducer: KafkaProducer<String, String>

    val fnr = "234232323"
    val fom = LocalDate.now().minusDays(7)
    val tom = LocalDate.now()
    val orgnummer = "123"
    val utbetalingId = "239fj20j3f"

    val vedtak = VedtakFattetForEksternDto(
        fødselsnummer = fnr,
        aktørId = fnr,
        organisasjonsnummer = orgnummer,
        fom = fom,
        tom = tom,
        skjæringstidspunkt = fom,
        dokumenter = emptyList(),
        inntekt = 0.0,
        sykepengegrunnlag = 0.0,
        utbetalingId = utbetalingId,
        grunnlagForSykepengegrunnlag = 0.0,
        grunnlagForSykepengegrunnlagPerArbeidsgiver = mutableMapOf("1234" to 0.0),
        begrensning = "VET_IKKE"
    )
    val utbetaling = UtbetalingUtbetalt(
        fødselsnummer = fnr,
        aktørId = fnr,
        organisasjonsnummer = orgnummer,
        fom = fom,
        tom = tom,
        utbetalingId = utbetalingId,
        event = "utbetaling_utbetalt",
        forbrukteSykedager = 42,
        foreløpigBeregnetSluttPåSykepenger = null,
        antallVedtak = 1,
        gjenståendeSykedager = 3254,
        automatiskBehandling = true,
        arbeidsgiverOppdrag = UtbetalingUtbetalt.OppdragDto(
            mottaker = orgnummer,
            fagområde = "SPREF",
            fagsystemId = "1234",
            nettoBeløp = 123,
            utbetalingslinjer = emptyList()
        ),
        type = "UTBETALING",
        utbetalingsdager = emptyList()
    )

    @Test
    @Order(1)
    fun `Oppretter vedtak`() {
        data class VedtakV2(val vedtak: String, val utbetaling: String?)

        val body = VedtakV2(vedtak.serialisertTilString(), utbetaling.serialisertTilString())

        mockMvc.perform(
            post("/api/v1/testdata/vedtak")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer ${loginserviceJwt(fnr)}")
                .content(body.serialisertTilString())
        ).andExpect(status().is2xxSuccessful).andReturn()
    }

    @Test
    @Order(2)
    fun `vi henter vedtaket`() {
        val vedtak = hentVedtakMedLoginserviceToken(fnr)

        vedtak shouldHaveSize 1
        vedtak.first().lest `should be` false
        vedtak.first().annullert `should be` false
    }

    @Test
    @Order(3)
    fun `Resetter personen via kafka`() {
        hentVedtakMedLoginserviceToken(fnr).shouldHaveSize(1)
        kafkaProducer.send(ProducerRecord(TESTDATA_RESET_TOPIC, UUID.randomUUID().toString(), fnr)).get()
        Awaitility.await().atMost(4, TimeUnit.SECONDS).until {
            hentVedtakMedLoginserviceToken(fnr).isEmpty()
        }
        hentVedtakMedLoginserviceToken(fnr).shouldBeEmpty()
    }
}
