package no.nav.helse.flex

import no.nav.helse.flex.domene.*
import no.nav.helse.flex.domene.UtbetalingUtbetalt.UtbetalingdagDto
import no.nav.helse.flex.kafka.UTBETALING_TOPIC
import no.nav.helse.flex.kafka.VEDTAK_TOPIC
import no.nav.helse.flex.service.BrukernotifikasjonService
import org.amshove.kluent.*
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.web.client.RestTemplate
import java.time.LocalDate
import java.util.concurrent.TimeUnit

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class PersonoppdragIntegrationTest : AbstractContainerBaseTest() {

    @Autowired
    lateinit var kafkaProducer: KafkaProducer<String, String>

    @Autowired
    lateinit var restTemplate: RestTemplate

    @Autowired
    lateinit var brukernotifikasjonService: BrukernotifikasjonService

    final val fnr = "1233342"
    final val aktørId = "321"
    final val org = "987123129"
    final val now = LocalDate.now()
    val utbetalingId = "168465"
    val vedtak = VedtakFattetForEksternDto(
        fødselsnummer = fnr,
        aktørId = aktørId,
        organisasjonsnummer = org,
        fom = now,
        tom = now,
        skjæringstidspunkt = now,
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
        aktørId = aktørId,
        organisasjonsnummer = org,
        fom = now,
        tom = now,
        utbetalingId = utbetalingId,
        antallVedtak = 1,
        event = "eventet",
        forbrukteSykedager = 42,
        gjenståendeSykedager = 3254,
        foreløpigBeregnetSluttPåSykepenger = LocalDate.of(2020, 3, 12),
        automatiskBehandling = true,
        personOppdrag = UtbetalingUtbetalt.OppdragDto(
            mottaker = org,
            fagområde = "SP",
            fagsystemId = "1234",
            nettoBeløp = 123,
            utbetalingslinjer = listOf(
                UtbetalingUtbetalt.OppdragDto.UtbetalingslinjeDto(
                    dagsats = 123,
                    fom = now,
                    tom = now,
                    grad = 100.0,
                    stønadsdager = 1,
                    totalbeløp = 123
                )
            )
        ),
        type = "UTBETALING",
        utbetalingsdager = listOf(
            UtbetalingdagDto(
                dato = now,
                type = "NavDag",
                begrunnelser = emptyList()
            )
        )
    )

    @Test
    @Order(1)
    fun `mottar vedtak`() {
        kafkaProducer.send(
            ProducerRecord(
                VEDTAK_TOPIC,
                null,
                fnr,
                vedtak.serialisertTilString()
            )
        ).get()

        await().atMost(5, TimeUnit.SECONDS).until {
            vedtakRepository.findVedtakDbRecordsByFnr(fnr).isNotEmpty()
        }

        val hentetVedtak = vedtakRepository.findVedtakDbRecordsByFnr(fnr).first()
        hentetVedtak.vedtak.tilVedtakFattetForEksternDto().fødselsnummer.shouldBeEqualTo(fnr)
        hentetVedtak.utbetalingId.shouldBeEqualTo(vedtak.utbetalingId)
    }

    @Test
    @Order(2)
    fun `finner ikke vedtaket`() {
        hentVedtak(fnr).shouldBeEmpty()
    }

    @Test
    @Order(2)
    fun `ingen brukernotifkasjon går ut før utbetalinga er der`() {
        val antall = brukernotifikasjonService.prosseserUtbetaling()
        antall `should be equal to` 0
    }

    @Test
    @Order(3)
    fun `mottar utbetaling`() {
        kafkaProducer.send(
            ProducerRecord(
                UTBETALING_TOPIC,
                null,
                fnr,
                utbetaling.serialisertTilString()
            )
        ).get()

        await().atMost(5, TimeUnit.SECONDS).until {
            utbetalingRepository.findUtbetalingDbRecordsByFnr(fnr).isNotEmpty()
        }

        val dbUtbetaling = utbetalingRepository.findUtbetalingDbRecordsByFnr(fnr).first()
        dbUtbetaling.utbetaling.tilUtbetalingUtbetalt().fødselsnummer.shouldBeEqualTo(fnr)
        dbUtbetaling.utbetalingId.shouldBeEqualTo(utbetaling.utbetalingId)
        dbUtbetaling.utbetalingType.shouldBeEqualTo("UTBETALING")
    }

    @Test
    @Order(4)
    fun `finner utbetalingen med query for brukernotifkasjon`() {
        val utbetaling =
            utbetalingRepository.findByLestIsNullAndBrukernotifikasjonSendtIsNullAndUtbetalingIdIsNotNullAndBrukernotifikasjonUtelattIsNull()
        utbetaling.shouldHaveSize(1)
    }

    @Test
    @Order(4)
    fun `finner vedtaket i v2`() {
        val vedtak = hentVedtak(fnr)
        vedtak.shouldHaveSize(1)
        vedtak[0].annullert.`should be false`()
        vedtak[0].lest.`should be false`()
        vedtak[0].orgnavn `should be equal to` org
        vedtak[0].sykepengebelop `should be equal to` 0
        vedtak[0].sykepengebelopArbeidsgiver `should be equal to` 0
        vedtak[0].sykepengebelopPerson `should be equal to` 123

        vedtak[0].vedtak.utbetaling.foreløpigBeregnetSluttPåSykepenger `should be equal to` LocalDate.of(2020, 3, 12)
        vedtak[0].vedtak.utbetaling.utbetalingId `should be equal to` utbetalingId
        vedtak[0].vedtak.utbetaling.arbeidsgiverOppdrag.shouldBeNull() // Jsonignore
        vedtak[0].vedtak.utbetaling.personOppdag.shouldBeNull() // Jsonignore
        vedtak[0].vedtak.utbetaling.utbetalingsdager[0].dato `should be equal to` now
        vedtak[0].vedtak.utbetaling.utbetalingsdager[0].type `should be equal to` "NavDag"
        vedtak[0].dager.shouldBeEmpty()
        vedtak[0].dagerArbeidsgiver.shouldBeEmpty()
        vedtak[0].dagerPerson.shouldHaveSize(1)
        vedtak[0].dagerPerson[0].dagtype `should be equal to` "NavDagSyk"
    }
}