package no.nav.helse.flex

import no.nav.helse.flex.domene.VedtakFattetForEksternDto
import no.nav.helse.flex.domene.tilUtbetalingUtbetalt
import no.nav.helse.flex.domene.tilVedtakFattetForEksternDto
import no.nav.helse.flex.kafka.UTBETALING_TOPIC
import no.nav.helse.flex.kafka.VEDTAK_TOPIC
import no.nav.helse.flex.testdata.lagArbeidsgiverOppdrag
import no.nav.helse.flex.testdata.lagUtbetaling
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should be false`
import org.amshove.kluent.`should not be null`
import org.amshove.kluent.shouldBeEmpty
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldHaveSize
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.header.internals.RecordHeader
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate
import java.util.concurrent.TimeUnit

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class LesVedtakTest : FellesTestOppsett() {
    @Autowired
    lateinit var kafkaProducer: KafkaProducer<String, String>

    final val fnr = "1233342"
    final val aktørId = "321"
    final val org = "987"
    final val now = LocalDate.now()
    final val utbetalingId = "124542"

    val vedtak1 =
        VedtakFattetForEksternDto(
            fødselsnummer = fnr,
            aktørId = aktørId,
            organisasjonsnummer = org,
            yrkesaktivitetstype = null,
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

    val vedtak2 =
        VedtakFattetForEksternDto(
            fødselsnummer = fnr,
            aktørId = aktørId,
            organisasjonsnummer = org,
            yrkesaktivitetstype = null,
            fom = now.plusDays(1),
            tom = now.plusDays(5),
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
        lagUtbetaling(
            fødselsnummer = fnr,
            aktørId = aktørId,
            organisasjonsnummer = org,
            fom = now,
            tom = now.plusDays(1),
            utbetalingId = utbetalingId,
            antallVedtak = 2,
            arbeidsgiverOppdrag = lagArbeidsgiverOppdrag(mottaker = org),
        )

    @Test
    @Order(1)
    fun `mottar ett av to vedtak`() {
        kafkaProducer
            .send(
                ProducerRecord(
                    VEDTAK_TOPIC,
                    null,
                    fnr,
                    vedtak1.serialisertTilString(),
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
        hentetVedtak.utbetalingId.shouldBeEqualTo(vedtak1.utbetalingId)
    }

    @Test
    @Order(2)
    fun `finner ikke brukervedtaket da utbetaling ikke er mottatt`() {
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
    @Order(6)
    fun `finner fortsatt ikke brukervedtaket da det siste vedtaket mangler`() {
        hentVedtakMedTokenXToken(fnr).shouldBeEmpty()
    }

    @Test
    @Order(7)
    fun `mottar det andre vedtaket`() {
        kafkaProducer
            .send(
                ProducerRecord(
                    VEDTAK_TOPIC,
                    null,
                    fnr,
                    vedtak2.serialisertTilString(),
                    listOf(RecordHeader("type", "VedtakFattet".toByteArray())),
                ),
            ).get()

        await().atMost(5, TimeUnit.SECONDS).until {
            vedtakRepository.findVedtakDbRecordsByFnr(fnr).size == 2
        }
    }

    @Test
    @Order(8)
    fun `finner brukervedtaket`() {
        val vedtak = hentVedtakMedTokenXToken(fnr)
        vedtak.shouldHaveSize(1)
        vedtak[0].annullert.`should be false`()
        vedtak[0].lest.`should be false`()
        vedtak[0].vedtak.fom `should be equal to` vedtak1.fom
        vedtak[0].vedtak.tom `should be equal to` vedtak2.tom
        vedtak[0].vedtak.utbetaling.utbetalingId `should be equal to` utbetalingId
    }

    @Test
    @Order(9)
    fun `bruker leser vedtaket`() {
        val vedtak = hentVedtakMedTokenXToken(fnr)

        vedtak.shouldHaveSize(1)
        vedtak[0].lest.`should be false`()

        val vedtaksId = vedtak[0].id

        lesVedtakMedTokenXToken(fnr, vedtaksId) `should be equal to` "Leste vedtak $vedtaksId"
        lesVedtakMedTokenXToken(fnr, vedtaksId) `should be equal to` "Vedtak $vedtaksId er allerede lest"

        val utbetalingDbRecord = utbetalingRepository.findUtbetalingDbRecordsByFnr(fnr).first { it.id == vedtaksId }
        utbetalingDbRecord.lest.`should not be null`()
    }

    @Test
    @Order(10)
    fun `tester at henting av vedtak fungerer med gammelt acr claim`() {
        val response = authMedSpesifiktAcrClaim(fnr, "Level4")
        assertThat(response).isEqualTo("200")
    }

    @Test
    @Order(11)
    fun `tester at henting av vedtak fungerer med nytt acr claim`() {
        val response = authMedSpesifiktAcrClaim(fnr, "idporten-loa-high")
        assertThat(response).isEqualTo("200")
    }

    @Test
    @Order(12)
    fun `tester at henting av vedtak ikke fungerer med tilfeldig valgt acr claim`() {
        val response = authMedSpesifiktAcrClaim(fnr, "doNotLetMeIn")
        assertThat(response).isEqualTo("401")
    }
}
