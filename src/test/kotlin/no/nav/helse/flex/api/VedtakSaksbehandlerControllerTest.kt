package no.nav.helse.flex.api

import com.github.navikt.tbd_libs.populasjonstilgang.api.TilgangSomMangler
import com.github.navikt.tbd_libs.populasjonstilgang.api.TilgangskontrollResultat
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.domene.VedtakFattetForEksternDto
import no.nav.helse.flex.fake.PopulasjonstilgangskontrollFake
import no.nav.helse.flex.kafka.UTBETALING_TOPIC
import no.nav.helse.flex.kafka.VEDTAK_TOPIC
import no.nav.helse.flex.kafka.producer.AUDIT_LOG_TOPIC
import no.nav.helse.flex.kafka.producer.AuditEntry
import no.nav.helse.flex.kafka.producer.AuditLogProducer
import no.nav.helse.flex.kafka.producer.EventType
import no.nav.helse.flex.objectMapper
import no.nav.helse.flex.serialisertTilString
import no.nav.helse.flex.skapSaksbehandlerJwt
import no.nav.helse.flex.testdata.lagArbeidsgiverOppdrag
import no.nav.helse.flex.testdata.lagUtbetaling
import no.nav.helse.flex.ventPåRecords
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.header.internals.RecordHeader
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.module.kotlin.readValue
import java.io.IOException
import java.net.URI
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.TimeUnit

class VedtakSaksbehandlerControllerTest : FellesTestOppsett() {
    @Autowired
    lateinit var kafkaProducer: KafkaProducer<String, String>

    @Autowired
    lateinit var tilgangsmaskin: PopulasjonstilgangskontrollFake

    @Autowired
    lateinit var auditLogProducer: AuditLogProducer

    @Autowired
    lateinit var auditKafkaConsumer: Consumer<String, String>

    private val auditPartisjon = TopicPartition(AUDIT_LOG_TOPIC, 0)

    private val fnr = "40000000001"
    private val orgnummer = "999888777"
    private val utbetalingId = UUID.randomUUID().toString()
    private val annenUtbetalingId = UUID.randomUUID().toString()

    @BeforeAll
    fun opprettDataOgMocks() {
        opprettVedtakOgUtbetaling(utbetalingId, fom = LocalDate.now().minusDays(10), tom = LocalDate.now())
        opprettVedtakOgUtbetaling(annenUtbetalingId, fom = LocalDate.now().minusDays(40), tom = LocalDate.now().minusDays(20))
        settUtbetalingKlarTilVisning()
        opprettAuditTopic()
    }

    @AfterEach
    fun nullstillMocks() {
        tilgangsmaskin.nullstill()
    }

    @Test
    fun `saksbehandler med tilgang får alle vedtakene og oppslaget auditlogges`() {
        startLesingAvAuditlogg()

        val response =
            mockMvc
                .perform(hentVedtak(utbetalingId, skapSaksbehandlerJwt()))
                .andExpect(status().isOk)
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn()
                .response

        val vedtak = objectMapper.readValue<HentVedtakForSaksbehandlerResponse>(response.contentAsString).vedtak
        vedtak shouldHaveSize 2
        vedtak.map { it.id } shouldContainExactlyInAnyOrder listOf(utbetalingId, annenUtbetalingId)
        vedtak.forEach { it.id shouldBe it.vedtak.utbetaling.utbetalingId }
        tilgangsmaskin.kjerneoppslag shouldContainExactly listOf(fnr)

        val audit = lesAuditlogg()
        audit.utførtAv shouldBe "Z999999"
        audit.oppslagPå shouldBe fnr
        audit.forespørselTillatt shouldBe true
        audit.eventType shouldBe EventType.READ
        audit.requestUrl shouldBe URI.create("/api/v1/saksbehandler/vedtak/$utbetalingId")
    }

    @Test
    fun `avslag fra Tilgangsmaskinen gir 403 og auditlogges som avvist`() {
        tilgangsmaskin.resultat = { TilgangskontrollResultat.ManglerTilgang(TilgangSomMangler.EgenAnsatt) }
        startLesingAvAuditlogg()

        mockMvc
            .perform(hentVedtak(utbetalingId, skapSaksbehandlerJwt()))
            .andExpect(status().isForbidden)

        val audit = lesAuditlogg()
        audit.forespørselTillatt shouldBe false
        audit.oppslagPå shouldBe fnr
    }

