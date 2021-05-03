package no.nav.helse.flex

import no.nav.helse.flex.kafka.SPORBAR_TOPIC
import no.nav.helse.flex.vedtak.db.AnnulleringDAO
import no.nav.helse.flex.vedtak.db.VedtakDAO
import no.nav.helse.flex.vedtak.domene.AnnulleringDto
import no.nav.helse.flex.vedtak.domene.VedtakDto
import org.amshove.kluent.shouldBe
import org.amshove.kluent.shouldHaveSize
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
class AnnulleringVerdikjedeTest : AbstractContainerBaseTest() {

    @Autowired
    lateinit var onpremKafkaProducer: KafkaProducer<String, String>

    @Autowired
    lateinit var vedtakDAO: VedtakDAO

    @Autowired
    lateinit var annulleringDAO: AnnulleringDAO

    val fnr = "983475"
    val fom = LocalDate.now().minusDays(7)
    val tom = LocalDate.now()
    val automatiskBehandletVedtak = VedtakDto(
        fom = fom,
        tom = tom,
        forbrukteSykedager = 1,
        gjenståendeSykedager = 2,
        organisasjonsnummer = "123",
        utbetalinger = listOf(
            VedtakDto.UtbetalingDto(
                mottaker = "123",
                fagområde = "idk",
                totalbeløp = 1400,
                utbetalingslinjer = listOf(
                    VedtakDto.UtbetalingDto.UtbetalingslinjeDto(
                        fom = fom,
                        tom = tom,
                        dagsats = 14,
                        beløp = 1400,
                        grad = 100.0,
                        sykedager = 100
                    )
                )
            )
        ),
        dokumenter = emptyList(),
        automatiskBehandling = true
    )
    val annulleringDto = AnnulleringDto(
        fødselsnummer = fnr,
        orgnummer = "123",
        tidsstempel = LocalDateTime.now(),
        fom = fom,
        tom = tom
    )

    @Test
    @Order(2)
    fun `Et vedtak mottatt fra kafka blir lagret i db`() {
        onpremKafkaProducer.send(
            ProducerRecord(
                SPORBAR_TOPIC,
                null,
                fnr,
                automatiskBehandletVedtak.serialisertTilString(),
                listOf(RecordHeader("type", "Vedtak".toByteArray()))
            )
        ).get()

        await().atMost(5, TimeUnit.SECONDS).until {
            vedtakDAO.finnVedtak(fnr).isNotEmpty()
        }

        oppgaveKafkaConsumer.ventPåRecords(antall = 1)
    }

    @Test
    @Order(3)
    fun `Vedtaket blir funnet i REST APIet`() {
        val vedtakene = hentV1Vedtak(fnr)
        vedtakene shouldHaveSize 1
        vedtakene.first().annullert shouldBe false
    }

    @Test
    @Order(4)
    fun `Ei annullering mottatt fra kafka blir lagret i db`() {
        onpremKafkaProducer.send(
            ProducerRecord(
                SPORBAR_TOPIC,
                null,
                fnr,
                annulleringDto.serialisertTilString(),
                listOf(RecordHeader("type", "Annullering".toByteArray()))
            )
        ).get()

        await().atMost(5, TimeUnit.SECONDS).until {
            annulleringDAO.finnAnnullering(fnr).isNotEmpty()
        }
        oppgaveKafkaConsumer.ventPåRecords(antall = 0)
    }

    @Test
    @Order(5)
    fun `Det annullerte vedtaket blir funnet i REST APIet`() {
        val vedtakene = hentV1Vedtak(fnr)
        vedtakene shouldHaveSize 1
        vedtakene.first().annullert shouldBe true
    }

    @Test
    @Order(6)
    fun `Ei ny annullering mottatt på kafka blir lagret i db`() {
        onpremKafkaProducer.send(
            ProducerRecord(
                SPORBAR_TOPIC,
                null,
                fnr,
                annulleringDto.copy(orgnummer = "456").serialisertTilString(),
                listOf(RecordHeader("type", "Annullering".toByteArray()))
            )
        ).get()

        await().atMost(5, TimeUnit.SECONDS).until {
            annulleringDAO.finnAnnullering(fnr).size == 2
        }
    }

    @Test
    @Order(7)
    fun `Man finner fremdeles kun ett vedtak i REST APIet`() {
        val vedtakene = hentV1Vedtak(fnr)
        vedtakene shouldHaveSize 1
        vedtakene.first().annullert shouldBe true
    }

    @Test
    @Order(8)
    fun `Et nytt vedtak mottatt fra kafka blir lagret i db`() {
        onpremKafkaProducer.send(
            ProducerRecord(
                SPORBAR_TOPIC,
                null,
                fnr,
                automatiskBehandletVedtak.copy(organisasjonsnummer = "456").serialisertTilString(),
                listOf(RecordHeader("type", "Vedtak".toByteArray()))
            )
        ).get()

        await().atMost(5, TimeUnit.SECONDS).until {
            vedtakDAO.finnVedtak(fnr).size == 2
        }
        oppgaveKafkaConsumer.ventPåRecords(antall = 1)
    }

    @Test
    @Order(9)
    fun `Man finner to annullerte vedtak i REST APIet`() {
        val vedtakene = hentV1Vedtak(fnr)
        vedtakene shouldHaveSize 2
        vedtakene.first().annullert shouldBe true
        vedtakene.last().annullert shouldBe true
    }

    @Test
    @Order(10)
    fun `Enda et vedtak mottatt fra kafka blir lagret i db`() {
        onpremKafkaProducer.send(
            ProducerRecord(
                SPORBAR_TOPIC,
                null,
                fnr,
                automatiskBehandletVedtak.copy(
                    fom = LocalDate.now().minusDays(16),
                    tom = LocalDate.now().minusDays(8)
                ).serialisertTilString(),
                listOf(RecordHeader("type", "Vedtak".toByteArray()))
            )
        ).get()

        await().atMost(5, TimeUnit.SECONDS).until {
            vedtakDAO.finnVedtak(fnr).size == 3
        }
        oppgaveKafkaConsumer.ventPåRecords(antall = 1)
    }
}
