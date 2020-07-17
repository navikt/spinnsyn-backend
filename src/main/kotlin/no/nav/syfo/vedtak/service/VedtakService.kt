package no.nav.syfo.vedtak.service

import io.ktor.util.KtorExperimentalAPI
import kotlinx.coroutines.delay
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.metrics.MOTTATT_VEDTAK
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.log
import no.nav.syfo.vedtak.kafka.VedtakConsumer

@KtorExperimentalAPI
class VedtakService(
    //private val database: DatabaseInterface,
    private val applicationState: ApplicationState,
    private val vedtakConsumer: VedtakConsumer

) {
    suspend fun start() {
        while (applicationState.ready) {
            val jsonNodesAsString = vedtakConsumer.poll()
            jsonNodesAsString.forEach {
                val erVedtak = it.headers().any { header ->
                    header.key() == "type" && String(header.value()) == "Vedtak"
                }
                if (erVedtak) {
                    håndterVedtak(fnr = it.key(), vedtak = it.value())
                }
            }
            delay(1)
        }
    }

    fun håndterVedtak(fnr: String, vedtak: String) {
        MOTTATT_VEDTAK.inc()
        log.info("Mottatt vedtak: $vedtak $fnr")
    }
}

