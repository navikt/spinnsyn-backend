package no.nav.helse.flex

import no.nav.helse.flex.domene.AnnulleringDto
import no.nav.helse.flex.domene.UtbetalingUtbetalt
import no.nav.helse.flex.domene.VedtakFattetForEksternDto
import no.nav.helse.flex.kafka.SPORBAR_TOPIC
import no.nav.helse.flex.kafka.UTBETALING_TOPIC
import no.nav.helse.flex.kafka.VEDTAK_TOPIC
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
    lateinit var kafkaProducer: KafkaProducer<String, String>

    final val fnr = "983475"
    final val fom = LocalDate.now().minusDays(7)
    final val tom = LocalDate.now()
    final val org = "394783764"
    final val utbetalingId = "124542"
    val vedtak = VedtakFattetForEksternDto(
        fødselsnummer = fnr,
        aktørId = fnr,
        organisasjonsnummer = org,
        fom = fom,
        tom = tom,
        skjæringstidspunkt = fom,
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
        aktørId = fnr,
        organisasjonsnummer = org,
        fom = fom,
        tom = tom,
        utbetalingId = utbetalingId,
        antallVedtak = 1,
        event = "eventet",
        forbrukteSykedager = 42,
        gjenståendeSykedager = 3254,
        foreløpigBeregnetSluttPåSykepenger = null,
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
            UtbetalingUtbetalt.UtbetalingdagDto(
                dato = fom,
                type = "AvvistDag",
                begrunnelser = listOf("MinimumSykdomsgrad")
            )
        )
    )
    val annulleringDto = AnnulleringDto(
        fødselsnummer = fnr,
        orgnummer = org,
        tidsstempel = LocalDateTime.now(),
        fom = fom,
        tom = tom
    )

    @Test
    @Order(2)
    fun `Et vedtak med utbetaling mottatt fra kafka blir lagret i db`() {
        kafkaProducer.send(
            ProducerRecord(
                VEDTAK_TOPIC,
                null,
                fnr,
                vedtak.serialisertTilString()
            )
        ).get()

        kafkaProducer.send(
            ProducerRecord(
                UTBETALING_TOPIC,
                null,
                fnr,
                utbetaling.serialisertTilString()
            )
        ).get()

        await().atMost(5, TimeUnit.SECONDS).until {
            vedtakRepository.findVedtakDbRecordsByFnr(fnr).isNotEmpty()
        }

        oppgaveKafkaConsumer.ventPåRecords(antall = 0)
    }

    @Test
    @Order(3)
    fun `Vedtaket blir funnet i REST APIet`() {
        val vedtakene = hentVedtakMedLoginserviceToken(fnr)
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
        val vedtakene = hentVedtakMedLoginserviceToken(fnr)
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
        val vedtakene = hentVedtakMedLoginserviceToken(fnr)
        vedtakene shouldHaveSize 1
        vedtakene.first().annullert shouldBe true
    }

    @Test
    @Order(8)
    fun `Et nytt vedtak mottatt fra kafka blir lagret i db`() {
        kafkaProducer.send(
            ProducerRecord(
                VEDTAK_TOPIC,
                null,
                fnr,
                vedtak.copy(
                    organisasjonsnummer = "456",
                    utbetalingId = "$utbetalingId nr2"
                ).serialisertTilString()
            )
        ).get()

        kafkaProducer.send(
            ProducerRecord(
                UTBETALING_TOPIC,
                null,
                fnr,
                utbetaling.copy(
                    organisasjonsnummer = "456",
                    utbetalingId = "$utbetalingId nr2",
                    arbeidsgiverOppdrag = utbetaling.arbeidsgiverOppdrag?.copy(
                        mottaker = "456",
                    )
                ).serialisertTilString()
            )
        ).get()

        await().atMost(5, TimeUnit.SECONDS).until {
            vedtakRepository.findVedtakDbRecordsByFnr(fnr).isNotEmpty()
        }

        oppgaveKafkaConsumer.ventPåRecords(antall = 0)
    }

    @Test
    @Order(9)
    fun `Man finner to annullerte vedtak i REST APIet`() {
        val vedtakene = hentVedtakMedLoginserviceToken(fnr)
        vedtakene shouldHaveSize 2
        vedtakene.first().annullert shouldBe true
        vedtakene.last().annullert shouldBe true
    }
}
