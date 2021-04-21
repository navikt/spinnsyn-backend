package no.nav.helse.flex.vedtak.cronjob
/*

import io.ktor.util.KtorExperimentalAPI
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.nav.brukernotifikasjon.schemas.Done
import no.nav.brukernotifikasjon.schemas.Nokkel
import no.nav.helse.flex.Environment
import no.nav.helse.flex.application.ApplicationState
import no.nav.helse.flex.brukernotifkasjon.BrukernotifikasjonKafkaProdusent
import no.nav.helse.flex.db.DatabaseInterface
import no.nav.helse.flex.leaderelection.PodLeaderCoordinator
import no.nav.helse.flex.log
import no.nav.helse.flex.vedtak.db.finnInternVedtak
import no.nav.helse.flex.vedtak.db.hentVedtakEldreEnnTolvMnd
import no.nav.helse.flex.vedtak.db.slettVedtak
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

fun vedtakCronjob(
    database: DatabaseInterface,
    env: Environment,
    brukernotifikasjonKafkaProdusent: BrukernotifikasjonKafkaProdusent
): VedtakCronjobResultat {
    val resultat = VedtakCronjobResultat()

    log.info("Kjører spinnsyn vedtak cronjob")

    val vedtakSomSkalSlettes = database.hentVedtakEldreEnnTolvMnd()
    vedtakSomSkalSlettes.forEach {
        try {
            val vedtak = database.finnInternVedtak(fnr = it.fnr, vedtaksId = it.id)!!
            if (vedtak.lest == null) {
                // Fjern brukernotifikasjon
                brukernotifikasjonKafkaProdusent.sendDonemelding(
                    Nokkel(env.serviceuserUsername, vedtak.id),
                    Done(Instant.now().toEpochMilli(), vedtak.fnr, vedtak.id)
                )
                resultat.doneMelding++
            }
            database.slettVedtak(vedtakId = vedtak.id, fnr = vedtak.fnr)
            resultat.slettet++
            log.info("Slettet vedtak ${vedtak.id}")
        } catch (e: Exception) {
            log.error("Feil ved sletting av vedtak ${it.id}", e)
        }
    }

    return resultat
}

data class VedtakCronjobResultat(
    var slettet: Int = 0,
    var doneMelding: Int = 0

)

@KtorExperimentalAPI
class VedtakCronjob(
    private val applicationState: ApplicationState,
    private val podLeaderCoordinator: PodLeaderCoordinator,
    private val database: DatabaseInterface,
    private val env: Environment,
    private val brukernotifikasjonKafkaProdusent: BrukernotifikasjonKafkaProdusent
) {
    suspend fun start() = coroutineScope {
        val (initialDelay, interval) = hentKjøretider(env)
        log.info("Schedulerer VedtakCronjob start: $initialDelay ms, interval: $interval ms")
        delay(initialDelay)

        while (applicationState.ready) {
            val job = launch { run() }
            delay(interval)
            if (job.isActive) {
                log.warn("VedtakCronjob er ikke ferdig, venter til den er ferdig")
                job.join()
            }
        }

        log.info("Avslutter VedtakCronjob")
    }

    private fun run() {
        try {
            if (podLeaderCoordinator.isLeader()) {
                vedtakCronjob(
                    database = database,
                    env = env,
                    brukernotifikasjonKafkaProdusent = brukernotifikasjonKafkaProdusent
                )
            } else {
                log.debug("Jeg er ikke leder")
            }
        } catch (ex: Exception) {
            log.error("Feil i VedtakCronjob, kjøres på nytt neste gang", ex)
        }
    }
}

private fun hentKjøretider(env: Environment): Pair<Long, Long> {
    val osloTz = ZoneId.of("Europe/Oslo")
    val now = ZonedDateTime.now(osloTz)
    if (env.cluster == "dev-gcp" || env.cluster == "flex") {
        val omEtMinutt = Duration.ofMinutes(1).toMillis()
        val femMinutter = Duration.ofMinutes(5).toMillis()
        return Pair(omEtMinutt, femMinutter)
    }
    if (env.cluster == "prod-gcp") {
        val nesteNatt = now.next(LocalTime.of(2, 0, 0)) - now.toInstant().toEpochMilli()
        val enDag = Duration.ofDays(1).toMillis()
        return Pair(nesteNatt, enDag)
    }
    throw IllegalStateException("Ukjent cluster name for cronjob ${env.cluster}")
}

private fun ZonedDateTime.next(atTime: LocalTime): Long {
    return if (this.toLocalTime().isAfter(atTime)) {
        this.plusDays(1).withHour(atTime.hour).withMinute(atTime.minute).withSecond(atTime.second).toInstant().toEpochMilli()
    } else {
        this.withHour(atTime.hour).withMinute(atTime.minute).withSecond(atTime.second).toInstant().toEpochMilli()
    }
}
*/
