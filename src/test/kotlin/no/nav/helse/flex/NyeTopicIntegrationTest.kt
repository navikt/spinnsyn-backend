package no.nav.helse.flex

import no.nav.helse.flex.domene.*
import no.nav.helse.flex.domene.UtbetalingUtbetalt.UtbetalingdagDto
import no.nav.helse.flex.kafka.SPORBAR_TOPIC
import no.nav.helse.flex.kafka.UTBETALING_TOPIC
import no.nav.helse.flex.kafka.VEDTAK_TOPIC
import no.nav.helse.flex.organisasjon.Organisasjon
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
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.client.RestTemplate
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class NyeTopicIntegrationTest : AbstractContainerBaseTest() {

    @Autowired
    lateinit var kafkaProducer: KafkaProducer<String, String>

    @Autowired
    lateinit var restTemplate: RestTemplate

    @Value("\${on-prem-kafka.username}")
    lateinit var systembruker: String

    final val fnr = "1233342"
    final val aktørId = "321"
    final val org = "987123123"
    final val now = LocalDate.now()
    val utbetalingId = "124542"
    val vedtak = VedtakFattetForEksternDto(
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

    val utbetaling = UtbetalingUtbetalt(
        fødselsnummer = fnr,
        aktørId = aktørId,
        organisasjonsnummer = org,
        fom = now,
        tom = now,
        utbetalingId = utbetalingId,
        antallVedtak = 1,
        event = "eventet",
        forbrukteSykedager = 42,
        gjenståendeSykedager = 3254,
        foreløpigBeregnetSluttPåSykepenger = LocalDate.of(2020, 3, 12),
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

    val annulleringDto = AnnulleringDto(
        fødselsnummer = fnr,
        orgnummer = org,
        tidsstempel = LocalDateTime.now(),
        fom = now,
        tom = now
    )

    @Test
    @Order(1)
    fun `mottar vedtak`() {
        kafkaProducer.send(
            ProducerRecord(
                VEDTAK_TOPIC,
                null,
                fnr,
                vedtak.serialisertTilString()
            )
        ).get()

        await().atMost(5, TimeUnit.SECONDS).until {
            vedtakRepository.findVedtakDbRecordsByFnr(fnr).isNotEmpty()
        }

        val hentetVedtak = vedtakRepository.findVedtakDbRecordsByFnr(fnr).first()
        hentetVedtak.vedtak.tilVedtakFattetForEksternDto().fødselsnummer.shouldBeEqualTo(fnr)
        hentetVedtak.utbetalingId.shouldBeEqualTo(vedtak.utbetalingId)
    }

    @Test
    @Order(2)
    fun `finner ikke vedtaket`() {
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
    @Order(4)
    fun `finner vedtaket i v2 og v3`() {
        val vedtak = hentVedtakMedLoginserviceToken(fnr)
        val vedtakTokenX = hentVedtakMedTokenXToken(fnr)
        vedtak `should be equal to` vedtakTokenX
        vedtak.shouldHaveSize(1)
        vedtak[0].annullert.`should be false`()
        vedtak[0].lest.`should be false`()
        vedtak[0].orgnavn `should be equal to` org
        vedtak[0].vedtak.utbetaling.foreløpigBeregnetSluttPåSykepenger `should be equal to` LocalDate.of(2020, 3, 12)
        vedtak[0].vedtak.utbetaling.utbetalingId `should be equal to` utbetalingId
        vedtak[0].vedtak.utbetaling.utbetalingsdager[0].dato `should be equal to` now
        vedtak[0].vedtak.utbetaling.utbetalingsdager[0].type `should be equal to` "AvvistDag"
        vedtak[0].vedtak.utbetaling.utbetalingsdager[0].begrunnelser[0] `should be equal to` "MinimumSykdomsgrad"

        organisasjonRepository.save(
            Organisasjon(
                navn = "Barneskolen",
                orgnummer = org,
                oppdatert = Instant.now(),
                opprettet = Instant.now(),
                oppdatertAv = "bla"
            )
        )

        val vedtakMedNavn = hentVedtakMedLoginserviceToken(fnr)
        vedtakMedNavn[0].orgnavn `should be equal to` "Barneskolen"
    }

    @Test
    @Order(5)
    fun `En veileder med obo tilgang kan hente vedtaket`() {

        val veilederToken = skapAzureJwt()
        mockSyfoTilgangskontroll(true, fnr)

        val vedtak = hentVedtakSomVeilederObo(fnr, veilederToken)

        vedtak shouldHaveSize 1
        vedtak.first().lest `should be` false
        syfotilgangskontrollMockRestServiceServer?.verify()
        syfotilgangskontrollMockRestServiceServer?.reset()
    }

    @Test
    @Order(5)
    fun `spinnsyn-frontend-arkivering kan hente vedtaket`() {

        val token = skapAzureJwt(subject = "spinnsyn-frontend-arkivering-client-id")

        val vedtak = hentVedtakSomSpinnsynFrontendArkivering(fnr, token)

        vedtak shouldHaveSize 1
        vedtak.first().lest `should be` false
    }

    @Test
    @Order(5)
    fun `Maskin til maskin apiet trenger tokens`() {
        mockMvc.perform(
            get("/api/v1/arkivering/vedtak")
                .header("Authorization", "Bearer blabla-fake-token")
                .header("fnr", fnr)
                .contentType(APPLICATION_JSON)
        ).andExpect(status().isUnauthorized)

        mockMvc.perform(
            get("/api/v1/arkivering/vedtak")
                .header("fnr", fnr)
                .contentType(APPLICATION_JSON)
        ).andExpect(status().isUnauthorized)
    }

    @Test
    @Order(6)
    fun `Oppdaterer utbetaling med varslet-med`() {
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
    @Order(7)
    fun `vi leser vedtaket`() {
        val vedtak = hentVedtakMedLoginserviceToken(fnr)

        vedtak.shouldHaveSize(1)
        vedtak[0].lest.`should be false`()

        val vedtaksId = vedtak[0].id

        lesVedtakMedTokenXToken(fnr, vedtaksId) `should be equal to` "Leste vedtak $vedtaksId"

        lesVedtakMedTokenXToken(fnr, vedtaksId) `should be equal to` "Vedtak $vedtaksId er allerede lest"

        val dones = doneKafkaConsumer.ventPåRecords(antall = 1)
        dones.shouldHaveSize(1)

        val nokkel = dones[0].key()
        nokkel.getEventId() `should be equal to` vedtaksId

        val done = dones[0].value()
        done.getFodselsnummer() `should be equal to` fnr

        utbetalingRepository.findUtbetalingDbRecordsByFnr(fnr).first().lest.`should not be null`()
    }

    @Test
    @Order(8)
    fun `Ei annullering mottatt på kafka blir lagret i db`() {
        kafkaProducer.send(
            ProducerRecord(
                SPORBAR_TOPIC,
                null,
                fnr,
                annulleringDto.serialisertTilString(),
                listOf(RecordHeader("type", "Annullering".toByteArray()))
            )
        ).get()

        await().atMost(5, TimeUnit.SECONDS).until {
            annulleringDAO.finnAnnullering(fnr).size == 1
        }
    }

    @Test
    @Order(9)
    fun `vi finner vedtaket i v2 hvor det nå er annullert`() {
        val vedtak = hentVedtakMedLoginserviceToken(fnr)
        vedtak.shouldHaveSize(1)
        vedtak[0].annullert.`should be true`()
    }

    @Test
    @Order(10)
    fun `mottar vedtak med null utbetaling id`() {

        vedtakRepository.findVedtakDbRecordsByFnr(fnr).shouldHaveSize(1)

        kafkaProducer.send(
            ProducerRecord(
                VEDTAK_TOPIC,
                null,
                fnr,
                vedtak.copy(utbetalingId = null).serialisertTilString()
            )
        ).get()

        await().atMost(5, TimeUnit.SECONDS).until {
            vedtakRepository.findVedtakDbRecordsByFnr(fnr).size == 2
        }
    }

    @Test
    @Order(11)
    fun `mottar enda et vedtak med null utbetaling id`() {
        vedtakRepository.findVedtakDbRecordsByFnr(fnr).shouldHaveSize(2)

        kafkaProducer.send(
            ProducerRecord(
                VEDTAK_TOPIC,
                null,
                fnr,
                vedtak.copy(utbetalingId = null).copy(fom = LocalDate.now().minusDays(5)).serialisertTilString()
            )
        ).get()

        await().atMost(5, TimeUnit.SECONDS).until {
            vedtakRepository.findVedtakDbRecordsByFnr(fnr).size == 3
        }
    }

    @Test
    @Order(12)
    fun `mottar duplikat av det første vedtaket`() {

        vedtakRepository.findVedtakDbRecordsByFnr(fnr).shouldHaveSize(3)

        kafkaProducer.send(
            ProducerRecord(
                VEDTAK_TOPIC,
                null,
                fnr,
                vedtak.serialisertTilString()
            )
        ).get()

        await().during(2, TimeUnit.SECONDS).until {
            vedtakRepository.findVedtakDbRecordsByFnr(fnr).size == 3
        }

        vedtakRepository.findVedtakDbRecordsByFnr(fnr).shouldHaveSize(3)
    }

    @Test
    @Order(13)
    fun `mottar duplikat av den første utbetalingen`() {

        utbetalingRepository.findUtbetalingDbRecordsByFnr(fnr).shouldHaveSize(1)

        kafkaProducer.send(
            ProducerRecord(
                UTBETALING_TOPIC,
                null,
                fnr,
                utbetaling.serialisertTilString()
            )
        ).get()

        await().during(5, TimeUnit.SECONDS).until {
            utbetalingRepository.findUtbetalingDbRecordsByFnr(fnr).size == 1
        }

        utbetalingRepository.findUtbetalingDbRecordsByFnr(fnr).shouldHaveSize(1)
    }
}
