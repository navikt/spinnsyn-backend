package no.nav.helse.flex.varsling.cronjob

import io.ktor.util.* // ktlint-disable no-wildcard-imports
import no.nav.helse.flex.db.DatabaseInterface
import no.nav.helse.flex.log
import no.nav.helse.flex.util.PodLeaderCoordinator
import no.nav.helse.flex.varsling.domene.EnkeltVarsel
import no.nav.helse.flex.varsling.kafka.EnkeltvarselKafkaProducer
import no.nav.helse.flex.vedtak.db.* // ktlint-disable no-wildcard-imports
import java.time.* // ktlint-disable no-wildcard-imports
import java.util.* // ktlint-disable no-wildcard-imports
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
                        varselTypeId = "NySykmeldingUtenLenke" // TODO bytt til vårt eget varsel
                    )
                )
                database.settVedtakVarslet(it.id)
                resultat.varsler++
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
                        varselTypeId = "NySykmeldingUtenLenke" // TODO bytt til vårt eget varsel
                    )
                )
                database.settVedtakRevarslet(it.id)
                resultat.revarsler++
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
