package no.nav.syfo

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import io.prometheus.client.hotspot.DefaultExports

import no.nav.syfo.application.ApplicationServer
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.createApplicationEngine

val log: Logger = LoggerFactory.getLogger("no.nav.syfo.spinnsyn-backend")

fun main() {
    val env = Environment()
    DefaultExports.initialize()
    val applicationState = ApplicationState()
    val applicationEngine = createApplicationEngine(
            env,
            applicationState
    )
    val applicationServer = ApplicationServer(applicationEngine, applicationState)
    applicationServer.start()
    applicationState.ready = true
}