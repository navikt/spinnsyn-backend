package no.nav.syfo

import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.application.install
import io.ktor.auth.authenticate
import io.ktor.features.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.jackson
import io.ktor.routing.routing
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.TestApplicationRequest
import io.ktor.server.testing.handleRequest
import io.ktor.util.KtorExperimentalAPI
import io.mockk.* // ktlint-disable no-wildcard-imports
import kotlinx.coroutines.runBlocking
import no.nav.brukernotifikasjon.schemas.Done
import no.nav.brukernotifikasjon.schemas.Oppgave
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.setupAuth
import no.nav.syfo.brukernotifkasjon.BrukernotifikasjonKafkaProducer
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.kafka.toProducerConfig
import no.nav.syfo.testutil.TestDB
import no.nav.syfo.testutil.generateJWT
import no.nav.syfo.testutil.stopApplicationNårKafkaTopicErLest
import no.nav.syfo.vedtak.api.registerVedtakApi
import no.nav.syfo.vedtak.db.finnVedtak
import no.nav.syfo.vedtak.kafka.VedtakConsumer
import no.nav.syfo.vedtak.service.VedtakService
import no.nav.syfo.vedtak.service.tilRSVedtak
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.shouldEqual
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.header.internals.RecordHeader
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.containers.Network
import java.nio.file.Paths
import java.util.* // ktlint-disable no-wildcard-imports

