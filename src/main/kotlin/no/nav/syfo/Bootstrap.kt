package no.nav.syfo

import com.auth0.jwk.JwkProviderBuilder
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import io.prometheus.client.hotspot.DefaultExports

import no.nav.syfo.application.ApplicationServer
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.createApplicationEngine
import no.nav.syfo.application.getWellKnown
import no.nav.syfo.application.util.KafkaClients
import java.net.URL
import java.util.concurrent.TimeUnit

val log: Logger = LoggerFactory.getLogger("no.nav.syfo.spinnsyn-backend")

fun main() {
    val env = Environment()

    val vaultSecrets = VaultSecrets()

    val wellKnown = getWellKnown(vaultSecrets.oidcWellKnownUri)

    val jwkProvider = JwkProviderBuilder(URL(wellKnown.jwks_uri))
        .cached(10, 24, TimeUnit.HOURS)
        .rateLimited(10, 1, TimeUnit.MINUTES)
        .build()

    val kafkaClients = KafkaClients(env, vaultSecrets)
    val applicationState = ApplicationState()

    DefaultExports.initialize()
    val applicationEngine = createApplicationEngine(
            env,
            applicationState
    )
    val applicationServer = ApplicationServer(applicationEngine, applicationState)
    applicationServer.start()
    applicationState.ready = true
}