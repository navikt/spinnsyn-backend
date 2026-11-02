package no.nav.helse.flex

import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import no.nav.helse.flex.domene.tilUtbetalingUtbetalt
import no.nav.helse.flex.domene.tilVedtakFattetForEksternDto
import no.nav.helse.flex.kafka.UTBETALING_TOPIC
import no.nav.helse.flex.kafka.VEDTAK_TOPIC
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.header.internals.RecordHeader
import org.awaitility.Awaitility
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import java.io.File
import java.time.LocalDate
import java.util.concurrent.TimeUnit

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class UtregningAvRevurdering : FellesTestOppsett() {
    @Autowired
    lateinit var kafkaProducer: KafkaProducer<String, String>

    final val fnr = "14127317470"

    val vedtak1 = File("src/test/resources/vedtakDel1.txt").readText()
    val vedtak2 = File("src/test/resources/vedtakDel2.txt").readText()
    val utbetaling = File("src/test/resources/utbetaling.txt").readText()

    @Test
    @Order(1)
    fun `mottar utbetaling`() {
        kafkaProducer
            .send(
                ProducerRecord(
                    UTBETALING_TOPIC,
                    null,
                    fnr,
                    utbetaling,
                ),
            ).get()

        Awaitility.await().atMost(5, TimeUnit.SECONDS).until {
            utbetalingRepository.findUtbetalingDbRecordsByFnr(fnr).isNotEmpty()
        }

        val dbUtbetaling = utbetalingRepository.findUtbetalingDbRecordsByFnr(fnr).first()
        dbUtbetaling.utbetaling
            .tilUtbetalingUtbetalt()
            .fødselsnummer
            .shouldBe(fnr)
        dbUtbetaling.utbetalingType.shouldBe("REVURDERING")
    }

    @Test
    @Order(2)
    fun `mottar vedtakene til utbetalingen`() {
        kafkaProducer
            .send(
                ProducerRecord(
                    VEDTAK_TOPIC,
                    null,
                    fnr,
                    vedtak1,
                    listOf(RecordHeader("type", "VedtakFattet".toByteArray())),
                ),
            ).get()
        kafkaProducer
            .send(
                ProducerRecord(
                    VEDTAK_TOPIC,
                    null,
                    fnr,
                    vedtak2,
                    listOf(RecordHeader("type", "VedtakFattet".toByteArray())),
                ),
            ).get()

        Awaitility.await().atMost(5, TimeUnit.SECONDS).until {
            vedtakRepository.findVedtakDbRecordsByFnr(fnr).isNotEmpty()
        }

        val hentetVedtak = vedtakRepository.findVedtakDbRecordsByFnr(fnr).first()
        hentetVedtak.vedtak
            .tilVedtakFattetForEksternDto()
            .fødselsnummer
            .shouldBe(fnr)
    }

    @Test
    @Order(3)
    fun `Kan hente revurderingen`() {
        val vedtak = hentVedtakMedTokenXToken(fnr)
        vedtak.shouldHaveSize(1)
        vedtak[0].vedtak.utbetaling.utbetalingType shouldBe "REVURDERING"
        vedtak[0].revurdert.shouldBeFalse()
        vedtak[0].vedtak.fom.shouldBe(LocalDate.of(2021, 7, 1))
        vedtak[0].vedtak.tom.shouldBe(LocalDate.of(2021, 8, 15))
        vedtak[0].andreArbeidsgivere.shouldBeNull()
    }
}
