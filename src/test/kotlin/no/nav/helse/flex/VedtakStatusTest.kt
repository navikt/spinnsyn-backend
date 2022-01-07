package no.nav.helse.flex

import no.nav.helse.flex.domene.UtbetalingUtbetalt
import no.nav.helse.flex.domene.VedtakFattetForEksternDto
import no.nav.helse.flex.domene.VedtakStatus
import no.nav.helse.flex.domene.tilVedtakStatusDto
import no.nav.helse.flex.kafka.UTBETALING_TOPIC
import no.nav.helse.flex.kafka.VEDTAK_STATUS_TOPIC
import no.nav.helse.flex.kafka.VEDTAK_TOPIC
import no.nav.helse.flex.service.VedtakStatusService
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.shouldBeEmpty
import org.amshove.kluent.shouldHaveSize
import org.amshove.kluent.shouldNotBeNull
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.awaitility.Awaitility
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.TimeUnit

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class VedtakStatusTest : AbstractContainerBaseTest() {
    @Autowired
    lateinit var kafkaProducer: KafkaProducer<String, String>

    @Autowired
    lateinit var statusKafkaConsumer: Consumer<String, String>

    @Autowired
    lateinit var vedtakStatusService: VedtakStatusService

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
        gjenståendeSykedager = 254,
        foreløpigBeregnetSluttPåSykepenger = now.plusDays(256),
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
                dato = now,
                type = "AvvistDag",
                begrunnelser = listOf("MinimumSykdomsgrad")
            )
        )
    )

    @BeforeAll
    fun `subscribe til- og tøm status topic`() {
        // Prosesserer vedtak og utbetalinger i fra andre tester
        vedtakStatusService.prosesserUtbetalinger()

        statusKafkaConsumer.subscribeHvisIkkeSubscribed(VEDTAK_STATUS_TOPIC)
        var meldinger = statusKafkaConsumer.hentProduserteRecords()
        while (meldinger.isNotEmpty()) {
            meldinger = statusKafkaConsumer.hentProduserteRecords()
        }
    }

    @AfterAll
    fun `alle meldinger er konsumert fra fra status topic`() {
        statusKafkaConsumer.hentProduserteRecords().shouldBeEmpty()
        vedtakStatusService.prosesserUtbetalinger() `should be equal to` 0
    }

    @Test
    @Order(100)
    fun `mottar vedtak uten at status blir sendt da utbetaling ikke er mottatt`() {
        kafkaProducer.send(
            ProducerRecord(
                VEDTAK_TOPIC,
                null,
                fnr,
                vedtak1.copy(
                    utbetalingId = "VedtakFørst"
                ).serialisertTilString()
            )
        ).get()

        Awaitility.await().atMost(5, TimeUnit.SECONDS).until {
            vedtakRepository.findVedtakDbRecordsByFnr(fnr).any { it.utbetalingId == "VedtakFørst" }
        }

        vedtakStatusService.prosesserUtbetalinger() `should be equal to` 0
    }

    @Test
    @Order(101)
    fun `mottar utbetaling`() {
        kafkaProducer.send(
            ProducerRecord(
                UTBETALING_TOPIC,
                null,
                fnr,
                utbetaling.copy(
                    antallVedtak = 1,
                    utbetalingId = "VedtakFørst"
                ).serialisertTilString()
            )
        ).get()

        Awaitility.await().atMost(5, TimeUnit.SECONDS).until {
            utbetalingRepository.findUtbetalingDbRecordsByFnr(fnr)
                .firstOrNull {
                    it.utbetalingId == "VedtakFørst"
                } != null
        }
    }

    @Test
    @Order(102)
    fun `sender statusmelding på Kafka etter at utbetaling er mottatt`() {
        vedtakStatusService.prosesserUtbetalinger() `should be equal to` 1

        val kafkameldinger = statusKafkaConsumer.ventPåRecords(1)
        kafkameldinger.shouldHaveSize(1)

        val utbetalingDbRecord = utbetalingRepository.findUtbetalingDbRecordsByFnr(fnr).first {
            it.utbetalingId == "VedtakFørst"
        }
        utbetalingDbRecord.shouldNotBeNull()

        val crStatus = kafkameldinger.first()
        crStatus.key() `should be equal to` utbetalingDbRecord.id

        val statudDto = crStatus.value().tilVedtakStatusDto()
        statudDto.id `should be equal to` utbetalingDbRecord.id
        statudDto.fnr `should be equal to` utbetalingDbRecord.fnr
        statudDto.vedtakStatus `should be equal to` VedtakStatus.MOTATT
    }

    @Test
    @Order(103)
    fun `sender melding om at bruker har lest vedtaket på Kafka`() {
        val vedtaket = hentFrontendVedtak("VedtakFørst")
        val vedtaksId = vedtaket.id
        vedtaket.lest `should be equal to` false

        lesVedtakMedTokenXToken(fnr, vedtaksId) `should be equal to` "Leste vedtak $vedtaksId"

        val crStatus = statusKafkaConsumer.ventPåRecords(1).first()
        crStatus.key() `should be equal to` vedtaksId

        val statudDto = crStatus.value().tilVedtakStatusDto()
        statudDto.id `should be equal to` vedtaksId
        statudDto.fnr `should be equal to` fnr
        statudDto.vedtakStatus `should be equal to` VedtakStatus.LEST
    }

    @Test
    @Order(104)
    fun `ny melding sender ikke selv om bruker leser vedtaket på nytt`() {
        val vedtaket = hentFrontendVedtak("VedtakFørst")
        val vedtaksId = vedtaket.id
        vedtaket.lest `should be equal to` true

        val utbetalingFørLesing = utbetalingRepository.findUtbetalingDbRecordsByFnr(fnr).first {
            it.utbetalingId == "VedtakFørst"
        }

        lesVedtakMedTokenXToken(fnr, vedtaksId) `should be equal to` "Vedtak $vedtaksId er allerede lest"
        statusKafkaConsumer.ventPåRecords(0).shouldBeEmpty()

        val etter = utbetalingRepository.findUtbetalingDbRecordsByFnr(fnr).first {
            it.utbetalingId == "VedtakFørst"
        }
        utbetalingFørLesing.lest `should be equal to` etter.lest
    }

    @Test
    @Order(200)
    fun `mottar utbetaling uten at status blir sendt da vedtaket ikke er mottatt`() {
        kafkaProducer.send(
            ProducerRecord(
                UTBETALING_TOPIC,
                null,
                fnr,
                utbetaling.copy(
                    antallVedtak = 1,
                    utbetalingId = "UtbetalingFørst"
                ).serialisertTilString()
            )
        ).get()

        Awaitility.await().atMost(5, TimeUnit.SECONDS).until {
            utbetalingRepository.findUtbetalingDbRecordsByFnr(fnr).firstOrNull {
                it.utbetalingId == "UtbetalingFørst"
            } != null
        }

        vedtakStatusService.prosesserUtbetalinger() `should be equal to` 0
    }

    @Test
    @Order(201)
    fun `mottar vedtak`() {
        kafkaProducer.send(
            ProducerRecord(
                VEDTAK_TOPIC,
                null,
                fnr,
                vedtak1.copy(
                    utbetalingId = "UtbetalingFørst"
                ).serialisertTilString()
            )
        ).get()

        Awaitility.await().atMost(5, TimeUnit.SECONDS).until {
            vedtakRepository.findVedtakDbRecordsByFnr(fnr).any { it.utbetalingId == "UtbetalingFørst" }
        }
    }

    @Test
    @Order(202)
    fun `sender statusmelding på Kafka etter at vedtaket er mottatt`() {
        vedtakStatusService.prosesserUtbetalinger() `should be equal to` 1

        val kafkameldinger = statusKafkaConsumer.ventPåRecords(1)
        kafkameldinger.shouldHaveSize(1)

        val utbetalingDbRecord = utbetalingRepository.findUtbetalingDbRecordsByFnr(fnr).first {
            it.utbetalingId == "UtbetalingFørst"
        }
        utbetalingDbRecord.shouldNotBeNull()

        val crStatus = kafkameldinger.first()
        crStatus.key() `should be equal to` utbetalingDbRecord.id

        val statudDto = crStatus.value().tilVedtakStatusDto()
        statudDto.id `should be equal to` utbetalingDbRecord.id
        statudDto.fnr `should be equal to` utbetalingDbRecord.fnr
        statudDto.vedtakStatus `should be equal to` VedtakStatus.MOTATT
    }

    @Test
    @Order(203)
    fun `oppdaterer utbetaling med verdi for feltet varslet_med`() {
        utbetalingRepository.findUtbetalingDbRecordsByFnr(fnr)
            .first { it.utbetalingId == "UtbetalingFørst" }
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
    @Order(204)
    fun `bruker leser vedtaket`() {
        val vedtaket = hentFrontendVedtak("UtbetalingFørst")
        val vedtaksId = vedtaket.id
        vedtaket.lest `should be equal to` false

        lesVedtakMedTokenXToken(fnr, vedtaksId) `should be equal to` "Leste vedtak $vedtaksId"

        val crStatus = statusKafkaConsumer.ventPåRecords(1).first()
        crStatus.key() `should be equal to` vedtaksId

        val statudDto = crStatus.value().tilVedtakStatusDto()
        statudDto.id `should be equal to` vedtaksId
        statudDto.fnr `should be equal to` fnr
        statudDto.vedtakStatus `should be equal to` VedtakStatus.LEST

        doneKafkaConsumer.ventPåRecords(antall = 1)
    }

    @Test
    @Order(300)
    fun `mottar ett av to vedtak uten at status blir sendt`() {
        kafkaProducer.send(
            ProducerRecord(
                VEDTAK_TOPIC,
                null,
                fnr,
                vedtak1.copy(
                    utbetalingId = "EnAvTo"
                ).serialisertTilString()
            )
        ).get()

        Awaitility.await().atMost(5, TimeUnit.SECONDS).until {
            vedtakRepository.findVedtakDbRecordsByFnr(fnr).any { it.utbetalingId == "EnAvTo" }
        }

        vedtakStatusService.prosesserUtbetalinger() `should be equal to` 0
    }

    @Test
    @Order(301)
    fun `mottar utbetaling men mangler fortsatt ett vedtak så ingen status sendes`() {
        kafkaProducer.send(
            ProducerRecord(
                UTBETALING_TOPIC,
                null,
                fnr,
                utbetaling.copy(
                    utbetalingId = "EnAvTo",
                    antallVedtak = 2
                ).serialisertTilString()
            )
        ).get()

        Awaitility.await().atMost(5, TimeUnit.SECONDS).until {
            utbetalingRepository.findUtbetalingDbRecordsByFnr(fnr).firstOrNull {
                it.utbetalingId == "EnAvTo"
            } != null
        }

        vedtakStatusService.prosesserUtbetalinger() `should be equal to` 0
    }

    @Test
    @Order(302)
    fun `mottar det siste vedtaket`() {
        kafkaProducer.send(
            ProducerRecord(
                VEDTAK_TOPIC,
                null,
                fnr,
                vedtak2.copy(
                    utbetalingId = "EnAvTo"
                ).serialisertTilString()
            )
        ).get()

        Awaitility.await().atMost(5, TimeUnit.SECONDS).until {
            vedtakRepository.findVedtakDbRecordsByFnr(fnr)
                .filter { it.utbetalingId == "EnAvTo" }
                .size == 2
        }
    }

    @Test
    @Order(303)
    fun `prosesserer utbetalinger da alle vedtak er mottatt`() {
        vedtakStatusService.prosesserUtbetalinger() `should be equal to` 1

        val kafkameldinger = statusKafkaConsumer.ventPåRecords(1)
        kafkameldinger.shouldHaveSize(1)

        val utbetalingDbRecord = utbetalingRepository.findUtbetalingDbRecordsByFnr(fnr).first {
            it.utbetalingId == "EnAvTo"
        }
        utbetalingDbRecord.shouldNotBeNull()
        utbetalingDbRecord.antallVedtak `should be equal to` 2

        val crStatus = kafkameldinger.first()
        crStatus.key() `should be equal to` utbetalingDbRecord.id

        val statusDto = crStatus.value().tilVedtakStatusDto()
        statusDto.id `should be equal to` utbetalingDbRecord.id
        statusDto.fnr `should be equal to` utbetalingDbRecord.fnr
        statusDto.vedtakStatus `should be equal to` VedtakStatus.MOTATT
    }

    @Test
    @Order(400)
    fun `vedtakWrapper der det er ingen dager hvor NAV har vært innvolvert`() {
        kafkaProducer.send(
            ProducerRecord(
                UTBETALING_TOPIC,
                null,
                fnr,
                utbetaling.copy(
                    utbetalingId = "IngenAndreDager",
                    antallVedtak = 1,
                    utbetalingsdager = listOf(
                        UtbetalingUtbetalt.UtbetalingdagDto(
                            dato = now,
                            type = "NavHelgDag",
                            begrunnelser = emptyList(),
                        )
                    )
                ).serialisertTilString()
            )
        ).get()

        kafkaProducer.send(
            ProducerRecord(
                VEDTAK_TOPIC,
                null,
                fnr,
                vedtak1.copy(
                    utbetalingId = "IngenAndreDager"
                ).serialisertTilString()
            )
        ).get()

        Awaitility.await().atMost(5, TimeUnit.SECONDS).until {
            vedtakStatusService.prosesserUtbetalinger() == 1
        }

        statusKafkaConsumer.ventPåRecords(1)

        val utbetalingDbRecord = utbetalingRepository.findUtbetalingDbRecordsByFnr(fnr).first {
            it.utbetalingId == "IngenAndreDager"
        }
        utbetalingDbRecord.motattPublisert.shouldNotBeNull() // TODO: null
        utbetalingDbRecord.skalVisesTilBruker `should be equal to` null // TODO: false
    }

    private fun hentFrontendVedtak(utbetalingId: String) =
        hentVedtakMedLoginserviceToken(fnr)
            .filter { it.vedtak.utbetaling.utbetalingId == utbetalingId }
            .shouldHaveSize(1)
            .first()
}
