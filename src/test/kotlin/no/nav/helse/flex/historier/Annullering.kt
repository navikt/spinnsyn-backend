package no.nav.helse.flex.historier

import com.auth0.jwk.JwkProviderBuilder
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.TestApplicationRequest
import io.ktor.server.testing.handleRequest
import io.ktor.util.KtorExperimentalAPI
import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.runBlocking
import no.nav.helse.flex.Environment
import no.nav.helse.flex.application.ApplicationState
import no.nav.helse.flex.application.IssuerInternalId
import no.nav.helse.flex.application.JwtIssuer
import no.nav.helse.flex.application.WellKnown
import no.nav.helse.flex.application.configureApplication
import no.nav.helse.flex.brukernotifkasjon.BrukernotifikasjonKafkaProducer
import no.nav.helse.flex.testutil.TestDB
import no.nav.helse.flex.testutil.generateJWT
import no.nav.helse.flex.testutil.mockSyfotilgangskontrollServer
import no.nav.helse.flex.testutil.stopApplicationNårAntallKafkaMeldingerErLest
import no.nav.helse.flex.tilRSVedtakListe
import no.nav.helse.flex.vedtak.db.finnAnnullering
import no.nav.helse.flex.vedtak.db.finnVedtak
import no.nav.helse.flex.vedtak.domene.AnnulleringDto
import no.nav.helse.flex.vedtak.domene.Dto
import no.nav.helse.flex.vedtak.domene.VedtakDto
import no.nav.helse.flex.vedtak.kafka.VedtakConsumer
import no.nav.helse.flex.vedtak.service.RSVedtak
import no.nav.helse.flex.vedtak.service.SyfoTilgangskontrollService
import no.nav.helse.flex.vedtak.service.VedtakNullstillService
import no.nav.helse.flex.vedtak.service.VedtakService
import no.nav.helse.flex.vedtak.service.tilRSVedtak
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.kafka.toProducerConfig
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
import org.testcontainers.utility.DockerImageName
import java.nio.file.Paths
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Properties

