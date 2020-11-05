package no.nav.helse.flex.vedtak.service

import io.ktor.util.KtorExperimentalAPI
import kotlinx.coroutines.delay
import no.nav.brukernotifikasjon.schemas.Done
import no.nav.brukernotifikasjon.schemas.Nokkel
import no.nav.brukernotifikasjon.schemas.Oppgave
import no.nav.helse.flex.Environment
import no.nav.helse.flex.application.ApplicationState
import no.nav.helse.flex.application.metrics.MOTTATT_AUTOMATISK_VEDTAK
import no.nav.helse.flex.application.metrics.MOTTATT_MANUELT_VEDTAK
import no.nav.helse.flex.application.metrics.MOTTATT_VEDTAK
import no.nav.helse.flex.brukernotifkasjon.BrukernotifikasjonKafkaProducer
import no.nav.helse.flex.db.DatabaseInterface
import no.nav.helse.flex.log
import no.nav.helse.flex.vedtak.db.Vedtak
import no.nav.helse.flex.vedtak.db.eierVedtak
import no.nav.helse.flex.vedtak.db.finnVedtak
import no.nav.helse.flex.vedtak.db.lesVedtak
import no.nav.helse.flex.vedtak.db.opprettVedtak
import no.nav.helse.flex.vedtak.domene.VedtakDto
import no.nav.helse.flex.vedtak.domene.tilVedtakDto
import no.nav.helse.flex.vedtak.kafka.VedtakConsumer
import org.apache.kafka.clients.consumer.ConsumerRecord
import java.lang.Exception
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
    private val environment: Environment,
    private val delayStart: Long = 10_000L
) {
    suspend fun start() {
        while (applicationState.alive) {
            try {
                run()
            } catch (ex: Exception) {
                log.error("Feil ved konsumering fra kafka, restarter om $delayStart ms", ex)
                vedtakConsumer.unsubscribe()
            }
            delay(delayStart)
        }
    }

    private fun run() {
        log.info("VedtakService started")
        vedtakConsumer.subscribe()

        while (applicationState.ready) {
            val cr = vedtakConsumer.poll()
            cr.forEach {
                if (it.erVedtak()) {
                    mottaVedtak(
                        id = UUID.nameUUIDFromBytes("${it.partition()}-${it.offset()}".toByteArray()),
                        fnr = it.key(),
                        vedtak = it.value(),
                        opprettet = Instant.now()
                    )
                }
            }
            if (!cr.isEmpty) {
                vedtakConsumer.commitSync()
            }
        }
    }

    fun mottaVedtak(id: UUID, fnr: String, vedtak: String, opprettet: Instant) {

        val vedtakSerialisert = try {
            vedtak.tilVedtakDto()
        } catch (e: Exception) {
            log.error("Kunne ikke deserialisere vedtak", e)
            return
        }

        database.finnVedtak(fnr)
            .firstOrNull { it.vedtak == vedtakSerialisert }
            ?.let {
                if (it.id == id.toString()) {
                    log.info("Vedtak $id er allerede mottat, går videre")
                } else {
                    log.warn("Oppretter ikke duplikate vedtak ny id: $id, eksisterende id: ${it.id}")
                }
                return
            }

        val vedtaket = database.opprettVedtak(fnr = fnr, vedtak = vedtak, id = id, lest = false, opprettet = opprettet)

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

        MOTTATT_VEDTAK.inc()

        if (vedtakSerialisert.automatiskBehandling) {
            MOTTATT_AUTOMATISK_VEDTAK.inc()
        } else {
            MOTTATT_MANUELT_VEDTAK.inc()
        }
    }

    fun hentVedtak(fnr: String) =
        database.finnVedtak(fnr)
            .map { it.tilRSVedtak() }

    fun hentVedtak(fnr: String, vedtaksId: String) =
        database.finnVedtak(fnr)
            .find { it.id == vedtaksId }
            ?.tilRSVedtak()

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
    val vedtak: VedtakDto,
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

private fun ConsumerRecord<String, String>.erVedtak(): Boolean {
    return headers().any { header ->
        header.key() == "type" && String(header.value()) == "Vedtak"
    }
}
