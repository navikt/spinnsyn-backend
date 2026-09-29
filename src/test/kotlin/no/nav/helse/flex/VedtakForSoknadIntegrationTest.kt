package no.nav.helse.flex

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.domene.Dokument
import no.nav.helse.flex.domene.RSVedtakWrapper
import no.nav.helse.flex.domene.UtbetalingUtbetalt
import no.nav.helse.flex.domene.VedtakFattetForEksternDto
import no.nav.helse.flex.kafka.UTBETALING_TOPIC
import no.nav.helse.flex.kafka.VEDTAK_TOPIC
import no.nav.helse.flex.service.BrukerVedtak
import no.nav.helse.flex.testdata.lagArbeidsgiverOppdrag
import no.nav.helse.flex.testdata.lagUtbetaling
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldHaveSize
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.header.internals.RecordHeader
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.TimeUnit

class VedtakForSoknadIntegrationTest : FellesTestOppsett() {
    @Autowired
    lateinit var kafkaProducer: KafkaProducer<String, String>

    @Autowired
    lateinit var vedtakService: BrukerVedtak

    private val fnr = "12345678901"
    private val aktorId = "123"
    private val orgnummer = "999888777"

    @Test
    fun `returnerer to vedtak for samme soknad i tjenestens rekkefolge`() {
        val soknadId = UUID.randomUUID()
        opprettVedtakOgUtbetaling(utbetalingId = "u1", dokumenter = listOf(dokument(soknadId)))
        opprettVedtakOgUtbetaling(
            utbetalingId = "u2",
            dokumenter = listOf(dokument(soknadId), dokument(UUID.randomUUID())),
        )
        opprettVedtakOgUtbetaling(utbetalingId = "u3", dokumenter = listOf(dokument(UUID.randomUUID())))

        val response =
            mockMvc
                .perform(
                    post("/api/v1/flex/vedtak/soknad")
                        .header("Authorization", "Bearer gyldig-flex-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fnr\":\"$fnr\",\"soknadId\":\"$soknadId\"}"),
                ).andExpect(status().isOk)
                .andReturn()
                .response

        val vedtakFraApi: List<RSVedtakWrapper> = objectMapper.readValue(response.contentAsString)
        vedtakFraApi.shouldHaveSize(2)

        val expected =
            vedtakService
                .hentVedtak(fnr, hentSomBruker = false)
                .map { it.copy(id = it.vedtak.utbetaling.utbetalingId ?: it.id) }
                .filter { wrapper ->
                    wrapper.vedtak.dokumenter.any { it.type == Dokument.Type.Søknad && it.dokumentId == soknadId }
                }.map { it.id }

        vedtakFraApi.map { it.id } shouldBeEqualTo expected
    }

    @Test
    fun `returnerer 200 med tom liste ved ingen treff`() {
        val soknadId = UUID.randomUUID()
        opprettVedtakOgUtbetaling(utbetalingId = "u4", dokumenter = listOf(dokument(UUID.randomUUID())))
        val response =
            mockMvc
                .perform(
                    post("/api/v1/flex/vedtak/soknad")
                        .header("Authorization", "Bearer gyldig-flex-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fnr\":\"$fnr\",\"soknadId\":\"$soknadId\"}"),
                ).andExpect(status().isOk)
                .andReturn()
                .response

        val vedtakFraApi: List<RSVedtakWrapper> = objectMapper.readValue(response.contentAsString)
        vedtakFraApi.shouldHaveSize(0)
    }

    @Test
    fun `returnerer 400 for ugyldig fnr`() {
        mockMvc
            .perform(
                post("/api/v1/flex/vedtak/soknad")
                    .header("Authorization", "Bearer gyldig-flex-token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"fnr\":\"123\",\"soknadId\":\"11111111-1111-1111-1111-111111111111\"}"),
            ).andExpect(status().isBadRequest)
    }

    @Test
    fun `returnerer 400 for ugyldig soknadId`() {
        mockMvc
            .perform(
                post("/api/v1/flex/vedtak/soknad")
                    .header("Authorization", "Bearer gyldig-flex-token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"fnr\":\"$fnr\",\"soknadId\":\"ikke-uuid\"}"),
            ).andExpect(status().isBadRequest)
    }

    @Test
    fun `returnerer 401 uten navident`() {
        mockMvc
            .perform(
                post("/api/v1/flex/vedtak/soknad")
                    .header("Authorization", "Bearer gyldig-token-uten-navident")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"fnr\":\"$fnr\",\"soknadId\":\"11111111-1111-1111-1111-111111111111\"}"),
            ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `returnerer 401 uten token`() {
        mockMvc
            .perform(
                post("/api/v1/flex/vedtak/soknad")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"fnr\":\"$fnr\",\"soknadId\":\"11111111-1111-1111-1111-111111111111\"}"),
            ).andExpect(status().isUnauthorized)
    }

    private fun dokument(
        id: UUID,
        type: Dokument.Type = Dokument.Type.Søknad,
    ) = Dokument(dokumentId = id, type = type)

    private fun opprettVedtakOgUtbetaling(
        utbetalingId: String,
        dokumenter: List<Dokument>,
    ) {
        val vedtak =
            VedtakFattetForEksternDto(
                fødselsnummer = fnr,
                aktørId = aktorId,
                organisasjonsnummer = orgnummer,
                yrkesaktivitetstype = null,
                fom = LocalDate.now().minusDays(10),
                tom = LocalDate.now(),
                skjæringstidspunkt = LocalDate.now().minusDays(10),
                dokumenter = dokumenter,
                inntekt = 50000.0,
                sykepengegrunnlag = 600000.0,
                utbetalingId = utbetalingId,
                grunnlagForSykepengegrunnlag = 600000.0,
                grunnlagForSykepengegrunnlagPerArbeidsgiver = mapOf(orgnummer to 600000.0),
                begrensning = "VET_IKKE",
                vedtakFattetTidspunkt = LocalDate.now(),
            )

        val utbetaling: UtbetalingUtbetalt =
            lagUtbetaling(
                fødselsnummer = fnr,
                aktørId = aktorId,
                organisasjonsnummer = orgnummer,
                fom = LocalDate.now().minusDays(10),
                tom = LocalDate.now(),
                utbetalingId = utbetalingId,
                antallVedtak = 1,
                type = "UTBETALING",
                arbeidsgiverOppdrag = lagArbeidsgiverOppdrag(mottaker = orgnummer),
            )

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

        kafkaProducer.send(ProducerRecord(UTBETALING_TOPIC, null, fnr, utbetaling.serialisertTilString())).get()

        await().atMost(5, TimeUnit.SECONDS).until {
            vedtakRepository.findVedtakDbRecordsByFnr(fnr).any { it.utbetalingId == utbetalingId } &&
                utbetalingRepository.findUtbetalingDbRecordsByFnr(fnr).any { it.utbetalingId == utbetalingId }
        }
    }
}
