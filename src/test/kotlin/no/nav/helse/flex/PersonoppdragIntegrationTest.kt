package no.nav.helse.flex

import no.nav.helse.flex.domene.VedtakFattetForEksternDto
import no.nav.helse.flex.domene.tilUtbetalingUtbetalt
import no.nav.helse.flex.domene.tilVedtakFattetForEksternDto
import no.nav.helse.flex.kafka.UTBETALING_TOPIC
import no.nav.helse.flex.kafka.VEDTAK_TOPIC
import no.nav.helse.flex.testdata.lagPersonOppdrag
import no.nav.helse.flex.testdata.lagUtbetaling
import no.nav.helse.flex.testdata.lagUtbetalingdag
import no.nav.helse.flex.testdata.lagUtbetalingslinje
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
import java.util.concurrent.TimeUnit

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class PersonoppdragIntegrationTest : FellesTestOppsett() {
    @Autowired
    lateinit var kafkaProducer: KafkaProducer<String, String>

    final val fnr = "1233342"
    final val aktørId = "321"
    final val org = "987123129"
    final val fom = LocalDate.of(2022, 2, 1)
    final val tom = fom.plusDays(7)
    final val utbetalingId = "168465"
    val vedtak =
        VedtakFattetForEksternDto(
            fødselsnummer = fnr,
            aktørId = aktørId,
            organisasjonsnummer = org,
            yrkesaktivitetstype = null,
            fom = fom,
            tom = tom,
            skjæringstidspunkt = fom,
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
            fom = fom,
            tom = tom,
            utbetalingId = utbetalingId,
            antallVedtak = 1,
            forbrukteSykedager = 2,
            gjenståendeSykedager = 3254,
            foreløpigBeregnetSluttPåSykepenger = LocalDate.of(2020, 3, 12),
            personOppdrag =
                lagPersonOppdrag(
                    mottaker = org,
                    nettoBeløp = 738,
                    utbetalingslinjer =
                        listOf(
                            lagUtbetalingslinje(
                                fom = fom,
                                tom = tom,
                                dagsats = 123,
                                totalbeløp = 738,
                                grad = 100.0,
                                stønadsdager = 6,
                            ),
                        ),
                ),
            utbetalingsdager =
                listOf(
                    lagUtbetalingdag(
                        dato = fom,
                        type = "ArbeidsgiverperiodeDag",
                    ),
                    lagUtbetalingdag(
                        dato = fom.plusDays(1),
                        type = "ArbeidsgiverperiodeDag",
                    ),
                    lagUtbetalingdag(
                        dato = fom.plusDays(2),
                        type = "ArbeidsgiverperiodeDag",
                    ),
                    lagUtbetalingdag(
                        dato = fom.plusDays(3),
                        type = "ArbeidsgiverperiodeDag",
                    ),
                    lagUtbetalingdag(
                        dato = fom.plusDays(4),
                        type = "ArbeidsgiverperiodeDag",
                    ),
                    lagUtbetalingdag(
                        dato = fom.plusDays(5),
                        type = "ArbeidsgiverperiodeDag",
                    ),
                    lagUtbetalingdag(
                        dato = fom.plusDays(6),
                        type = "NavDag",
                    ),
                    lagUtbetalingdag(
                        dato = fom.plusDays(7),
                        type = "NavDag",
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
    fun `finner vedtaket`() {
        val vedtak = hentVedtakMedTokenXToken(fnr)
        vedtak.shouldHaveSize(1)
        vedtak[0].annullert.`should be false`()
        vedtak[0].lest.`should be false`()
        vedtak[0].orgnavn `should be equal to` org

        vedtak[0].vedtak.utbetaling.foreløpigBeregnetSluttPåSykepenger `should be equal to` LocalDate.of(2020, 3, 12)
        vedtak[0].vedtak.utbetaling.utbetalingId `should be equal to` utbetalingId
        vedtak[0]
            .vedtak.utbetaling.arbeidsgiverOppdrag
            .shouldBeNull()
        vedtak[0]
            .vedtak.utbetaling.personOppdrag
            .shouldNotBeNull()
        vedtak[0]
            .vedtak.utbetaling.personOppdrag!!
            .utbetalingslinjer
            .shouldHaveSize(1)
    }
}
