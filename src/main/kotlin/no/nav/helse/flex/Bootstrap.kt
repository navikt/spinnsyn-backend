package no.nav.helse.flex

import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.util.KtorExperimentalAPI
import io.prometheus.client.hotspot.DefaultExports
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import no.nav.helse.flex.application.ApplicationServer
import no.nav.helse.flex.application.ApplicationState
import no.nav.helse.flex.application.IssuerInternalId
import no.nav.helse.flex.application.JwtIssuer
import no.nav.helse.flex.application.createApplicationEngine
import no.nav.helse.flex.application.getWellKnown
import no.nav.helse.flex.brukernotifkasjon.skapBrukernotifikasjonKafkaProducer
import no.nav.helse.flex.db.Database
import no.nav.helse.flex.util.KafkaClients
import no.nav.helse.flex.varsling.kafka.skapEnkeltvarselKafkaProducer
import no.nav.helse.flex.vedtak.kafka.VedtakConsumer
import no.nav.helse.flex.vedtak.service.SyfoTilgangskontrollService
import no.nav.helse.flex.vedtak.service.VedtakNullstillService
import no.nav.helse.flex.vedtak.service.VedtakService
import no.nav.syfo.kafka.envOverrides
import no.nav.syfo.kafka.loadBaseConfig
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URL
import java.util.concurrent.TimeUnit

val log: Logger = LoggerFactory.getLogger("no.nav.helse.flex.spinnsyn-backend")

val objectMapper: ObjectMapper = ObjectMapper().apply {
    registerKotlinModule()
    registerModule(JavaTimeModule())
    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
}

@KtorExperimentalAPI
fun main() {

    log.info("Starter spinnsyn-backend")
    val env = Environment()

    // Sov litt slik at sidecars er klare
    log.info("Sover i ${env.sidecarInitialDelay} ms i håp om at sidecars er klare")
    Thread.sleep(env.sidecarInitialDelay)

    val selvbetjeningIssuer = hentSelvbetjeningJwtIssuer(env)
    val veilederIssuer = hentVeilederJwtIssuer(env)

    val database = Database(env)

    val kafkaClients = KafkaClients(env)
    val applicationState = ApplicationState()

    DefaultExports.initialize()

    val kafkaBaseConfig = loadBaseConfig(env, env.hentKafkaCredentials()).envOverrides()

    val brukernotifikasjonKafkaProducer = skapBrukernotifikasjonKafkaProducer(kafkaBaseConfig)
    val enkeltvarselKafkaProducer = skapEnkeltvarselKafkaProducer(kafkaBaseConfig)

    val vedtakConsumer = VedtakConsumer(
        kafkaClients.kafkaVedtakConsumer,
        listOf("aapen-helse-sporbar")
    )
    val vedtakService = VedtakService(
        database = database,
        applicationState = applicationState,
        vedtakConsumer = vedtakConsumer,
        brukernotifikasjonKafkaProducer = brukernotifikasjonKafkaProducer,
        environment = env
    )

    val vedtakNullstillService = VedtakNullstillService(
        database = database,
        brukernotifikasjonKafkaProducer = brukernotifikasjonKafkaProducer,
        environment = env
    )

    val syfoTilgangskontrollService = SyfoTilgangskontrollService(environment = env)

    val applicationEngine = createApplicationEngine(
        env = env,
        vedtakService = vedtakService,
        vedtakNullstillService = vedtakNullstillService,
        syfoTilgangskontrollService = syfoTilgangskontrollService,
        selvbetjeningIssuer = selvbetjeningIssuer,
        veilederIssuer = veilederIssuer,
        applicationState = applicationState
    )
    val applicationServer = ApplicationServer(applicationEngine, applicationState)
    applicationServer.start()
    applicationState.ready = true
    log.info("Application server started")

    /*
    createListener(applicationState) {
        vedtakService.start()
    }



    val podLeaderCoordinator = PodLeaderCoordinator(env = env)
    settOppVarslingCronjob(
        database = database,
        podLeaderCoordinator = podLeaderCoordinator,
        enkeltvarselKafkaProducer = enkeltvarselKafkaProducer
    )
    settOppVedtakCronjob(
        database = database,
        podLeaderCoordinator = podLeaderCoordinator,
        env = env,
        brukernotifikasjonKafkaProducer = brukernotifikasjonKafkaProducer
    )

     */
}

private fun hentSelvbetjeningJwtIssuer(env: Environment): JwtIssuer =
    hentJwtIssuer(
        env.loginserviceIdportenDiscoveryUrl,
        expectedAudience = env.loginserviceIdportenAudience,
        issuerInternalId = IssuerInternalId.selvbetjening
    )

private fun hentVeilederJwtIssuer(env: Environment): JwtIssuer =
    hentJwtIssuer(
        env.veilederWellKnownUri,
        expectedAudience = env.veilederExpectedAudience,
        issuerInternalId = IssuerInternalId.veileder
    )

private fun hentJwtIssuer(wllKnownUri: String, expectedAudience: List<String>, issuerInternalId: IssuerInternalId): JwtIssuer {
    val wellKnown = getWellKnown(wllKnownUri)

    val jwkProvider = JwkProviderBuilder(URL(wellKnown.jwks_uri))
        .cached(10, 24, TimeUnit.HOURS)
        .rateLimited(10, 1, TimeUnit.MINUTES)
        .build()

    return JwtIssuer(
        issuerInternalId = issuerInternalId,
        wellKnown = wellKnown,
        expectedAudience = expectedAudience,
        jwkProvider = jwkProvider
    )
}

fun createListener(applicationState: ApplicationState, action: suspend CoroutineScope.() -> Unit): Job =
    GlobalScope.launch {
        try {
            action()
        } catch (ex: Exception) {
            log.error("Noe gikk veldig galt, avslutter applikasjon: {}", ex.message)
        } finally {
            applicationState.alive = false
            applicationState.ready = false
        }
    }
