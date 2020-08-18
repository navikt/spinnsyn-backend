package no.nav.syfo.vedtak.service

import io.ktor.util.KtorExperimentalAPI
import kotlinx.coroutines.delay
import no.nav.brukernotifikasjon.schemas.Done
import no.nav.brukernotifikasjon.schemas.Nokkel
import no.nav.brukernotifikasjon.schemas.Oppgave
import no.nav.syfo.Environment
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.metrics.MOTTATT_VEDTAK
import no.nav.syfo.brukernotifkasjon.BrukernotifikasjonKafkaProducer
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.log
import no.nav.syfo.vedtak.db.* // ktlint-disable no-wildcard-imports
import no.nav.syfo.vedtak.kafka.VedtakConsumer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.* // ktlint-disable no-wildcard-imports

@KtorExperimentalAPI
class VedtakService(
    private val database: DatabaseInterface,
    private val applicationState: ApplicationState,
    private val vedtakConsumer: VedtakConsumer,
    private val brukernotifikasjonKafkaProducer: BrukernotifikasjonKafkaProducer,
    private val servicebruker: String,
    private val environment: Environment
) {
    suspend fun start() {
        while (applicationState.ready) {
            val consumerRecords = vedtakConsumer.poll()
            consumerRecords.forEach {
                val erVedtak = it.headers().any { header ->
                    header.key() == "type" && String(header.value()) == "Vedtak"
                }
                it.offset()
                if (erVedtak) {
                    val id = UUID.nameUUIDFromBytes("${it.partition()}-${it.offset()}".toByteArray())
                    håndterVedtak(
                        id = id,
                        fnr = it.key(),
                        vedtak = it.value()
                    )
                }
            }
            delay(1)
        }
    }

    fun håndterVedtak(id: UUID, fnr: String, vedtak: String) {
        val vedtaket = database.opprettVedtak(fnr = fnr, vedtak = vedtak, id = id)
        MOTTATT_VEDTAK.inc()
        log.info("Opprettet vedtak med spinnsyn databaseid $id")
        brukernotifikasjonKafkaProducer.opprettBrukernotifikasjonOppgave(
            Nokkel(servicebruker, id.toString()),
            Oppgave(
                vedtaket.opprettet.toEpochMilli(),
                fnr,
                id.toString(),
                "NAV har behandlet søknad om sykepenger",
                "${environment.spvedtakFrontendUrl}/vedtak/$id",
                4
            )
        )
    }

    fun hentVedtak(fnr: String) =
        database.finnVedtak(fnr).map { it.tilRSVedtak() }

    fun hentVedtak(fnr: String, vedtaksId: String) =
        database.finnVedtak(fnr, vedtaksId)?.tilRSVedtak()

    fun eierVedtak(fnr: String, vedtaksId: String) =
        database.eierVedtak(fnr, vedtaksId)

    fun lesVedtak(fnr: String, vedtaksId: String): Boolean {
        val bleLest = database.lesVedtak(fnr, vedtaksId)
        if (bleLest) {
            brukernotifikasjonKafkaProducer.sendDonemelding(
                Nokkel(servicebruker, vedtaksId),
                Done(Instant.now().toEpochMilli(), fnr, vedtaksId)
            )
        }
        return bleLest
    }
}

data class RSVedtak(
    val id: String,
    val lest: Boolean,
    val vedtak: Any,
    val opprettet: LocalDate
)

fun Vedtak.tilRSVedtak(): RSVedtak {
    return RSVedtak(
        id = this.id,
        lest = this.lest,
        vedtak = this.vedtak,
        opprettet = LocalDate.ofInstant(this.opprettet, ZoneId.of("Europe/Oslo"))
    )
}