@KtorExperimentalAPI
object VedtakVerdikjedeSpek : Spek({

    val issuer = "TestIssuer"
    val audience = "AUD"

    val brukernotifikasjonKafkaProducer = mockk<BrukernotifikasjonKafkaProducer>()
    val env = mockk<Environment>()

    beforeEachTest {
        clearAllMocks()
        every { env.spinnsynFrontendUrl } returns "https://www.nav.no/syk/sykepenger"
        every { env.serviceuserUsername } returns "srvspvedtak"
        every { env.isProd() } returns false
        every { brukernotifikasjonKafkaProducer.opprettBrukernotifikasjonOppgave(any(), any()) } just Runs
        every { brukernotifikasjonKafkaProducer.sendDonemelding(any(), any()) } just Runs
    }

    describe("Test hele verdikjeden") {
        with(TestApplicationEngine()) {

            val testDb = TestDB()
            val kafka = KafkaContainer().withNetwork(Network.newNetwork())
            kafka.start()

            val kafkaConfig = Properties()
            kafkaConfig.let {
                it["bootstrap.servers"] = kafka.bootstrapServers
                it[ConsumerConfig.GROUP_ID_CONFIG] = "groupId"
                it[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
                it[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
                it[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
            }
            val consumerProperties = kafkaConfig.toConsumerConfig(
                "consumer", valueDeserializer = StringDeserializer::class
            )
            val producerProperties = kafkaConfig.toProducerConfig(
                "producer", valueSerializer = StringSerializer::class
            )

            val vedtakKafkaProducer = KafkaProducer<String, String>(producerProperties)

            val vedtakKafkaConsumer = spyk(KafkaConsumer<String, String>(consumerProperties))
            vedtakKafkaConsumer.subscribe(listOf("aapen-helse-sporbar"))

            val applicationState = ApplicationState()
            applicationState.ready = true
            applicationState.alive = true

            val vedtakConsumer = VedtakConsumer(vedtakKafkaConsumer)
            val vedtakService = VedtakService(
                database = testDb,
                applicationState = applicationState,
                vedtakConsumer = vedtakConsumer,
                brukernotifikasjonKafkaProducer = brukernotifikasjonKafkaProducer,
                environment = env
            )

            val fnr = "13068700000"

            val path = "src/test/resources/jwkset.json"
            val uri = Paths.get(path).toUri().toURL()
            val jwkProvider = JwkProviderBuilder(uri).build()

            start()
            application.setupAuth(jwkProvider = jwkProvider, loginserviceClientId = audience, issuer = issuer)
            application.routing {
                authenticate("jwt") {
                    registerVedtakApi(vedtakService)
                }
            }
            application.install(ContentNegotiation) {
                jackson {
                    registerKotlinModule()
                    registerModule(JavaTimeModule())
                    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                }
            }

            fun TestApplicationRequest.medFnr(subject: String) {
                addHeader(
                    HttpHeaders.Authorization,
                    "Bearer ${generateJWT(audience = audience, issuer = issuer, subject = subject)}"
                )
            }

            it("Vedtak mottas fra kafka topic og lagres i db") {

                val vedtak = testDb.finnVedtak(fnr)
                vedtak.size `should be equal to` 0

                vedtakKafkaProducer.send(
                    ProducerRecord(
                        "aapen-helse-sporbar",
                        null,
                        fnr,
                        "{ \"vedtak\": 123}",
                        listOf(RecordHeader("type", "Vedtak".toByteArray()))
                    )
                )

                vedtakKafkaProducer.send(
                    ProducerRecord(
                        "aapen-helse-sporbar",
                        null,
                        fnr,
                        "{ \"Behandlingstilstand-på-samme-topic-som-ikke-skal-lages\": 123}",
                        listOf(RecordHeader("type", "Behandlingstilstand".toByteArray()))
                    )
                )
                stopApplicationNårKafkaTopicErLest(vedtakKafkaConsumer, applicationState)

                runBlocking {
                    vedtakService.start()
                }

                val vedtakEtter = testDb.finnVedtak(fnr)
                vedtakEtter.size `should be equal to` 1

                val oppgaveSlot = slot<Oppgave>()
                val vedtaksId = vedtakEtter[0].id

                verify(exactly = 1) { brukernotifikasjonKafkaProducer.opprettBrukernotifikasjonOppgave(any(), capture(oppgaveSlot)) }
                oppgaveSlot.captured.getFodselsnummer() shouldEqual fnr
                oppgaveSlot.captured.getGrupperingsId() shouldEqual vedtaksId
                oppgaveSlot.captured.getSikkerhetsnivaa() shouldEqual 4
                oppgaveSlot.captured.getTekst() shouldEqual "NAV har behandlet søknad om sykepenger"
                oppgaveSlot.captured.getLink() shouldEqual "https://www.nav.no/syk/sykepenger/vedtak/$vedtaksId"

                verify(exactly = 0) { brukernotifikasjonKafkaProducer.sendDonemelding(any(), any()) }
            }

            it("Vedtaket kan hentes i REST APIet") {
                val vedtak = testDb.finnVedtak(fnr)[0].tilRSVedtak()
                val generertVedtakId = vedtak.id
                val opprettet = vedtak.opprettet

                with(
                    handleRequest(HttpMethod.Get, "/api/v1/vedtak") {
                        medFnr(fnr)
                    }
                ) {
                    response.status() shouldEqual HttpStatusCode.OK
                    response.content shouldEqual "[{\"id\":\"$generertVedtakId\",\"lest\":false,\"vedtak\":{\"vedtak\":123},\"opprettet\":\"$opprettet\"}]"
                }
            }

            it("Dersom bruker ikke har lagret vedtak får vi et tomt array") {
                with(
                    handleRequest(HttpMethod.Get, "/api/v1/vedtak") {
                        medFnr("12345610102")
                    }
                ) {
                    response.status() shouldEqual HttpStatusCode.OK
                    response.content shouldEqual "[]"
                }
            }

            it("Vedtaket kan hentes med vedtaksid i REST APIet") {
                val vedtak = testDb.finnVedtak(fnr)[0].tilRSVedtak()
                val generertVedtakId = vedtak.id
                val opprettet = vedtak.opprettet

                with(
                    handleRequest(HttpMethod.Get, "/api/v1/vedtak/$generertVedtakId") {
                        medFnr(fnr)
                    }
                ) {
                    response.status() shouldEqual HttpStatusCode.OK
                    response.content shouldEqual "{\"id\":\"$generertVedtakId\",\"lest\":false,\"vedtak\":{\"vedtak\":123},\"opprettet\":\"$opprettet\"}"
                }
            }

            it("Vedtaket skal returnere 404 for en uautorisert person") {
                val generertVedtakId = testDb.finnVedtak(fnr)[0].id

                with(
                    handleRequest(HttpMethod.Get, "/api/v1/vedtak/$generertVedtakId") {
                        medFnr("12345610102")
                    }
                ) {
                    response.status() shouldEqual HttpStatusCode.NotFound
                    response.content shouldEqual "{\"melding\":\"Finner ikke vedtak $generertVedtakId\"}"
                }
            }

            it("Vedtaket kan markeres som lest av autorisert person") {
                val generertVedtakId = testDb.finnVedtak(fnr)[0].id

                with(
                    handleRequest(HttpMethod.Post, "/api/v1/vedtak/$generertVedtakId/les") {
                        medFnr(fnr)
                    }
                ) {
                    response.status() shouldEqual HttpStatusCode.OK
                    response.content shouldEqual "{\"melding\":\"Leste vedtak $generertVedtakId\"}"
                }
                val doneSlot = slot<Done>()

                verify(exactly = 1) { brukernotifikasjonKafkaProducer.sendDonemelding(any(), capture(doneSlot)) }
                doneSlot.captured.getFodselsnummer() shouldEqual fnr
                doneSlot.captured.getGrupperingsId() shouldEqual generertVedtakId
            }

            it("Et allerede lest vedtak skal ikke leses igjen") {
                val generertVedtakId = testDb.finnVedtak(fnr)[0].id

                with(
                    handleRequest(HttpMethod.Post, "/api/v1/vedtak/$generertVedtakId/les") {
                        medFnr(fnr)
                    }
                ) {
                    response.status() shouldEqual HttpStatusCode.OK
                    response.content shouldEqual "{\"melding\":\"Vedtak $generertVedtakId er allerede lest\"}"
                }
                verify(exactly = 0) { brukernotifikasjonKafkaProducer.sendDonemelding(any(), any()) }
            }

            it("Et forsøkt lest vedtak av uautorisert person skal returnere 404") {
                val generertVedtakId = testDb.finnVedtak(fnr)[0].id

                with(
                    handleRequest(HttpMethod.Post, "/api/v1/vedtak/$generertVedtakId/les") {
                        medFnr("12345610102")
                    }
                ) {
                    response.status() shouldEqual HttpStatusCode.NotFound
                    response.content shouldEqual "{\"melding\":\"Finner ikke vedtak $generertVedtakId\"}"
                }
            }
        }
    }
})
