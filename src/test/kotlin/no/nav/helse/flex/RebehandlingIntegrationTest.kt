package no.nav.helse.flex

import no.nav.helse.flex.domene.*
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
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.client.RestTemplate
import java.time.LocalDate
import java.util.*
import java.util.concurrent.TimeUnit

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class RebehandlingIntegrationTest : AbstractContainerBaseTest() {

    @Autowired
    lateinit var kafkaProducer: KafkaProducer<String, String>

    @Autowired
    lateinit var restTemplate: RestTemplate

    @Autowired
    lateinit var brukernotifikasjonService: BrukernotifikasjonService

    @Value("\${on-prem-kafka.username}")
    lateinit var systembruker: String

    final val fnr = "1233342"
    final val aktørId = "321"
    final val org = "987"
    final val now = LocalDate.now()
    val utbetalingId = "124542"
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
        utbetalingId = utbetalingId
    )

    val utbetaling = UtbetalingUtbetalt(
        fødselsnummer = fnr,
        aktørId = aktørId,
        organisasjonsnummer = org,
        fom = now,
        tom = now,
        utbetalingId = utbetalingId,
        event = "eventet",
        forbrukteSykedager = 42,
        gjenståendeSykedager = 3254,
        automatiskBehandling = true,
        arbeidsgiverOppdrag = UtbetalingUtbetalt.OppdragDto(
            mottaker = org,
            fagområde = "SP",
            fagsystemId = "1234",
            nettoBeløp = 123,
            utbetalingslinjer = emptyList()
        ),
        type = "UTBETALING",
        utbetalingsdager = emptyList()
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
        val antall = brukernotifikasjonService.prosseserVedtak()
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
    fun `finner vedtaket med queryen for brukernotifkasjon`() {
        val vedtak =
            vedtakRepository.findByLestIsNullAndBrukernotifikasjonSendtIsNullAndUtbetalingIdIsNotNullAndBrukernotifikasjonUtelattIsNull()
        vedtak.shouldHaveSize(1)
    }

    @Test
    @Order(4)
    fun `finner vedtaket `() {
        val vedtak = hentVedtak(fnr)
        vedtak.shouldHaveSize(1)
        vedtak.first().vedtak.utbetaling.utbetalingType `should be equal to` "UTBETALING"
    }

    @Test
    @Order(5)
    fun `mottar en revurdering på vedtaket`() {
        val utbetalingsid = UUID.randomUUID().toString()
        kafkaProducer.send(
            ProducerRecord(
                VEDTAK_TOPIC,
                null,
                fnr,
                vedtak.copy(utbetalingId = utbetalingsid).serialisertTilString()
            )
        ).get()

        kafkaProducer.send(
            ProducerRecord(
                UTBETALING_TOPIC,
                null,
                fnr,
                utbetaling
                    .copy(utbetalingId = utbetalingsid, type = "REVURDERING")
                    .serialisertTilString()
            )
        ).get()

        await().atMost(5, TimeUnit.SECONDS).until {
            vedtakRepository.findVedtakDbRecordsByFnr(fnr).size == 2
        }

        await().atMost(5, TimeUnit.SECONDS).until {
            utbetalingRepository.findUtbetalingDbRecordsByFnr(fnr).size == 2
        }

        val dbUtbetaling = utbetalingRepository.findUtbetalingDbRecordsByFnr(fnr).sortedBy { it.opprettet }.last()
        dbUtbetaling.utbetalingType.shouldBeEqualTo("REVURDERING")
    }

    @Test
    @Order(6)
    fun `finner vedtakene, en er revurdering, det andre er revurdert `() {
        val vedtak = hentVedtak(fnr).sortedBy { it.opprettetTimestamp }
        vedtak.shouldHaveSize(2)
        vedtak[0].vedtak.utbetaling.utbetalingType `should be equal to` "UTBETALING"
        vedtak[0].revurdert.`should be true`()
        vedtak[1].vedtak.utbetaling.utbetalingType `should be equal to` "REVURDERING"
        vedtak[1].revurdert.`should be false`()
    }

    @Test
    @Order(7)
    fun `2 brukernotifkasjoner går ut når cronjobben kjøres`() {
        val antall = brukernotifikasjonService.prosseserVedtak()
        antall `should be equal to` 2

        oppgaveKafkaConsumer.ventPåRecords(antall = 2)
        doneKafkaConsumer.ventPåRecords(antall = 0)
    }

    @Test
    @Order(8)
    fun `finner ikke lengre vedtaket med queryen for brukernotifkasjon`() {
        val vedtak =
            vedtakRepository.findByLestIsNullAndBrukernotifikasjonSendtIsNullAndUtbetalingIdIsNotNullAndBrukernotifikasjonUtelattIsNull()
        vedtak.shouldBeEmpty()
    }

    @Test
    @Order(9)
    fun `mottar en revurdering på revurderinga`() {
        val utbetalingsid = UUID.randomUUID().toString()
        kafkaProducer.send(
            ProducerRecord(
                VEDTAK_TOPIC,
                null,
                fnr,
                vedtak.copy(utbetalingId = utbetalingsid).serialisertTilString()
            )
        ).get()

        kafkaProducer.send(
            ProducerRecord(
                UTBETALING_TOPIC,
                null,
                fnr,
                utbetaling
                    .copy(utbetalingId = utbetalingsid, type = "REVURDERING")
                    .serialisertTilString()
            )
        ).get()

        await().atMost(5, TimeUnit.SECONDS).until {
            vedtakRepository.findVedtakDbRecordsByFnr(fnr).size == 3
        }

        await().atMost(5, TimeUnit.SECONDS).until {
            utbetalingRepository.findUtbetalingDbRecordsByFnr(fnr).size == 3
        }

        val dbUtbetaling = utbetalingRepository.findUtbetalingDbRecordsByFnr(fnr).sortedBy { it.opprettet }.last()
        dbUtbetaling.utbetalingType.shouldBeEqualTo("REVURDERING")
    }

    @Test
    @Order(10)
    fun `finner vedtakene, to er revurdering, den ene revurderinga er selv blirrrevurdert `() {
        val vedtak = hentVedtak(fnr).sortedBy { it.opprettetTimestamp }
        vedtak.shouldHaveSize(3)
        vedtak[0].vedtak.utbetaling.utbetalingType `should be equal to` "UTBETALING"
        vedtak[0].revurdert.`should be true`()
        vedtak[1].vedtak.utbetaling.utbetalingType `should be equal to` "REVURDERING"
        vedtak[1].revurdert.`should be true`()
        vedtak[2].vedtak.utbetaling.utbetalingType `should be equal to` "REVURDERING"
        vedtak[2].revurdert.`should be false`()
    }
}