@KtorExperimentalAPI
object Annullering : Spek({

    val selvbetjeningissuer = "TestIssuer"
    val selvbetjeningaudience = "AUD"
    val veilederissuer = "VeilederIssuer"
    val veilederaudience = "veileder"

    val fnr = "13068700000"

    val fom = LocalDate.now().minusDays(7)
    val tom = LocalDate.now()
    val automatiskBehandletVedtak = VedtakDto(
        fom = fom,
        tom = tom,
        forbrukteSykedager = 1,
        gjenståendeSykedager = 2,
        orgnummer = "123",
        utbetalinger = emptyList(),
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

    val brukernotifikasjonKafkaProducer = mockk<BrukernotifikasjonKafkaProducer>()
    val env = mockk<Environment>()

    val applicationState = ApplicationState()

    val mockServerPort = 6060
    val mockHttpServerUrl = "http://localhost:$mockServerPort"

    fun setupEnvMock() {
        clearAllMocks()
        every { env.spinnsynFrontendUrl } returns "https://www.nav.no/syk/sykepenger"
        every { env.serviceuserUsername } returns "srvspvedtak"
        every { env.syfotilgangskontrollApiGwKey } returns "whateverkey"
        every { env.isProd() } returns false
        every { env.apiGatewayUrl } returns mockHttpServerUrl
        every { env.isDev() } returns false
        every { brukernotifikasjonKafkaProducer.opprettBrukernotifikasjonOppgave(any(), any()) } just Runs
        every { brukernotifikasjonKafkaProducer.sendDonemelding(any(), any()) } just Runs
    }

    setupEnvMock()

    beforeEachTest {
        setupEnvMock()
        applicationState.alive = true
        applicationState.ready = true
    }

    describe("Verdikjede med annullering i fokus") {
        with(TestApplicationEngine()) {

            val testDb = TestDB()
            val kafka = KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:5.4.3"))
                .withNetwork(Network.newNetwork())
            kafka.start()

            val kafkaConfig = Properties()
            kafkaConfig.let {
                it[ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG] = kafka.bootstrapServers
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

            val vedtakConsumer = VedtakConsumer(
                vedtakKafkaConsumer,
                listOf("aapen-helse-sporbar")
            )
            val vedtakService = VedtakService(
                database = testDb,
                applicationState = applicationState,
                vedtakConsumer = vedtakConsumer,
                brukernotifikasjonKafkaProducer = brukernotifikasjonKafkaProducer,
                environment = env,
                delayStart = 100L
            )
            val vedtakNullstillService = VedtakNullstillService(
                database = testDb,
                brukernotifikasjonKafkaProducer = brukernotifikasjonKafkaProducer,
                environment = env
            )

            val path = "src/test/resources/jwkset.json"
            val uri = Paths.get(path).toUri().toURL()
            val jwkProvider = JwkProviderBuilder(uri).build()

            val selvbetjeningIssuer = JwtIssuer(
                issuerInternalId = IssuerInternalId.selvbetjening,
                wellKnown = WellKnown(
                    authorization_endpoint = "hatever",
                    token_endpoint = "whatever",
                    jwks_uri = uri.toString(),
                    issuer = selvbetjeningissuer
                ),
                expectedAudience = listOf(selvbetjeningaudience),
                jwkProvider = jwkProvider
            )

            val veilederIssuer = JwtIssuer(
                issuerInternalId = IssuerInternalId.veileder,
                wellKnown = WellKnown(
                    authorization_endpoint = "hatever",
                    token_endpoint = "whatever",
                    jwks_uri = uri.toString(),
                    issuer = veilederissuer
                ),
                expectedAudience = listOf(veilederaudience),
                jwkProvider = jwkProvider
            )

            val tilgangskontrollServer = mockSyfotilgangskontrollServer(mockServerPort, fnr).start(wait = false)

            afterGroup { tilgangskontrollServer.stop(1L, 10L) }

            start()
            application.configureApplication(
                selvbetjeningIssuer = selvbetjeningIssuer,
                veilederIssuer = veilederIssuer,
                applicationState = applicationState,
                vedtakService = vedtakService,
                syfoTilgangskontrollService = SyfoTilgangskontrollService(environment = env),
                env = env,
                vedtakNullstillService = vedtakNullstillService
            )

            fun TestApplicationRequest.medSelvbetjeningToken(subject: String) {
                addHeader(
                    HttpHeaders.Authorization,
                    "Bearer ${generateJWT(audience = selvbetjeningaudience, issuer = selvbetjeningissuer, subject = subject)}"
                )
            }

            fun TestApplicationRequest.medVeilederToken() {
                addHeader(
                    HttpHeaders.Authorization,
                    "Bearer ${generateJWT(audience = veilederaudience, issuer = veilederissuer)}"
                )
            }

            it("Et vedtak mottatt fra kafka blir lagret i db") {
                val vedtakFraDb = testDb.finnVedtak(fnr)
                vedtakFraDb.size `should be equal to` 0

                vedtakKafkaProducer.send(fnr, automatiskBehandletVedtak, "Vedtak")
                stopApplicationNårAntallKafkaMeldingerErLest(vedtakKafkaConsumer, applicationState, antallKafkaMeldinger = 1)

                runBlocking {
                    vedtakService.start()
                }

                val vedtakEtter = testDb.finnVedtak(fnr)
                vedtakEtter.size `should be equal to` 1
            }

            it("Vedtaket blir funnet i REST APIet") {
                val vedtak = testDb.finnVedtak(fnr).map { vedtak -> vedtak.tilRSVedtak() }
                val generertVedtakId = vedtak.map { it.id }
                val opprettet = vedtak.map { it.opprettet }
                with(
                    handleRequest(HttpMethod.Get, "/api/v1/vedtak") {
                        medSelvbetjeningToken(fnr)
                    }
                ) {
                    response.status() shouldEqual HttpStatusCode.OK
                    response.content!!.tilRSVedtakListe() shouldEqual listOf(
                        RSVedtak(id = generertVedtakId[0], lest = false, vedtak = automatiskBehandletVedtak, opprettet = opprettet[0], annullert = false)
                    )
                }
            }

            it("Ei annullering mottatt fra kafka blir lagret i db") {
                val annulleringFraDb = testDb.finnAnnullering(fnr)
                annulleringFraDb.size `should be equal to` 0

                vedtakKafkaProducer.send(fnr, annulleringDto, "Annullering")

                stopApplicationNårAntallKafkaMeldingerErLest(
                    vedtakKafkaConsumer,
                    applicationState,
                    antallKafkaMeldinger = 1
                )

                runBlocking {
                    vedtakService.start()
                }

                val annulleringEtter = testDb.finnAnnullering(fnr)
                annulleringEtter.size shouldEqual 1
            }

            it("Det annullerte vedtaket blir funnet i REST APIet") {
                val vedtak = testDb.finnVedtak(fnr).map { vedtak -> vedtak.tilRSVedtak() }
                val generertVedtakId = vedtak.map { it.id }
                val opprettet = vedtak.map { it.opprettet }
                with(
                    handleRequest(HttpMethod.Get, "/api/v1/vedtak") {
                        medSelvbetjeningToken(fnr)
                    }
                ) {
                    response.status() shouldEqual HttpStatusCode.OK
                    response.content!!.tilRSVedtakListe() shouldEqual listOf(
                        RSVedtak(id = generertVedtakId[0], lest = false, vedtak = automatiskBehandletVedtak, opprettet = opprettet[0], annullert = true)
                    )
                }
            }

            it("Ei ny annullering mottatt på kafka blir lagret i db") {
                val annulleringFraDb = testDb.finnAnnullering(fnr)
                annulleringFraDb.size `should be equal to` 1

                vedtakKafkaProducer.send(fnr, annulleringDto.copy(orgnummer = "456"), "Annullering")

                stopApplicationNårAntallKafkaMeldingerErLest(
                    vedtakKafkaConsumer,
                    applicationState,
                    antallKafkaMeldinger = 1
                )

                runBlocking {
                    vedtakService.start()
                }

                val annulleringEtter = testDb.finnAnnullering(fnr)
                annulleringEtter.size shouldEqual 2
            }

            it("Man finner fremdeles kun ett vedtak i REST APIet") {
                val vedtak = testDb.finnVedtak(fnr).map { vedtak -> vedtak.tilRSVedtak() }
                val generertVedtakId = vedtak.map { it.id }
                val opprettet = vedtak.map { it.opprettet }
                with(
                    handleRequest(HttpMethod.Get, "/api/v1/vedtak") {
                        medSelvbetjeningToken(fnr)
                    }
                ) {
                    response.status() shouldEqual HttpStatusCode.OK
                    response.content!!.tilRSVedtakListe() shouldEqual listOf(
                        RSVedtak(id = generertVedtakId[0], lest = false, vedtak = automatiskBehandletVedtak, opprettet = opprettet[0], annullert = true)
                    )
                }
            }

            it("Et nytt vedtak mottatt fra kafka blir lagret i db") {
                val vedtakFraDb = testDb.finnVedtak(fnr)
                vedtakFraDb.size `should be equal to` 1

                vedtakKafkaProducer.send(fnr, automatiskBehandletVedtak.copy(orgnummer = "456"), "Vedtak")
                stopApplicationNårAntallKafkaMeldingerErLest(vedtakKafkaConsumer, applicationState, antallKafkaMeldinger = 1)

                runBlocking {
                    vedtakService.start()
                }

                val vedtakEtter = testDb.finnVedtak(fnr)
                vedtakEtter.size `should be equal to` 2
            }

            it("Man finner to annullerte vedtak i REST APIet") {
                val vedtak = testDb.finnVedtak(fnr).map { vedtak -> vedtak.tilRSVedtak() }
                val generertVedtakId = vedtak.map { it.id }
                val opprettet = vedtak.map { it.opprettet }
                with(
                    handleRequest(HttpMethod.Get, "/api/v1/vedtak") {
                        medSelvbetjeningToken(fnr)
                    }
                ) {
                    response.status() shouldEqual HttpStatusCode.OK
                    response.content!!.tilRSVedtakListe() shouldEqual listOf(
                        RSVedtak(id = generertVedtakId[0], lest = false, vedtak = automatiskBehandletVedtak, opprettet = opprettet[0], annullert = true),
                        RSVedtak(id = generertVedtakId[1], lest = false, vedtak = automatiskBehandletVedtak.copy(orgnummer = "456"), opprettet = opprettet[1], annullert = true)
                    )
                }
            }
        }
    }
})

fun KafkaProducer<String, String>.send(key: String, value: Dto, type: String) {
    this.send(
        ProducerRecord(
            "aapen-helse-sporbar",
            null,
            key,
            value.serialisertTilString(),
            listOf(RecordHeader("type", type.toByteArray()))
        )
    )
}
