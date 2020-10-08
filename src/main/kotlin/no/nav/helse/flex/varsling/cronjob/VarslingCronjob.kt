package no.nav.helse.flex.varsling.cronjob

import io.ktor.util.KtorExperimentalAPI
import no.nav.helse.flex.application.metrics.FØRSTEGANGSVARSEL
import no.nav.helse.flex.application.metrics.REVARSEL
import no.nav.helse.flex.db.DatabaseInterface
import no.nav.helse.flex.log
import no.nav.helse.flex.util.PodLeaderCoordinator
import no.nav.helse.flex.varsling.domene.EnkeltVarsel
import no.nav.helse.flex.varsling.kafka.EnkeltvarselKafkaProducer
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
import kotlin.concurrent.timer

fun varslingCronjob(
    database: DatabaseInterface,
    enkeltvarselKafkaProducer: EnkeltvarselKafkaProducer
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
                enkeltvarselKafkaProducer.opprettEnkeltVarsel(
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
                enkeltvarselKafkaProducer.opprettEnkeltVarsel(
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

private fun InternVedtak.varselBestillingId(): String {
    return UUID.nameUUIDFromBytes("${this.id}-første-varsel".toByteArray()).toString()
}

private fun InternVedtak.revarselBestillingId(): String {
    return UUID.nameUUIDFromBytes("${this.id}-revarsel".toByteArray()).toString()
}

@KtorExperimentalAPI
fun settOppVarslingCronjob(
    podLeaderCoordinator: PodLeaderCoordinator,
    database: DatabaseInterface,
    enkeltvarselKafkaProducer: EnkeltvarselKafkaProducer
) {

    val periodeMellomJobber = Duration.ofMinutes(1).toMillis()

    timer(
        initialDelay = periodeMellomJobber,
        period = periodeMellomJobber
    ) {
        if (podLeaderCoordinator.isLeader()) {
            varslingCronjob(database = database, enkeltvarselKafkaProducer = enkeltvarselKafkaProducer)
        } else {
            log.debug("Jeg er ikke leder")
        }
    }
}

fun ZonedDateTime.erUtenforFornuftigTidForVarsling(): Boolean {
    return when (this.hour) {
        in 9..16 -> false
        else -> true
    }
}
