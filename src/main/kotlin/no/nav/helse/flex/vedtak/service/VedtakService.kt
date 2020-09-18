package no.nav.helse.flex.vedtak.service

import io.ktor.util.KtorExperimentalAPI
import kotlinx.coroutines.delay
import no.nav.brukernotifikasjon.schemas.Done
import no.nav.brukernotifikasjon.schemas.Nokkel
import no.nav.brukernotifikasjon.schemas.Oppgave
import no.nav.helse.flex.Environment
import no.nav.helse.flex.application.ApplicationState
import no.nav.helse.flex.application.metrics.MOTTATT_VEDTAK
import no.nav.helse.flex.brukernotifkasjon.BrukernotifikasjonKafkaProducer
import no.nav.helse.flex.db.DatabaseInterface
import no.nav.helse.flex.log
import no.nav.helse.flex.vedtak.db.Vedtak
import no.nav.helse.flex.vedtak.db.eierVedtak
import no.nav.helse.flex.vedtak.db.finnVedtak
import no.nav.helse.flex.vedtak.db.lesVedtak
import no.nav.helse.flex.vedtak.db.opprettVedtak
import no.nav.helse.flex.vedtak.kafka.VedtakConsumer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

@KtorExperimentalAPI
class VedtakService(
    private val database: DatabaseInterface,
    private val applicationState: ApplicationState,
    private val vedtakConsumer: VedtakConsumer,
    private val brukernotifikasjonKafkaProducer: BrukernotifikasjonKafkaProducer,
    private val environment: Environment
) {
    suspend fun start() {
        log.info("VedtakService stated")

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
        if (environment.isProd()) {
            log.info("Mottok vedtak som ville fått spinnsyn databaseid $id, men lagrer ikke i produksjon ennå")
            return
        }
        val vedtaket = database.opprettVedtak(fnr = fnr, vedtak = vedtak, id = id)
        MOTTATT_VEDTAK.inc()
        log.info("Opprettet vedtak med spinnsyn databaseid $id")
        brukernotifikasjonKafkaProducer.opprettBrukernotifikasjonOppgave(
            Nokkel(environment.serviceuserUsername, id.toString()),
            Oppgave(
                vedtaket.opprettet.toEpochMilli(),
                fnr,
                id.toString(),
                "Sykepengene dine er beregnet - se resultatet",
                "${environment.spinnsynFrontendUrl}/vedtak/$id",
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
                Nokkel(environment.serviceuserUsername, vedtaksId),
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
