package no.nav.syfo.application

import io.ktor.server.engine.ApplicationEngine
import java.util.concurrent.TimeUnit

class ApplicationServer(private val applicationServer: ApplicationEngine, private val applicationState: ApplicationState) {
    init {
        Runtime.getRuntime().addShutdownHook(
            Thread {
                this.applicationServer.stop(TimeUnit.SECONDS.toMillis(10), TimeUnit.SECONDS.toMillis(10))
            }
        )
    }

    fun start() {
        applicationServer.start(false)
        applicationState.alive = true
    }
}
