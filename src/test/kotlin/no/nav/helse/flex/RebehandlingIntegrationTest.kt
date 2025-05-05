package no.nav.helse.flex

import no.nav.helse.flex.domene.UtbetalingUtbetalt
import no.nav.helse.flex.domene.UtbetalingUtbetalt.UtbetalingdagDto.Begrunnelse.MinimumSykdomsgrad
import no.nav.helse.flex.domene.VedtakFattetForEksternDto
import no.nav.helse.flex.domene.tilUtbetalingUtbetalt
import no.nav.helse.flex.domene.tilVedtakFattetForEksternDto
import no.nav.helse.flex.kafka.UTBETALING_TOPIC
import no.nav.helse.flex.kafka.VEDTAK_TOPIC
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
import org.springframework.web.client.RestTemplate
import java.time.LocalDate
import java.util.*
import java.util.concurrent.TimeUnit

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class RebehandlingIntegrationTest : FellesTestOppsett() {
    @Autowired
    lateinit var kafkaProducer: KafkaProducer<String, String>

    @Autowired
    lateinit var restTemplate: RestTemplate

    final val fnr = "1233342"
    final val aktørId = "321"
    final val org = "987"
    final val now = LocalDate.now()
    final val utbetalingId = "124542"
    val vedtak =
        VedtakFattetForEksternDto(
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
            begrensning = "VET_IKKE",
            vedtakFattetTidspunkt = LocalDate.now(),
        )

    val utbetaling =
        UtbetalingUtbetalt(
            fødselsnummer = fnr,
            aktørId = aktørId,
            organisasjonsnummer = org,
            fom = now,
            tom = now,
            utbetalingId = utbetalingId,
            event = "eventet",
            antallVedtak = 1,
            forbrukteSykedager = 42,
            foreløpigBeregnetSluttPåSykepenger = null,
            gjenståendeSykedager = 3254,
            automatiskBehandling = true,
            arbeidsgiverOppdrag =
                UtbetalingUtbetalt.OppdragDto(
                    mottaker = org,
                    fagområde = "SP",
                    fagsystemId = "1234",
                    nettoBeløp = 123,
                    utbetalingslinjer = emptyList(),
                ),
            type = "UTBETALING",
            utbetalingsdager =
                listOf(
                    UtbetalingUtbetalt.UtbetalingdagDto(
                        dato = now,
                        type = "AvvistDag",
                        begrunnelser = listOf(MinimumSykdomsgrad),
                    ),
                ),
        )

    @Test
    @Order(1)
    fun `mottar vedtak`() {
        kafkaProducer
            .send(
                ProducerRecord(
                    VEDTAK_TOPIC,
                    null,
                    fnr,
                    vedtak.serialisertTilString(),
                    listOf(RecordHeader("type", "VedtakFattet".toByteArray())),
                ),
            ).get()

        await().atMost(5, TimeUnit.SECONDS).until {
            vedtakRepository.findVedtakDbRecordsByFnr(fnr).isNotEmpty()
        }

        val hentetVedtak = vedtakRepository.findVedtakDbRecordsByFnr(fnr).first()
        hentetVedtak.vedtak
            .tilVedtakFattetForEksternDto()
            .fødselsnummer
            .shouldBeEqualTo(fnr)
        hentetVedtak.utbetalingId.shouldBeEqualTo(vedtak.utbetalingId)
    }

    @Test
    @Order(2)
    fun `finner ikke vedtaket`() {
        hentVedtakMedTokenXToken(fnr).shouldBeEmpty()
    }

    @Test
    @Order(3)
    fun `mottar utbetaling`() {
        kafkaProducer
            .send(
                ProducerRecord(
                    UTBETALING_TOPIC,
                    null,
                    fnr,
                    utbetaling.serialisertTilString(),
                ),
            ).get()

        await().atMost(5, TimeUnit.SECONDS).until {
            utbetalingRepository.findUtbetalingDbRecordsByFnr(fnr).isNotEmpty()
        }

        val dbUtbetaling = utbetalingRepository.findUtbetalingDbRecordsByFnr(fnr).first()
        dbUtbetaling.utbetaling
            .tilUtbetalingUtbetalt()
            .fødselsnummer
            .shouldBeEqualTo(fnr)
        dbUtbetaling.utbetalingId.shouldBeEqualTo(utbetaling.utbetalingId)
        dbUtbetaling.utbetalingType.shouldBeEqualTo("UTBETALING")
    }

    @Test
    @Order(4)
    fun `finner vedtaket `() {
        val vedtak = hentVedtakMedTokenXToken(fnr)
        vedtak.shouldHaveSize(1)
        vedtak
            .first()
            .vedtak.utbetaling.utbetalingType `should be equal to` "UTBETALING"
    }

    @Test
    @Order(5)
    fun `mottar en revurdering på vedtaket`() {
        val utbetalingsid = UUID.randomUUID().toString()
        kafkaProducer
            .send(
                ProducerRecord(
                    VEDTAK_TOPIC,
                    null,
                    fnr,
                    vedtak.copy(utbetalingId = utbetalingsid).serialisertTilString(),
                    listOf(RecordHeader("type", "VedtakFattet".toByteArray())),
                ),
            ).get()

        kafkaProducer
            .send(
                ProducerRecord(
                    UTBETALING_TOPIC,
                    null,
                    fnr,
                    utbetaling
                        .copy(utbetalingId = utbetalingsid, type = "REVURDERING")
                        .serialisertTilString(),
                ),
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
        val vedtak = hentVedtakMedTokenXToken(fnr).sortedBy { it.opprettetTimestamp }
        vedtak.shouldHaveSize(2)
        vedtak[0].vedtak.utbetaling.utbetalingType `should be equal to` "UTBETALING"
        vedtak[0].revurdert.`should be true`()
        vedtak[1].vedtak.utbetaling.utbetalingType `should be equal to` "REVURDERING"
        vedtak[1].revurdert.`should be false`()
    }

    @Test
    @Order(7)
    fun `mottar en revurdering på revurderinga`() {
        val utbetalingsid = UUID.randomUUID().toString()
        kafkaProducer
            .send(
                ProducerRecord(
                    VEDTAK_TOPIC,
                    null,
                    fnr,
                    vedtak.copy(utbetalingId = utbetalingsid).serialisertTilString(),
                    listOf(RecordHeader("type", "VedtakFattet".toByteArray())),
                ),
            ).get()

        kafkaProducer
            .send(
                ProducerRecord(
                    UTBETALING_TOPIC,
                    null,
                    fnr,
                    utbetaling
                        .copy(utbetalingId = utbetalingsid, type = "REVURDERING")
                        .serialisertTilString(),
                ),
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
    @Order(8)
    fun `finner vedtakene, to er revurdering, den ene revurderinga er selv blirrrevurdert `() {
        val vedtak = hentVedtakMedTokenXToken(fnr).sortedBy { it.opprettetTimestamp }
        vedtak.shouldHaveSize(3)
        vedtak[0].vedtak.utbetaling.utbetalingType `should be equal to` "UTBETALING"
        vedtak[0].revurdert.`should be true`()
        vedtak[1].vedtak.utbetaling.utbetalingType `should be equal to` "REVURDERING"
        vedtak[1].revurdert.`should be true`()
        vedtak[2].vedtak.utbetaling.utbetalingType `should be equal to` "REVURDERING"
        vedtak[2].revurdert.`should be false`()
    }
}
