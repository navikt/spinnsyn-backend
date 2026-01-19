package no.nav.helse.flex

import no.nav.helse.flex.domene.VedtakFattetForEksternDto
import no.nav.helse.flex.kafka.UTBETALING_TOPIC
import no.nav.helse.flex.kafka.VEDTAK_TOPIC
import no.nav.helse.flex.testdata.lagArbeidsgiverOppdrag
import no.nav.helse.flex.testdata.lagUtbetaling
import org.amshove.kluent.`should be equal to`
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
import java.util.concurrent.TimeUnit

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class AnnulleringVedtakTopicVerdikjedeTest : FellesTestOppsett() {
    @Autowired
    lateinit var kafkaProducer: KafkaProducer<String, String>

    final val fnr = "983475"
    final val fom = LocalDate.now().minusDays(7)
    final val tom = LocalDate.now()
    final val vedtakFattetTidspunk = LocalDate.now()
    final val org = "394783764"
    final val utbetalingId = "124542"

    data class VedtakAnnullertDto(
        val organisasjonsnummer: String?,
        val fødselsnummer: String,
        val fom: LocalDate?,
        val tom: LocalDate?,
    )

    val vedtak =
        VedtakFattetForEksternDto(
            fødselsnummer = fnr,
            aktørId = fnr,
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
            vedtakFattetTidspunkt = vedtakFattetTidspunk,
        )

    val utbetaling =
        lagUtbetaling(
            fødselsnummer = fnr,
            aktørId = fnr,
            organisasjonsnummer = org,
            fom = fom,
            tom = tom,
            utbetalingId = utbetalingId,
            arbeidsgiverOppdrag = lagArbeidsgiverOppdrag(mottaker = org),
        )
    val vedtakAnnullertDto =
        VedtakAnnullertDto(
            fødselsnummer = fnr,
            organisasjonsnummer = org,
            fom = fom,
            tom = tom,
        )

    @Test
    @Order(2)
    fun `Et vedtak med utbetaling mottatt fra kafka blir lagret i db`() {
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
            vedtakRepository.findVedtakDbRecordsByFnr(fnr).isNotEmpty()
        }
    }

    @Test
    @Order(3)
    fun `Vedtaket blir funnet i REST APIet`() {
        val vedtakene = hentVedtakMedTokenXToken(fnr)
        vedtakene shouldHaveSize 1
        vedtakene.first().annullert shouldBe false
    }

    @Test
    @Order(4)
    fun `Ei annullering mottatt fra kafka blir lagret i db`() {
        kafkaProducer
            .send(
                ProducerRecord(
                    VEDTAK_TOPIC,
                    null,
                    fnr,
                    vedtakAnnullertDto.serialisertTilString(),
                    listOf(RecordHeader("type", "VedtakAnnullert".toByteArray())),
                ),
            ).get()

        await().atMost(5, TimeUnit.SECONDS).until {
            annulleringDAO.finnAnnullering(fnr).isNotEmpty()
        }
        annulleringDAO.finnAnnullering(fnr).first().kilde `should be equal to` VEDTAK_TOPIC
    }

    @Test
    @Order(5)
    fun `Det annullerte vedtaket blir funnet i REST APIet`() {
        val vedtakene = hentVedtakMedTokenXToken(fnr)
        vedtakene shouldHaveSize 1
        vedtakene.first().annullert shouldBe true
    }

    @Test
    @Order(6)
    fun `Ei ny annullering mottatt på kafka blir lagret i db`() {
        kafkaProducer
            .send(
                ProducerRecord(
                    VEDTAK_TOPIC,
                    null,
                    fnr,
                    vedtakAnnullertDto.copy(organisasjonsnummer = "456").serialisertTilString(),
                    listOf(RecordHeader("type", "VedtakAnnullert".toByteArray())),
                ),
            ).get()

        await().atMost(5, TimeUnit.SECONDS).until {
            annulleringDAO.finnAnnullering(fnr).size == 2
        }
    }

    @Test
    @Order(7)
    fun `Man finner fremdeles kun ett vedtak i REST APIet`() {
        val vedtakene = hentVedtakMedTokenXToken(fnr)
        vedtakene shouldHaveSize 1
        vedtakene.first().annullert shouldBe true
    }

    @Test
    @Order(8)
    fun `Et nytt vedtak mottatt fra kafka blir lagret i db`() {
        kafkaProducer
            .send(
                ProducerRecord(
                    VEDTAK_TOPIC,
                    null,
                    fnr,
                    vedtak
                        .copy(
                            organisasjonsnummer = "456",
                            utbetalingId = "$utbetalingId nr2",
                        ).serialisertTilString(),
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
                        .copy(
                            organisasjonsnummer = "456",
                            utbetalingId = "$utbetalingId nr2",
                            arbeidsgiverOppdrag =
                                utbetaling.arbeidsgiverOppdrag?.copy(
                                    mottaker = "456",
                                ),
                        ).serialisertTilString(),
                ),
            ).get()

        await().atMost(5, TimeUnit.SECONDS).until {
            vedtakRepository.findVedtakDbRecordsByFnr(fnr).isNotEmpty()
        }
    }

    @Test
    @Order(9)
    fun `Man finner to annullerte vedtak i REST APIet`() {
        val vedtakene = hentVedtakMedTokenXToken(fnr)
        vedtakene shouldHaveSize 2
        vedtakene.first().annullert shouldBe true
        vedtakene.last().annullert shouldBe true
    }
}
