package no.nav.helse.flex.varsling.cronjob

import io.ktor.util.KtorExperimentalAPI
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.nav.helse.flex.application.ApplicationState
import no.nav.helse.flex.application.metrics.FØRSTEGANGSVARSEL
import no.nav.helse.flex.application.metrics.REVARSEL
import no.nav.helse.flex.db.DatabaseInterface
import no.nav.helse.flex.leaderelection.PodLeaderCoordinator
import no.nav.helse.flex.log
import no.nav.helse.flex.varsling.domene.EnkeltVarsel
import no.nav.helse.flex.varsling.kafka.EnkeltvarselKafkaProdusent
import no.nav.helse.flex.vedtak.db.InternVedtak
import no.nav.helse.flex.vedtak.db.finnInternVedtak
import no.nav.helse.flex.vedtak.db.hentVedtakForRevarsling
import no.nav.helse.flex.vedtak.db.hentVedtakForVarsling
import no.nav.helse.flex.vedtak.db.settVedtakRevarslet
import no.nav.helse.flex.vedtak.db.settVedtakVarslet
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

fun varslingCronjob(
    database: DatabaseInterface,
    enkeltvarselKafkaProdusent: EnkeltvarselKafkaProdusent
): VarslingCronjobResultat {
    val resultat = VarslingCronjobResultat()
    if (Instant.now().atZone(ZoneId.of("Europe/Oslo")).erUtenforFornuftigTidForVarsling()) {
        log.debug("Utenfor fornuftig tid for varsel")
        return resultat
    }

    log.info("Kjører spinnsyn varsling cronjob")

    val vedtakForVarsling = database.hentVedtakForVarsling()
    vedtakForVarsling.forEach {

        try {
            // Hent og sjekk at det fortsatt ikke er lest
            val vedtak = database.finnInternVedtak(fnr = it.fnr, vedtaksId = it.id)!!
            if (vedtak.lest == null) {
                val varselBestillingId = vedtak.varselBestillingId()
                enkeltvarselKafkaProdusent.opprettEnkeltVarsel(
                    EnkeltVarsel(
                        fodselsnummer = vedtak.fnr,
                        varselBestillingId = varselBestillingId,
                        varselTypeId = "NyttSykepengevedtak"
                    )
                )
                database.settVedtakVarslet(it.id)
                resultat.varsler++
                FØRSTEGANGSVARSEL.inc()

                log.info("Sendte varsel for vedtak ${it.id} med varselBestillingId $varselBestillingId")
            }
        } catch (e: Exception) {
            log.error("Feil ved varling av vedtak ${it.id}", e)
        }
    }

    val vedtakForReVarsling = database.hentVedtakForRevarsling()
    vedtakForReVarsling.forEach {

        try {
            // Hent og sjekk at det fortsatt ikke er lest
            val vedtak = database.finnInternVedtak(fnr = it.fnr, vedtaksId = it.id)!!
            if (vedtak.lest == null) {
                val varselBestillingId = vedtak.revarselBestillingId()
                enkeltvarselKafkaProdusent.opprettEnkeltVarsel(
                    EnkeltVarsel(
                        fodselsnummer = vedtak.fnr,
                        varselBestillingId = varselBestillingId,
                        varselTypeId = "NyttSykepengevedtak"
                    )
                )
                database.settVedtakRevarslet(it.id)
                resultat.revarsler++
                REVARSEL.inc()

                log.info("Sendte revarsel for vedtak ${it.id} med varselBestillingId $varselBestillingId")
            }
        } catch (e: Exception) {
            log.error("Feil ved varling av vedtak ${it.id}", e)
        }
    }

    return resultat
}

data class VarslingCronjobResultat(
    var varsler: Int = 0,
    var revarsler: Int = 0

)

private fun ZonedDateTime.erUtenforFornuftigTidForVarsling(): Boolean {
    return when (this.hour) {
        in 9..16 -> false
        else -> true
    }
}

private fun InternVedtak.varselBestillingId(): String {
    return UUID.nameUUIDFromBytes("${this.id}-første-varsel".toByteArray()).toString()
}

private fun InternVedtak.revarselBestillingId(): String {
    return UUID.nameUUIDFromBytes("${this.id}-revarsel".toByteArray()).toString()
}

@KtorExperimentalAPI
class VarslingCronjob(
    private val applicationState: ApplicationState,
    private val podLeaderCoordinator: PodLeaderCoordinator,
    private val database: DatabaseInterface,
    private val enkeltvarselKafkaProdusent: EnkeltvarselKafkaProdusent
) {
    suspend fun start() = coroutineScope {
        val interval = Duration.ofMinutes(1).toMillis()
        log.info("Schedulerer VarslingCronjob interval: $interval ms")
        delay(interval)

        while (applicationState.alive) {
            val job = launch { run() }
            delay(interval)
            if (job.isActive) {
                log.warn("VarslingCronjob er ikke ferdig, venter til den er ferdig")
                job.join()
            }
        }

        log.info("Avslutter VarslingCronjob")
    }

    private fun run() {
        try {
            if (podLeaderCoordinator.isLeader()) {
                varslingCronjob(database = database, enkeltvarselKafkaProdusent = enkeltvarselKafkaProdusent)
            } else {
                log.debug("Jeg er ikke leder")
            }
        } catch (ex: Exception) {
            log.error("Feil i VarslingCronjob, kjøres på nytt neste gang", ex)
        }
    }
}
