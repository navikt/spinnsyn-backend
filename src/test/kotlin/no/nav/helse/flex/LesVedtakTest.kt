package no.nav.helse.flex

import no.nav.helse.flex.domene.UtbetalingUtbetalt
import no.nav.helse.flex.domene.UtbetalingUtbetalt.UtbetalingdagDto
import no.nav.helse.flex.domene.VedtakFattetForEksternDto
import no.nav.helse.flex.domene.tilUtbetalingUtbetalt
import no.nav.helse.flex.domene.tilVedtakFattetForEksternDto
import no.nav.helse.flex.kafka.UTBETALING_TOPIC
import no.nav.helse.flex.kafka.VEDTAK_TOPIC
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should be false`
import org.amshove.kluent.`should not be null`
import org.amshove.kluent.shouldBeEmpty
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldHaveSize
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.TimeUnit

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class LesVedtakTest : AbstractContainerBaseTest() {

    @Autowired
    lateinit var kafkaProducer: KafkaProducer<String, String>

    @Value("\${on-prem-kafka.username}")
    lateinit var systembruker: String

    final val fnr = "1233342"
    final val aktørId = "321"
    final val org = "987"
    final val now = LocalDate.now()
    final val utbetalingId = "124542"

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
        utbetalingId = utbetalingId,
        grunnlagForSykepengegrunnlag = 0.0,
        grunnlagForSykepengegrunnlagPerArbeidsgiver = mutableMapOf("1234" to 0.0),
        begrensning = "VET_IKKE"
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
        utbetalingId = utbetalingId,
        grunnlagForSykepengegrunnlag = 0.0,
        grunnlagForSykepengegrunnlagPerArbeidsgiver = mutableMapOf("1234" to 0.0),
        begrensning = "VET_IKKE"
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
        foreløpigBeregnetSluttPåSykepenger = null,
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
    fun `mottar ett av to vedtak`() {
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
    fun `finner ikke brukervedtaket da utbetaling ikke er mottatt`() {
        hentVedtakMedLoginserviceToken(fnr).shouldBeEmpty()
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
    @Order(6)
    fun `finner fortsatt ikke brukervedtaket da det siste vedtaket mangler`() {
        hentVedtakMedLoginserviceToken(fnr).shouldBeEmpty()
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
    fun `finner merget brukervedtak`() {
        val vedtak = hentVedtakMedLoginserviceToken(fnr)
        vedtak.shouldHaveSize(1)
        vedtak[0].annullert.`should be false`()
        vedtak[0].lest.`should be false`()
        vedtak[0].vedtak.fom `should be equal to` vedtak1.fom
        vedtak[0].vedtak.tom `should be equal to` vedtak2.tom
        vedtak[0].vedtak.utbetaling.utbetalingId `should be equal to` utbetalingId
    }

    @Test
    @Order(9)
    fun `oppdaterer utbetaling med verdi for feltet varslet_med`() {
        utbetalingRepository.findUtbetalingDbRecordsByFnr(fnr)
            .first()
            .let {
                utbetalingRepository.save(
                    it.copy(
                        brukernotifikasjonSendt = Instant.now(),
                        varsletMed = it.id
                    )
                )
            }
    }

    @Test
    @Order(10)
    fun `bruker leser vedtaket`() {
        val vedtak = hentVedtakMedLoginserviceToken(fnr)

        vedtak.shouldHaveSize(1)
        vedtak[0].lest.`should be false`()

        val vedtaksId = vedtak[0].id

        lesVedtakMedTokenXToken(fnr, vedtaksId) `should be equal to` "Leste vedtak $vedtaksId"
        lesVedtakMedTokenXToken(fnr, vedtaksId) `should be equal to` "Vedtak $vedtaksId er allerede lest"

        val utbetalingDbRecord = utbetalingRepository.findUtbetalingDbRecordsByFnr(fnr).first { it.id == vedtaksId }
        utbetalingDbRecord.lest.`should not be null`()
        utbetalingDbRecord.varsletMed.`should be equal to`(vedtaksId)
    }
}
