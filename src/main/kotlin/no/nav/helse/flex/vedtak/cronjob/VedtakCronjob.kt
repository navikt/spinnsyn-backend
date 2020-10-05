package no.nav.helse.flex.vedtak.cronjob

import io.ktor.util.KtorExperimentalAPI
import no.nav.brukernotifikasjon.schemas.Done
import no.nav.brukernotifikasjon.schemas.Nokkel
import no.nav.helse.flex.Environment
import no.nav.helse.flex.brukernotifkasjon.BrukernotifikasjonKafkaProducer
import no.nav.helse.flex.db.DatabaseInterface
import no.nav.helse.flex.log
import no.nav.helse.flex.util.PodLeaderCoordinator
import no.nav.helse.flex.vedtak.db.finnInternVedtak
import no.nav.helse.flex.vedtak.db.hentVedtakEldreEnnTolvMnd
import no.nav.helse.flex.vedtak.db.slettVedtak
import java.time.Duration
import java.time.Instant
import kotlin.concurrent.timer

fun vedtakCronjob(
    database: DatabaseInterface,
    env: Environment,
    brukernotifikasjonKafkaProducer: BrukernotifikasjonKafkaProducer
): VedtakCronjobResultat {
    val resultat = VedtakCronjobResultat()

    log.info("Kjører spinnsyn vedtak cronjob")

    val vedtakSomSkalSlettes = database.hentVedtakEldreEnnTolvMnd()
    vedtakSomSkalSlettes.forEach {
        try {
            val vedtak = database.finnInternVedtak(fnr = it.fnr, vedtaksId = it.id)!!
            if (vedtak.lest == null) {
                // Fjern brukernotifikasjon
                brukernotifikasjonKafkaProducer.sendDonemelding(
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
fun settOppVedtakCronjob(
    podLeaderCoordinator: PodLeaderCoordinator,
    database: DatabaseInterface,
    env: Environment,
    brukernotifikasjonKafkaProducer: BrukernotifikasjonKafkaProducer
) {

    val periodeMellomJobber = Duration.ofMinutes(1).toMillis()

    timer(
        initialDelay = periodeMellomJobber,
        period = periodeMellomJobber
    ) {
        if (podLeaderCoordinator.isLeader()) {
            vedtakCronjob(
                database = database,
                env = env,
                brukernotifikasjonKafkaProducer = brukernotifikasjonKafkaProducer
            )
        } else {
            log.debug("Jeg er ikke leder")
        }
    }
}
