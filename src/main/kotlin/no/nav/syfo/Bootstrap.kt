package no.nav.syfo

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
import no.nav.syfo.application.ApplicationServer
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.createApplicationEngine
import no.nav.syfo.application.getWellKnown
import no.nav.syfo.application.util.KafkaClients
import no.nav.syfo.application.util.KafkaFactory.Companion.getBrukernotifikasjonKafkaProducer
import no.nav.syfo.db.Database
import no.nav.syfo.kafka.envOverrides
import no.nav.syfo.kafka.loadBaseConfig
import no.nav.syfo.vedtak.kafka.VedtakConsumer
import no.nav.syfo.vedtak.service.VedtakService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URL
import java.util.concurrent.TimeUnit

val log: Logger = LoggerFactory.getLogger("no.nav.syfo.spinnsyn-backend")

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
    Thread.sleep(env.sidecarInitialDelay)
    log.info("Sov i ${env.sidecarInitialDelay} ms i håp om at sidecars er klare")

    val wellKnown = getWellKnown(env.oidcWellKnownUri)

    val jwkProvider = JwkProviderBuilder(URL(wellKnown.jwks_uri))
        .cached(10, 24, TimeUnit.HOURS)
        .rateLimited(10, 1, TimeUnit.MINUTES)
        .build()

    val database = Database(env)

    val kafkaClients = KafkaClients(env)
    val applicationState = ApplicationState()

    DefaultExports.initialize()

    val kafkaBaseConfig = loadBaseConfig(env, env.hentKafkaCredentials()).envOverrides()

    val brukernotifikasjonKafkaProducer = getBrukernotifikasjonKafkaProducer(kafkaBaseConfig)

    val vedtakConsumer = VedtakConsumer(kafkaClients.kafkaVedtakConsumer)
    val vedtakService = VedtakService(
        database = database,
        applicationState = applicationState,
        vedtakConsumer = vedtakConsumer,
        brukernotifikasjonKafkaProducer = brukernotifikasjonKafkaProducer,
        environment = env
    )

    val applicationEngine = createApplicationEngine(
        env = env,
        vedtakService = vedtakService,
        jwkProvider = jwkProvider,
        applicationState = applicationState,
        issuer = wellKnown.issuer,
        loginserviceClientId = env.loginserviceClientId
    )
    val applicationServer = ApplicationServer(applicationEngine, applicationState)
    applicationServer.start()
    applicationState.ready = true
    log.info("Application server stated")
    createListener(applicationState) {
        vedtakService.start()
    }
}

fun createListener(applicationState: ApplicationState, action: suspend CoroutineScope.() -> Unit): Job =
    GlobalScope.launch {
        try {
            action()
        } catch (ex: Exception) {
            log.error("Noe gikk galt: {}", ex.message)
        } finally {
            applicationState.alive = false
            applicationState.ready = false
        }
    }
