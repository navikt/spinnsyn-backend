package no.nav.helse.flex.vedtak.service

import io.ktor.util.KtorExperimentalAPI
import kotlinx.coroutines.delay
import no.nav.brukernotifikasjon.schemas.Done
import no.nav.brukernotifikasjon.schemas.Nokkel
import no.nav.brukernotifikasjon.schemas.Oppgave
import no.nav.helse.flex.Environment
import no.nav.helse.flex.application.ApplicationState
import no.nav.helse.flex.application.metrics.MOTTATT_ANNULLERING_VEDTAK
import no.nav.helse.flex.application.metrics.MOTTATT_AUTOMATISK_VEDTAK
import no.nav.helse.flex.application.metrics.MOTTATT_MANUELT_VEDTAK
import no.nav.helse.flex.application.metrics.MOTTATT_VEDTAK
import no.nav.helse.flex.brukernotifkasjon.BrukernotifikasjonKafkaProducer
import no.nav.helse.flex.db.DatabaseInterface
import no.nav.helse.flex.log
import no.nav.helse.flex.vedtak.db.Annullering
import no.nav.helse.flex.vedtak.db.Vedtak
import no.nav.helse.flex.vedtak.db.eierVedtak
import no.nav.helse.flex.vedtak.db.finnAnnullering
import no.nav.helse.flex.vedtak.db.finnVedtak
import no.nav.helse.flex.vedtak.db.lesVedtak
import no.nav.helse.flex.vedtak.db.opprettAnnullering
import no.nav.helse.flex.vedtak.db.opprettVedtak
import no.nav.helse.flex.vedtak.domene.Periode
import no.nav.helse.flex.vedtak.domene.VedtakDto
import no.nav.helse.flex.vedtak.domene.tilAnnulleringDto
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
                } else if (it.erAnnullering()) {
                    mottaAnnullering(
                        id = UUID.nameUUIDFromBytes("${it.partition()}-${it.offset()}".toByteArray()),
                        fnr = it.key(),
                        annullering = it.value(),
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

    fun mottaAnnullering(id: UUID, fnr: String, annullering: String, opprettet: Instant) {
        val annulleringSerialisert = try {
            annullering.tilAnnulleringDto()
        } catch (e: Exception) {
            log.error("Kunne ikke deserialisere annullering", e)
            return
        }

        database.finnAnnullering(fnr)
            .firstOrNull { it.annullering == annulleringSerialisert }
            ?.let {
                if (it.id == id.toString()) {
                    log.info("Annullering $id er allerede mottat, går videre")
                } else {
                    log.warn("Oppretter ikke duplikate annulleringer ny id: $id, eksisterende id: ${it.id}")
                }
                return
            }

        database.opprettAnnullering(
            id = id,
            fnr = fnr,
            annullering = annullering,
            opprettet = opprettet
        )

        MOTTATT_ANNULLERING_VEDTAK.inc()

        log.info("Opprettet annullering med spinnsyn databaseid $id")
    }

    fun hentVedtak(fnr: String): List<RSVedtak> {
        val annulleringer = database.finnAnnullering(fnr)
        return database.finnVedtak(fnr)
            .map { it.tilRSVedtak(annulleringer.forVedtak(it)) }
    }

    fun hentVedtak(fnr: String, vedtaksId: String): RSVedtak? {
        val annulleringer = database.finnAnnullering(fnr)
        val vedtak = database.finnVedtak(fnr)
            .find { it.id == vedtaksId }
        return vedtak?.tilRSVedtak(annulleringer.forVedtak(vedtak))
    }

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
    val opprettet: LocalDate,
    val annullert: Boolean = false
)

fun Vedtak.tilRSVedtak(annullering: Boolean = false): RSVedtak {
    return RSVedtak(
        id = this.id,
        lest = this.lest,
        vedtak = this.vedtak,
        opprettet = LocalDate.ofInstant(this.opprettet, ZoneId.of("Europe/Oslo")),
        annullert = annullering
    )
}

fun List<Annullering>.forVedtak(vedtak: Vedtak): Boolean =
    this.any {
        vedtak.matcherAnnullering(it)
    }

fun Vedtak.matcherAnnullering(annullering: Annullering): Boolean {
    val vedtaksperiode = Periode(this.vedtak.fom, this.vedtak.tom)
    val annulleringsperiode = Periode(annullering.annullering.fom ?: return false, annullering.annullering.tom ?: return false)
    return vedtaksperiode.overlapper(annulleringsperiode) &&
        (
            this.vedtak.organisasjonsnummer == annullering.annullering.orgnummer ||
                this.vedtak.utbetalinger.any { it.mottaker == annullering.annullering.orgnummer }
            )
}

private fun ConsumerRecord<String, String>.erVedtak(): Boolean {
    return headers().any { header ->
        header.key() == "type" && String(header.value()) == "Vedtak"
    }
}

private fun ConsumerRecord<String, String>.erAnnullering(): Boolean {
    return headers().any { header ->
        header.key() == "type" && String(header.value()) == "Annullering"
    }
}
