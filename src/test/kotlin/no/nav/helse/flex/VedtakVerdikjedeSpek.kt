package no.nav.helse.flex

import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.module.kotlin.readValue
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
import io.mockk.slot
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.nav.brukernotifikasjon.schemas.Done
import no.nav.brukernotifikasjon.schemas.Oppgave
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
import no.nav.helse.flex.testutil.stopApplicationNårAntallKafkaPollErGjort
import no.nav.helse.flex.util.skapVedtakKafkaConsumer
import no.nav.helse.flex.vedtak.db.finnAnnullering
import no.nav.helse.flex.vedtak.db.finnVedtak
import no.nav.helse.flex.vedtak.domene.AnnulleringDto
import no.nav.helse.flex.vedtak.domene.VedtakDto
import no.nav.helse.flex.vedtak.kafka.VedtakConsumer
import no.nav.helse.flex.vedtak.service.RSVedtak
import no.nav.helse.flex.vedtak.service.SyfoTilgangskontrollService
import no.nav.helse.flex.vedtak.service.VedtakNullstillService
import no.nav.helse.flex.vedtak.service.VedtakService
import no.nav.helse.flex.vedtak.service.forVedtak
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
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

@KtorExperimentalAPI
object VedtakVerdikjedeSpek : Spek({

    val selvbetjeningissuer = "TestIssuer"
    val selvbetjeningaudience = "AUD"
    val veilederissuer = "VeilederIssuer"
    val veilederaudience = "veileder"

    val fnr = "13068700000"
    val automatiskBehandletVedtak = VedtakDto(
        fom = LocalDate.now(),
        tom = LocalDate.now(),
        forbrukteSykedager = 1,
        gjenståendeSykedager = 2,
        utbetalinger = emptyList(),
        dokumenter = emptyList(),
        automatiskBehandling = true
    )
    val manueltVedtak = automatiskBehandletVedtak.copy(automatiskBehandling = false)
    val annulleringDto = AnnulleringDto(
        fødselsnummer = fnr,
        orgnummer = "123",
        tidsstempel = LocalDateTime.now(),
        fom = LocalDate.now().minusDays(10),
        tom = LocalDate.now()
    )

    val brukernotifikasjonKafkaProducer = mockk<BrukernotifikasjonKafkaProdusent>()
    val env = mockk<Environment>()

    val applicationState = ApplicationState()

    val mockServerPort = 9090
    val mockHttpServerUrl = "http://localhost:$mockServerPort"

    fun setupEnvMock() {
        clearAllMocks()
        every { env.spinnsynFrontendUrl } returns "https://www.nav.no/syk/sykepenger"
        every { env.serviceuserUsername } returns "srvspvedtak"
        every { env.syfotilgangskontrollApiGwKey } returns "whateverkey"
        every { env.kafkaSecurityProtocol } returns "PLAINTEXT"
        every { env.serviceuserUsername } returns "user"
        every { env.serviceuserPassword } returns "pwd"
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

    describe("Test hele verdikjeden") {
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

            fun TestApplicationRequest.medSelvbetjeningToken(subject: String, level: String = "Level4") {
                addHeader(
                    HttpHeaders.Authorization,
                    "Bearer ${
                    generateJWT(
                        audience = selvbetjeningaudience,
                        issuer = selvbetjeningissuer,
                        subject = subject,
                        level = level
                    )
                    }"
                )
            }

            fun TestApplicationRequest.medVeilederToken() {
                addHeader(
                    HttpHeaders.Authorization,
                    "Bearer ${generateJWT(audience = veilederaudience, issuer = veilederissuer, level = null)}"
                )
            }

            it("Vedtak mottas fra kafka topic og lagres i db") {

                val vedtakFraDb = testDb.finnVedtak(fnr)
                vedtakFraDb.size `should be equal to` 0

                vedtakKafkaProducer.send(
                    ProducerRecord(
                        "aapen-helse-sporbar",
                        null,
                        fnr,
                        automatiskBehandletVedtak.serialisertTilString(),
                        listOf(RecordHeader("type", "Vedtak".toByteArray()))
                    )
                )

                vedtakKafkaProducer.send(
                    ProducerRecord(
                        "aapen-helse-sporbar",
                        null,
                        fnr,
                        manueltVedtak.serialisertTilString(),
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
                stopApplicationNårAntallKafkaMeldingerErLest(
                    vedtakKafkaConsumer,
                    applicationState,
                    antallKafkaMeldinger = 3
                )

                runBlocking {
                    vedtakService.start()
                }

                val vedtakEtter = testDb.finnVedtak(fnr)
                vedtakEtter.size `should be equal to` 2 // Vi lagrer både manuelle og automatiske

                val automatiskVedtakFraDb = vedtakEtter.find { it.vedtak.automatiskBehandling }!!
                automatiskVedtakFraDb.lest shouldEqual false

                val manueltVedtakFraDb = vedtakEtter.find { !it.vedtak.automatiskBehandling }!!
                manueltVedtakFraDb.lest shouldEqual false

                val oppgaveSlot = slot<Oppgave>()
                val vedtaksId = vedtakEtter[1].id

                verify(exactly = 2) {
                    brukernotifikasjonKafkaProducer.opprettBrukernotifikasjonOppgave(
                        any(),
                        capture(oppgaveSlot)
                    )
                }
                oppgaveSlot.captured.getFodselsnummer() shouldEqual fnr
                oppgaveSlot.captured.getGrupperingsId() shouldEqual vedtaksId
                oppgaveSlot.captured.getSikkerhetsnivaa() shouldEqual 4
                oppgaveSlot.captured.getTekst() shouldEqual "Sykepengene dine er beregnet - se resultatet"
                oppgaveSlot.captured.getLink() shouldEqual "https://www.nav.no/syk/sykepenger/vedtak/$vedtaksId"

                verify(exactly = 0) { brukernotifikasjonKafkaProducer.sendDonemelding(any(), any()) }
            }

            it("Vedtaket kan hentes i REST APIet") {
                val annulleringer = testDb.finnAnnullering(fnr)
                val vedtak = testDb.finnVedtak(fnr).map { vedtak ->
                    vedtak.tilRSVedtak(annulleringer.forVedtak(vedtak))
                }
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
                            opprettet = opprettet[0]
                        ),
                        RSVedtak(
                            id = generertVedtakId[1],
                            lest = false,
                            vedtak = manueltVedtak,
                            opprettet = opprettet[1]
                        )
                    )
                }
            }

            it("REST APIet for borger krever nivå 4") {
                with(
                    handleRequest(HttpMethod.Get, "/api/v1/vedtak") {
                        medSelvbetjeningToken(fnr, level = "Level3")
                    }
                ) {
                    response.status() shouldEqual HttpStatusCode.Unauthorized
                }
            }

            it("Vedtaket kan hentes i REST APIet av en veileder") {
                val vedtak = testDb.finnVedtak(fnr).map { it.tilRSVedtak() }
                val generertVedtakId = vedtak.map { it.id }
                val opprettet = vedtak.map { it.opprettet }

                with(
                    handleRequest(HttpMethod.Get, "/api/v1/veileder/vedtak?fnr=$fnr") {
                        medVeilederToken()
                    }
                ) {
                    response.status() shouldEqual HttpStatusCode.OK
                    response.content!!.tilRSVedtakListe() shouldEqual listOf(
                        RSVedtak(
                            id = generertVedtakId[0],
                            lest = false,
                            vedtak = automatiskBehandletVedtak,
                            opprettet = opprettet[0]
                        ),
                        RSVedtak(
                            id = generertVedtakId[1],
                            lest = false,
                            vedtak = manueltVedtak,
                            opprettet = opprettet[1]
                        )
                    )
                }
            }

            it("veileder får 403 ved henting av vedtak som ikke er registrert for hen") {

                with(
                    handleRequest(HttpMethod.Get, "/api/v1/veileder/vedtak?fnr=123") {
                        medVeilederToken()
                    }
                ) {
                    response.status() shouldEqual HttpStatusCode.Forbidden
                    response.content shouldEqual "{\"melding\":\"Veileder har ikke tilgang til dennne personen\"}"
                }
            }

            it("Veileder APIet krever query param") {
                with(
                    handleRequest(HttpMethod.Get, "/api/v1/veileder/vedtak") {
                        medVeilederToken()
                    }
                ) {
                    response.status() shouldEqual HttpStatusCode.BadRequest
                    response.content shouldEqual "{\"melding\":\"Mangler fnr query param\"}"
                }
            }

            it("Veileder APIet krever token") {
                with(
                    handleRequest(HttpMethod.Get, "/api/v1/veileder/vedtak")
                ) {
                    response.status() shouldEqual HttpStatusCode.Unauthorized
                }
            }

            it("Dersom bruker ikke har lagret vedtak får vi et tomt array") {
                with(
                    handleRequest(HttpMethod.Get, "/api/v1/vedtak") {
                        medSelvbetjeningToken("12345610102")
                    }
                ) {
                    response.status() shouldEqual HttpStatusCode.OK
                    response.content shouldEqual "[]"
                }
            }

            it("Vedtaket kan hentes med vedtaksid i REST APIet") {
                val dbVedtak = testDb.finnVedtak(fnr)[0]
                val vedtak = dbVedtak.tilRSVedtak()
                val generertVedtakId = vedtak.id
                val opprettet = vedtak.opprettet

                with(
                    handleRequest(HttpMethod.Get, "/api/v1/vedtak/$generertVedtakId") {
                        medSelvbetjeningToken(fnr)
                    }
                ) {
                    response.status() shouldEqual HttpStatusCode.OK
                    response.content!!.tilRSVedtak() shouldEqual RSVedtak(
                        id = generertVedtakId,
                        lest = false,
                        vedtak = automatiskBehandletVedtak,
                        opprettet = opprettet
                    )
                }
            }

            it("Vedtaket skal returnere 404 for en uautorisert person") {
                val generertVedtakId = testDb.finnVedtak(fnr)[0].id

                with(
                    handleRequest(HttpMethod.Get, "/api/v1/vedtak/$generertVedtakId") {
                        medSelvbetjeningToken("12345610102")
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
                        medSelvbetjeningToken(fnr)
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
                        medSelvbetjeningToken(fnr)
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
                        medSelvbetjeningToken("12345610102")
                    }
                ) {
                    response.status() shouldEqual HttpStatusCode.NotFound
                    response.content shouldEqual "{\"melding\":\"Finner ikke vedtak $generertVedtakId\"}"
                }
            }

            it("Feil med kafka har ikke påvirkninger på andre deler av appen") {
                val fnrForNyttVedtak = "83291023017"
                vedtakKafkaProducer.send(
                    ProducerRecord(
                        "aapen-helse-sporbar",
                        null,
                        fnrForNyttVedtak,
                        automatiskBehandletVedtak.serialisertTilString(),
                        listOf(RecordHeader("type", "Vedtak".toByteArray()))
                    )
                )

                every { vedtakKafkaConsumer.poll(any<Duration>()) } throws Exception("Denne skal feile")
                val co = launch {
                    vedtakService.start()
                }
                Thread.sleep(100)
                verify(exactly = 0) { brukernotifikasjonKafkaProducer.opprettBrukernotifikasjonOppgave(any(), any()) }
                testDb.finnVedtak(fnrForNyttVedtak).size `should be equal to` 0

                val vedtak = testDb.finnVedtak(fnr)[0].tilRSVedtak()
                val generertVedtakId = vedtak.id
                val opprettet = vedtak.opprettet
                val lestDato = vedtak.lestDato
                with(
                    handleRequest(HttpMethod.Get, "/api/v1/vedtak/$generertVedtakId") {
                        medSelvbetjeningToken(fnr)
                    }
                ) {
                    response.status() shouldEqual HttpStatusCode.OK
                    response.content!!.tilRSVedtak() shouldEqual RSVedtak(
                        id = generertVedtakId,
                        lest = true,
                        lestDato = lestDato,
                        vedtak = automatiskBehandletVedtak,
                        opprettet = opprettet
                    )
                }

                runBlocking {
                    stopApplicationNårAntallKafkaMeldingerErLest(
                        vedtakKafkaConsumer,
                        applicationState,
                        antallKafkaMeldinger = 1
                    )
                    co.join()
                    verify(exactly = 1) {
                        brukernotifikasjonKafkaProducer.opprettBrukernotifikasjonOppgave(
                            any(),
                            any()
                        )
                    }
                    testDb.finnVedtak(fnrForNyttVedtak).size `should be equal to` 1
                }
            }

            it("Feil ved prossesering av vedtak starter konsument på nytt") {
                val fnrForNyttVedtak = "12291023017"
                vedtakKafkaProducer.send(
                    ProducerRecord(
                        "aapen-helse-sporbar",
                        null,
                        fnrForNyttVedtak,
                        automatiskBehandletVedtak.serialisertTilString(),
                        listOf(RecordHeader("type", "Vedtak".toByteArray()))
                    )
                )

                every {
                    brukernotifikasjonKafkaProducer.opprettBrukernotifikasjonOppgave(any(), any())
                } throws Exception("Denne skal feile")
                val co = launch {
                    vedtakService.start()
                }
                Thread.sleep(100)
                verify(exactly = 0) { vedtakKafkaConsumer.commitSync() }

                runBlocking {
                    setupEnvMock()
                    stopApplicationNårAntallKafkaPollErGjort(vedtakKafkaConsumer, applicationState, antallKafkaPoll = 1)
                    co.join()
                    verify(exactly = 1) { vedtakKafkaConsumer.commitSync() }
                    testDb.finnVedtak(fnrForNyttVedtak).size `should be equal to` 1
                }
            }

            it("Consumer poll kan returnere tom liste") {
                stopApplicationNårAntallKafkaPollErGjort(vedtakKafkaConsumer, applicationState, antallKafkaPoll = 2)
                val co = launch {
                    vedtakService.start()
                }
                runBlocking {
                    co.join()
                    verify(exactly = 2) { vedtakKafkaConsumer.poll(any<Duration>()) }
                    verify(exactly = 0) { vedtakKafkaConsumer.commitSync() }
                }
            }

            it("Lagrer ikke duplikate vedtak") {
                val nyttFnr = "duplikat"
                val vedtakFraDb = testDb.finnVedtak(nyttFnr)
                vedtakFraDb.size `should be equal to` 0

                vedtakKafkaProducer.send(
                    ProducerRecord(
                        "aapen-helse-sporbar", null,
                        nyttFnr,
                        automatiskBehandletVedtak.serialisertTilString(),
                        listOf(RecordHeader("type", "Vedtak".toByteArray()))
                    )
                )

                vedtakKafkaProducer.send(
                    ProducerRecord(
                        "aapen-helse-sporbar", null,
                        nyttFnr,
                        automatiskBehandletVedtak.serialisertTilString(),
                        listOf(RecordHeader("type", "Vedtak".toByteArray()))
                    )
                )

                stopApplicationNårAntallKafkaMeldingerErLest(
                    vedtakKafkaConsumer,
                    applicationState,
                    antallKafkaMeldinger = 2
                )

                runBlocking {
                    vedtakService.start()
                }

                val vedtakEtter = testDb.finnVedtak(nyttFnr)
                vedtakEtter.size `should be equal to` 1
            }

            it("Annullering mottas fra kafka topic og lagres i db") {
                val annulleringFraDb = testDb.finnAnnullering(fnr)
                annulleringFraDb.size `should be equal to` 0

                vedtakKafkaProducer.send(
                    ProducerRecord(
                        "aapen-helse-sporbar",
                        null,
                        fnr,
                        annulleringDto.serialisertTilString(),
                        listOf(RecordHeader("type", "Annullering".toByteArray()))
                    )
                )

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

            it("Lagrer ikke duplikate annulleringer") {
                val nyttFnr = "duplikat"
                val annulleringFraDb = testDb.finnAnnullering(nyttFnr)
                annulleringFraDb.size `should be equal to` 0

                vedtakKafkaProducer.send(
                    ProducerRecord(
                        "aapen-helse-sporbar",
                        null,
                        nyttFnr,
                        annulleringDto.serialisertTilString(),
                        listOf(RecordHeader("type", "Annullering".toByteArray()))
                    )
                )
                vedtakKafkaProducer.send(
                    ProducerRecord(
                        "aapen-helse-sporbar",
                        null,
                        nyttFnr,
                        annulleringDto.serialisertTilString(),
                        listOf(RecordHeader("type", "Annullering".toByteArray()))
                    )
                )

                stopApplicationNårAntallKafkaMeldingerErLest(
                    vedtakKafkaConsumer,
                    applicationState,
                    antallKafkaMeldinger = 2
                )

                runBlocking {
                    vedtakService.start()
                }

                val vedtakEtter = testDb.finnVedtak(nyttFnr)
                vedtakEtter.size `should be equal to` 1
            }
        }
    }
})

fun String.tilRSVedtakListe(): List<RSVedtak> = objectMapper.readValue(this)
fun String.tilRSVedtak(): RSVedtak = objectMapper.readValue(this)
