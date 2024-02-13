package no.nav.helse.flex

import no.nav.helse.flex.domene.tilUtbetalingUtbetalt
import no.nav.helse.flex.domene.tilVedtakFattetForEksternDto
import no.nav.helse.flex.kafka.UTBETALING_TOPIC
import no.nav.helse.flex.kafka.VEDTAK_TOPIC
import org.amshove.kluent.`should be equal to`
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
import java.io.File
import java.time.LocalDate
import java.util.concurrent.TimeUnit

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class DelvisRefusjonOgAvvisteDagerTest : AbstractContainerBaseTest() {
    @Autowired
    lateinit var kafkaProducer: KafkaProducer<String, String>

    final val fnr = "04868197728"

    val vedtak = File("src/test/resources/vedtakMedDelvisRefusjonOgAvvisteDager.txt").readText()
    val utbetaling = File("src/test/resources/utbetalingMedDelvisRefusjonOgAvvisteDager.txt").readText()

    @Test
    @Order(1)
    fun `mottar utbetaling`() {
        kafkaProducer.send(
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
        dbUtbetaling.utbetaling.tilUtbetalingUtbetalt().fødselsnummer.shouldBeEqualTo(fnr)
        dbUtbetaling.utbetalingType.shouldBeEqualTo("UTBETALING")
    }

    @Test
    @Order(2)
    fun `mottar vedtakene til utbetalingen`() {
        kafkaProducer.send(
            ProducerRecord(
                VEDTAK_TOPIC,
                null,
                fnr,
                vedtak,
                listOf(RecordHeader("type", "VedtakFattet".toByteArray())),
            ),
        ).get()

        Awaitility.await().atMost(5, TimeUnit.SECONDS).until {
            vedtakRepository.findVedtakDbRecordsByFnr(fnr).isNotEmpty()
        }

        val hentetVedtak = vedtakRepository.findVedtakDbRecordsByFnr(fnr).first()
        hentetVedtak.vedtak.tilVedtakFattetForEksternDto().fødselsnummer.shouldBeEqualTo(fnr)
    }

    @Test
    @Order(3)
    fun `Arbeidsgiver og persondager avkortes riktig`() {
        val vedtak = hentVedtakMedTokenXToken(fnr)
        vedtak.shouldHaveSize(1)
        vedtak[0].vedtak.utbetaling.utbetalingType `should be equal to` "UTBETALING"
        vedtak[0].vedtak.fom.shouldBeEqualTo(LocalDate.of(2023, 2, 1))
        vedtak[0].vedtak.tom.shouldBeEqualTo(LocalDate.of(2023, 2, 28))

        vedtak[0].sykepengebelopPerson.shouldBeEqualTo(17311)
        vedtak[0].dagerPerson.shouldHaveSize(28)

        vedtak[0].sykepengebelopArbeidsgiver.shouldBeEqualTo(4844)
        vedtak[0].dagerArbeidsgiver.shouldHaveSize(9)
        vedtak[0].dagerArbeidsgiver[0].dagtype.shouldBeEqualTo("NavDagSyk")
        vedtak[0].dagerArbeidsgiver[0].belop.shouldBeEqualTo(692)
        vedtak[0].dagerArbeidsgiver[3].dagtype.shouldBeEqualTo("NavHelgDag")
        vedtak[0].dagerArbeidsgiver[3].belop.shouldBeEqualTo(0)
        vedtak[0].dagerArbeidsgiver[8].dagtype.shouldBeEqualTo("NavDagSyk")
        vedtak[0].dagerArbeidsgiver[8].belop.shouldBeEqualTo(692)
    }
}
