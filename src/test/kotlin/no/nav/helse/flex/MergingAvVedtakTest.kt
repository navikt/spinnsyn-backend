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
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.client.RestTemplate
import java.time.LocalDate
import java.util.concurrent.TimeUnit

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class MergingAvVedtakTest : AbstractContainerBaseTest() {

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
    val vedtak1 = VedtakFattetForEksternDto(
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

    val vedtak2 = VedtakFattetForEksternDto(
        fødselsnummer = fnr,
        aktørId = aktørId,
        organisasjonsnummer = org,
        fom = now.plusDays(1),
        tom = now.plusDays(5),
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
        tom = now.plusDays(1),
        utbetalingId = utbetalingId,
        antallVedtak = 2,
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
        utbetalingsdager = listOf(
            UtbetalingdagDto(
                dato = now,
                type = "AvvistDag",
                begrunnelser = listOf("MinimumSykdomsgrad")
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
                vedtak1.serialisertTilString()
            )
        ).get()

        await().atMost(5, TimeUnit.SECONDS).until {
            vedtakRepository.findVedtakDbRecordsByFnr(fnr).isNotEmpty()
        }

        val hentetVedtak = vedtakRepository.findVedtakDbRecordsByFnr(fnr).first()
        hentetVedtak.vedtak.tilVedtakFattetForEksternDto().fødselsnummer.shouldBeEqualTo(fnr)
        hentetVedtak.utbetalingId.shouldBeEqualTo(vedtak1.utbetalingId)
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
        val vedtak = vedtakRepository.findByLestIsNullAndBrukernotifikasjonSendtIsNullAndUtbetalingIdIsNotNullAndBrukernotifikasjonUtelattIsNull()
        vedtak.shouldHaveSize(1)
    }

    @Test
    @Order(5)
    fun `ingen brukernotifkasjon går ut før det siste vedtaket er der`() {
        val antall = brukernotifikasjonService.prosseserVedtak()
        antall `should be equal to` 0
    }

    @Test
    @Order(6)
    fun `finner fortsatt ikke vedtaket`() {
        hentVedtak(fnr).shouldBeEmpty()
    }

    @Test
    @Order(7)
    fun `mottar det andre vedtaket`() {
        kafkaProducer.send(
            ProducerRecord(
                VEDTAK_TOPIC,
                null,
                fnr,
                vedtak2.serialisertTilString()
            )
        ).get()

        await().atMost(5, TimeUnit.SECONDS).until {
            vedtakRepository.findVedtakDbRecordsByFnr(fnr).size == 2
        }
    }

    @Test
    @Order(8)
    fun `finner vedtaket`() {
        val vedtak = hentVedtak(fnr)
        vedtak.shouldHaveSize(1)
        vedtak[0].annullert.`should be false`()
        vedtak[0].lest.`should be false`()
        vedtak[0].vedtak.fom `should be equal to` vedtak1.fom
        vedtak[0].vedtak.tom `should be equal to` vedtak2.tom
        vedtak[0].vedtak.utbetaling.utbetalingId `should be equal to` utbetalingId
    }

    @Test
    @Order(9)
    fun `finner begge vedtakene med queryen for brukernotifkasjon`() {
        val vedtak = vedtakRepository.findByLestIsNullAndBrukernotifikasjonSendtIsNullAndUtbetalingIdIsNotNullAndBrukernotifikasjonUtelattIsNull()
        vedtak.shouldHaveSize(2)
    }

    @Test
    @Order(10)
    fun `en brukernotifkasjon går ut når cronjobben kjøres`() {
        val antall = brukernotifikasjonService.prosseserVedtak()
        antall `should be equal to` 1

        val id = hentVedtak(fnr).first().id

        val oppgaver = oppgaveKafkaConsumer.ventPåRecords(antall = 1)
        doneKafkaConsumer.ventPåRecords(antall = 0)

        oppgaver.shouldHaveSize(1)

        val nokkel = oppgaver[0].key()
        nokkel.getSystembruker() shouldBeEqualTo systembruker

        val oppgave = oppgaver[0].value()

        oppgave.getFodselsnummer() shouldBeEqualTo fnr
        oppgave.getSikkerhetsnivaa() shouldBeEqualTo 4
        oppgave.getTekst() shouldBeEqualTo "Oppgave: Sykepengene dine er beregnet - se resultatet"
        oppgave.getLink() shouldBeEqualTo "blah"
        oppgave.getGrupperingsId() shouldBeEqualTo id
        oppgave.getEksternVarsling() shouldBeEqualTo true
    }

    @Test
    @Order(11)
    fun `finner ikke lengre vedtaket med queryen for brukernotifkasjon`() {
        val vedtak = vedtakRepository.findByLestIsNullAndBrukernotifikasjonSendtIsNullAndUtbetalingIdIsNotNullAndBrukernotifikasjonUtelattIsNull()
        vedtak.shouldBeEmpty()
    }

    @Test
    @Order(12)
    fun `vi leser vedtaket`() {

        val vedtak = hentVedtak(fnr)

        val dbVedtak = vedtakRepository.findVedtakDbRecordsByFnr(fnr).first { it.id == vedtak[0].id }
        vedtakRepository.save(dbVedtak.copy(lest = null))

        vedtak.shouldHaveSize(1)
        vedtak[0].lest.`should be false`()

        val vedtaksId = vedtak[0].id

        lesVedtak(fnr, vedtaksId) `should be equal to` "Leste vedtak $vedtaksId"

        lesVedtak(fnr, vedtaksId) `should be equal to` "Vedtak $vedtaksId er allerede lest"

        val dones = doneKafkaConsumer.ventPåRecords(antall = 1)
        oppgaveKafkaConsumer.ventPåRecords(antall = 0)
        dones.shouldHaveSize(1)

        val nokkel = dones[0].key()
        nokkel.getEventId() `should be equal to` vedtaksId

        val done = dones[0].value()
        done.getFodselsnummer() `should be equal to` fnr

        vedtakRepository.findVedtakDbRecordsByFnr(fnr).first { it.id == vedtak[0].id }.lest.`should not be null`()
    }
}
