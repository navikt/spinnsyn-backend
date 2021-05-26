package no.nav.helse.flex

import no.nav.helse.flex.kafka.SPORBAR_TOPIC
import no.nav.helse.flex.kafka.UTBETALING_TOPIC
import no.nav.helse.flex.kafka.VEDTAK_TOPIC
import no.nav.helse.flex.vedtak.db.AnnulleringDAO
import no.nav.helse.flex.vedtak.db.UtbetalingRepository
import no.nav.helse.flex.vedtak.db.VedtakRepository
import no.nav.helse.flex.vedtak.domene.*
import org.amshove.kluent.*
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.header.internals.RecordHeader
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class NyeTopicIntegrationTest : AbstractContainerBaseTest() {

    @Autowired
    lateinit var kafkaProducer: KafkaProducer<String, String>

    @Autowired
    lateinit var vedtakRepository: VedtakRepository

    @Autowired
    lateinit var utbetalingRepository: UtbetalingRepository

    @Autowired
    lateinit var annulleringDAO: AnnulleringDAO

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

    val annulleringDto = AnnulleringDto(
        fødselsnummer = fnr,
        orgnummer = org,
        tidsstempel = LocalDateTime.now(),
        fom = now,
        tom = now
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
    fun `finner vedtaket i v2`() {
        val vedtak = hentVedtak(fnr)
        vedtak.shouldHaveSize(1)
        vedtak[0].annullert.`should be false`()
        vedtak[0].lest.`should be true`()
        vedtak[0].vedtak.utbetaling.utbetalingId `should be equal to` utbetalingId
    }

    @Test
    @Order(5)
    fun `vi endrer vedtaket til å være ulest`() {
        val dbVedtak = vedtakRepository.findVedtakDbRecordsByFnr(fnr).first()
        vedtakRepository.save(dbVedtak.copy(lest = null))

        val vedtak = hentVedtak(fnr)
        vedtak.shouldHaveSize(1)
        vedtak[0].lest.`should be false`()
    }

    @Test
    @Order(6)
    fun `vi leser vedtaket`() {
        val dbVedtak = vedtakRepository.findVedtakDbRecordsByFnr(fnr).first()
        vedtakRepository.save(dbVedtak.copy(lest = null))

        val vedtak = hentVedtak(fnr)

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

        vedtakRepository.findVedtakDbRecordsByFnr(fnr).first().lest.`should not be null`()
    }

    @Test
    @Order(7)
    fun `Ei annullering mottatt på kafka blir lagret i db`() {
        kafkaProducer.send(
            ProducerRecord(
                SPORBAR_TOPIC,
                null,
                fnr,
                annulleringDto.serialisertTilString(),
                listOf(RecordHeader("type", "Annullering".toByteArray()))
            )
        ).get()

        await().atMost(5, TimeUnit.SECONDS).until {
            annulleringDAO.finnAnnullering(fnr).size == 1
        }
    }

    @Test
    @Order(8)
    fun `vi finner vedtaket i v2 hvor det nå er annullert`() {
        val vedtak = hentVedtak(fnr)
        vedtak.shouldHaveSize(1)
        vedtak[0].annullert.`should be true`()
    }
}
