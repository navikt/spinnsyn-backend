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
import no.nav.helse.flex.brukernotifkasjon.BrukernotifikasjonKafkaProdusent
import no.nav.helse.flex.testutil.TestDB
import no.nav.helse.flex.testutil.generateJWT
import no.nav.helse.flex.testutil.mockSyfotilgangskontrollServer
import no.nav.helse.flex.testutil.stopApplicationNårAntallKafkaMeldingerErLest
import no.nav.helse.flex.tilRSVedtakListe
import no.nav.helse.flex.util.skapVedtakKafkaConsumer
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
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.shouldEqual
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.header.internals.RecordHeader
import org.apache.kafka.common.serialization.StringSerializer
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.containers.Network
import org.testcontainers.utility.DockerImageName
import java.nio.file.Paths
import java.time.LocalDate
import java.time.LocalDateTime

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

    val brukernotifikasjonKafkaProducer = mockk<BrukernotifikasjonKafkaProdusent>()
    val env = mockk<Environment>()

    val applicationState = ApplicationState()

    val mockServerPort = 6060
    val mockHttpServerUrl = "http://localhost:$mockServerPort"

    fun setupEnvMock() {
        clearAllMocks()
        every { env.spinnsynFrontendUrl } returns "https://www.nav.no/syk/sykepenger"
        every { env.serviceuserUsername } returns "srvspvedtak"
        every { env.kafkaSecurityProtocol } returns "PLAINTEXT"
        every { env.serviceuserUsername } returns "user"
        every { env.serviceuserPassword } returns "pwd"
        every { env.isProd() } returns false
        every { env.syfotilgangskontrollUrl } returns mockHttpServerUrl
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
            every { env.kafkaAutoOffsetReset } returns "earliest"
            every { env.kafkaBootstrapServers } returns kafka.bootstrapServers

            val vedtakKafkaProducer = KafkaProducer<String, String>(
                mapOf(
                    ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to kafka.bootstrapServers,
                    ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
                    ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java
                )
            )

            val vedtakKafkaConsumer = spyk(skapVedtakKafkaConsumer(env))

            val vedtakConsumer = VedtakConsumer(
                vedtakKafkaConsumer,
                listOf("aapen-helse-sporbar")
            )
            val vedtakService = VedtakService(
                database = testDb,
                applicationState = applicationState,
                vedtakConsumer = vedtakConsumer,
                brukernotifikasjonKafkaProdusent = brukernotifikasjonKafkaProducer,
                environment = env,
                delayStart = 100L
            )
            val vedtakNullstillService = VedtakNullstillService(
                database = testDb,
                brukernotifikasjonKafkaProdusent = brukernotifikasjonKafkaProducer,
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
                    "Bearer ${
                    generateJWT(
                        audience = selvbetjeningaudience,
                        issuer = selvbetjeningissuer,
                        subject = subject
                    )
                    }"
                )
            }

            it("Et vedtak mottatt fra kafka blir lagret i db") {
                val vedtakFraDb = testDb.finnVedtak(fnr)
                vedtakFraDb.size `should be equal to` 0

                vedtakKafkaProducer.send(fnr, automatiskBehandletVedtak, "Vedtak")
                stopApplicationNårAntallKafkaMeldingerErLest(
                    vedtakKafkaConsumer,
                    applicationState,
                    antallKafkaMeldinger = 1
                )

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
                        RSVedtak(
                            id = generertVedtakId[0],
                            lest = false,
                            vedtak = automatiskBehandletVedtak,
                            opprettet = opprettet[0],
                            annullert = false
                        )
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
                        RSVedtak(
                            id = generertVedtakId[0],
                            lest = false,
                            vedtak = automatiskBehandletVedtak,
                            opprettet = opprettet[0],
                            annullert = true
                        )
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
                        RSVedtak(
                            id = generertVedtakId[0],
                            lest = false,
                            vedtak = automatiskBehandletVedtak,
                            opprettet = opprettet[0],
                            annullert = true
                        )
                    )
                }
            }

            it("Et nytt vedtak mottatt fra kafka blir lagret i db") {
                val vedtakFraDb = testDb.finnVedtak(fnr)
                vedtakFraDb.size `should be equal to` 1

                vedtakKafkaProducer.send(fnr, automatiskBehandletVedtak.copy(organisasjonsnummer = "456"), "Vedtak")
                stopApplicationNårAntallKafkaMeldingerErLest(
                    vedtakKafkaConsumer,
                    applicationState,
                    antallKafkaMeldinger = 1
                )

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
                        RSVedtak(
                            id = generertVedtakId[0],
                            lest = false,
                            vedtak = automatiskBehandletVedtak,
                            opprettet = opprettet[0],
                            annullert = true
                        ),
                        RSVedtak(
                            id = generertVedtakId[1],
                            lest = false,
                            vedtak = automatiskBehandletVedtak.copy(organisasjonsnummer = "456"),
                            opprettet = opprettet[1],
                            annullert = true
                        )
                    )
                }
            }

            it("Enda et vedtak mottatt fra kafka blir lagret i db") {
                val vedtakFraDb = testDb.finnVedtak(fnr)
                vedtakFraDb.size `should be equal to` 2

                vedtakKafkaProducer.send(
                    fnr,
                    automatiskBehandletVedtak.copy(
                        fom = LocalDate.now().minusDays(16),
                        tom = LocalDate.now().minusDays(8)
                    ),
                    "Vedtak"
                )
                stopApplicationNårAntallKafkaMeldingerErLest(
                    vedtakKafkaConsumer,
                    applicationState,
                    antallKafkaMeldinger = 1
                )

                runBlocking {
                    vedtakService.start()
                }

                val vedtakEtter = testDb.finnVedtak(fnr)
                vedtakEtter.size `should be equal to` 3
            }

            it("Man finner et vedtak samt to annullerte vedtak i REST APIet") {
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
                        RSVedtak(
                            id = generertVedtakId[0],
                            lest = false,
                            vedtak = automatiskBehandletVedtak,
                            opprettet = opprettet[0],
                            annullert = true
                        ),
                        RSVedtak(
                            id = generertVedtakId[1],
                            lest = false,
                            vedtak = automatiskBehandletVedtak.copy(organisasjonsnummer = "456"),
                            opprettet = opprettet[1],
                            annullert = true
                        ),
                        RSVedtak(
                            id = generertVedtakId[2],
                            lest = false,
                            vedtak = automatiskBehandletVedtak.copy(
                                fom = LocalDate.now().minusDays(16),
                                tom = LocalDate.now().minusDays(8)
                            ),
                            opprettet = opprettet[2],
                            annullert = false
                        )
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