    @Test
    fun `ident ikke funnet i Tilgangsmaskinen gir 403`() {
        tilgangsmaskin.resultat = { TilgangskontrollResultat.IdentIkkeFunnet }

        mockMvc
            .perform(hentVedtak(utbetalingId, skapSaksbehandlerJwt()))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `uventet svar fra Tilgangsmaskinen gir 503 og ingen data`() {
        tilgangsmaskin.resultat = { TilgangskontrollResultat.UventetFeil("Uventet status fra tilgangsmaskinen: 500") }
        startLesingAvAuditlogg()

        mockMvc
            .perform(hentVedtak(utbetalingId, skapSaksbehandlerJwt()))
            .andExpect(status().isServiceUnavailable)

        auditKafkaConsumer.ventPåRecords(0)
    }

    @Test
    fun `exception fra Tilgangsmaskinen gir 503 og ingen data`() {
        tilgangsmaskin.resultat = { throw IOException("timeout") }
        startLesingAvAuditlogg()

        mockMvc
            .perform(hentVedtak(utbetalingId, skapSaksbehandlerJwt()))
            .andExpect(status().isServiceUnavailable)

        auditKafkaConsumer.ventPåRecords(0)
    }

    @Test
    fun `saksbehandler med Speil lesetilgang får vedtaket etter sjekk i Tilgangsmaskinen`() {
        mockMvc
            .perform(hentVedtak(utbetalingId, skapSaksbehandlerJwt(grupper = listOf("speil-lesetilgang-gruppe"))))
            .andExpect(status().isOk)

        tilgangsmaskin.kjerneoppslag shouldContainExactly listOf(fnr)
    }

    @Test
    fun `saksbehandler med Speil lesetilgang får 403 når Tilgangsmaskinen avslår`() {
        tilgangsmaskin.resultat = { TilgangskontrollResultat.ManglerTilgang(TilgangSomMangler.FortroligAdresse) }

        mockMvc
            .perform(hentVedtak(utbetalingId, skapSaksbehandlerJwt(grupper = listOf("speil-lesetilgang-gruppe"))))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `saksbehandler uten Speil-gruppe får 403 uten kall til Tilgangsmaskinen`() {
        mockMvc
            .perform(hentVedtak(utbetalingId, skapSaksbehandlerJwt(grupper = listOf("annen-gruppe"))))
            .andExpect(status().isForbidden)

        tilgangsmaskin.kjerneoppslag.shouldBeEmpty()
    }

    @Test
    fun `kall uten token gir 401`() {
        mockMvc
            .perform(get("/api/v1/saksbehandler/vedtak/$utbetalingId"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `token uten NAVident gir 401`() {
        mockMvc
            .perform(hentVedtak(utbetalingId, skapSaksbehandlerJwt(navIdent = null)))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `spinnsyn-frontend-interne kan ikke bruke saksbehandler-endepunktet`() {
        val token = skapSaksbehandlerJwt(subject = "spinnsyn-frontend-interne-client-id")

        val body =
            mockMvc
                .perform(hentVedtak(utbetalingId, token))
                .andExpect(status().isForbidden)
                .andReturn()
                .response.contentAsString
        body shouldContain "UKJENT_CLIENT"
    }

    @Test
    fun `spinnsyn-frontend-saksbehandler kan ikke bruke veileder-endepunktet`() {
        val body =
            mockMvc
                .perform(
                    get("/api/v4/veileder/vedtak")
                        .header("Authorization", "Bearer ${skapSaksbehandlerJwt()}")
                        .header("sykmeldt-fnr", fnr),
                ).andExpect(status().isForbidden)
                .andReturn()
                .response.contentAsString
        body shouldContain "UKJENT_CLIENT"
    }

    @Test
    fun `ukjent utbetalingId gir 404`() {
        mockMvc
            .perform(hentVedtak(UUID.randomUUID().toString(), skapSaksbehandlerJwt()))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `utbetalingId som ikke er UUID gir 400`() {
        mockMvc
            .perform(hentVedtak("ikke-en-uuid", skapSaksbehandlerJwt()))
            .andExpect(status().isBadRequest)
    }

    private fun hentVedtak(
        id: String,
        token: String,
    ) = get("/api/v1/saksbehandler/vedtak/$id").header("Authorization", "Bearer $token")

    private fun opprettAuditTopic() {
        auditLogProducer.publiser(
            AuditEntry(
                appNavn = "spinnsyn-backend",
                utførtAv = "Z999999",
                oppslagPå = "oppvarming",
                eventType = EventType.READ,
                forespørselTillatt = true,
                beskrivelse = "Oppretter audit-topic før consumer-posisjonering",
                requestUrl = URI.create("/internal/test"),
                requestMethod = "GET",
            ),
        )
    }

    private fun startLesingAvAuditlogg() {
        auditKafkaConsumer.assign(listOf(auditPartisjon))
        auditKafkaConsumer.seekToEnd(listOf(auditPartisjon))
        auditKafkaConsumer.position(auditPartisjon)
    }

    private fun lesAuditlogg(): AuditEntry = objectMapper.readValue(auditKafkaConsumer.ventPåRecords(1).first().value())

    private fun opprettVedtakOgUtbetaling(
        utbetalingId: String,
        fom: LocalDate,
        tom: LocalDate,
    ) {
        val vedtak =
            VedtakFattetForEksternDto(
                fødselsnummer = fnr,
                aktørId = "4000",
                organisasjonsnummer = orgnummer,
                yrkesaktivitetstype = null,
                fom = fom,
                tom = tom,
                skjæringstidspunkt = fom,
                dokumenter = emptyList(),
                inntekt = 50000.0,
                sykepengegrunnlag = 600000.0,
                utbetalingId = utbetalingId,
                grunnlagForSykepengegrunnlag = 600000.0,
                grunnlagForSykepengegrunnlagPerArbeidsgiver = mapOf(orgnummer to 600000.0),
                begrensning = "VET_IKKE",
                vedtakFattetTidspunkt = tom,
            )
        val utbetaling =
            lagUtbetaling(
                fødselsnummer = fnr,
                aktørId = "4000",
                organisasjonsnummer = orgnummer,
                fom = fom,
                tom = tom,
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
                utbetalingRepository.findByUtbetalingId(utbetalingId) != null
        }
    }
}
