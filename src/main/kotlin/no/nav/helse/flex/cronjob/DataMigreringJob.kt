package no.nav.helse.flex.cronjob

import no.nav.helse.flex.db.UtbetalingRepository
import no.nav.helse.flex.db.VedtakDbRecord
import no.nav.helse.flex.db.VedtakRepository
import no.nav.helse.flex.domene.tilUtbetalingUtbetalt
import no.nav.helse.flex.logger
import org.springframework.data.repository.findByIdOrNull
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class DataMigreringJob(
    val leaderElection: LeaderElection,
    val utbetalingRepository: UtbetalingRepository,
    val vedtakRepository: VedtakRepository,
) {
    val log = logger()

    @Scheduled(initialDelay = 1000L * 60, fixedDelay = 1000L * 60 * 10)
    fun run() {
        if (leaderElection.isLeader()) {
            log.info("Kjører data migrering")
            val antall = migrate()
            log.info("Ferdig med data migrering. $antall utbetalinger prosseser")
        } else {
            log.info("Kjører ikke data migrering siden denne podden ikke er leader")
        }
    }

    fun migrate(batchSize: Int = 1000): Int {
        log.debug("Using batchSize: $batchSize")
        var totaltProsesserte = 0
        var prosessertILoop = -1

        generateSequence { utbetalingerSomSkalProsesseres(batchSize) }
            .takeWhile { it.isNotEmpty() && prosessertILoop != 0; }
            .forEach { utbetalingerSomSkalProsesseres ->
                prosessertILoop = 0
                utbetalingerSomSkalProsesseres.forEach utbetaling@{ dbId ->
                    val utbetalingDbRecord = utbetalingRepository.findByIdOrNull(dbId)
                    val utbetaling = utbetalingDbRecord?.utbetaling?.tilUtbetalingUtbetalt()

                    if (utbetaling == null) {
                        log.error("Avbryter fordi vi ikke finner utbetaling med id $dbId")
                        return totaltProsesserte
                    }

                    val relaterteVedtak = relaterteVedtak(utbetaling.utbetalingId).sortedBy { it.id }

                    val vedtakFelter = relaterteVedtak.hentFelter()

                    utbetalingRepository.save(
                        utbetalingDbRecord.copy(
                            lest = vedtakFelter.lest,
                            brukernotifikasjonSendt = vedtakFelter.sendt,
                            brukernotifikasjonUtelatt = vedtakFelter.utelatt,
                            varsletMed = vedtakFelter.varsletMed,
                        )
                    )

                    prosessertILoop++
                }

                totaltProsesserte += prosessertILoop
                log.info("Migrerte utbetalinger: $totaltProsesserte")
            }

        return totaltProsesserte
    }

    private data class VedtakFelter(
        val lest: Instant?,
        val sendt: Instant?,
        val utelatt: Instant?,
        val varsletMed: String,
    )

    private fun List<VedtakDbRecord>.hentFelter(): VedtakFelter {
        val vedtaket = this.first()

        val erAlleUtelatt = this.all {
            it.brukernotifikasjonUtelatt != null
        }
        return if (erAlleUtelatt) {
            VedtakFelter(
                lest = null,
                sendt = null,
                utelatt = vedtaket.brukernotifikasjonUtelatt,
                varsletMed = vedtaket.id!!,
            )
        } else {
            VedtakFelter(
                lest = vedtaket.lest,
                sendt = vedtaket.brukernotifikasjonSendt,
                utelatt = null,
                varsletMed = vedtaket.id!!,
            )
        }
    }

    private fun utbetalingerSomSkalProsesseres(batchSize: Int) =
        utbetalingRepository.utbetalingerSomSkalProsesseres(batchSize)

    private fun relaterteVedtak(utbetalingId: String) = vedtakRepository.findByUtbetalingId(utbetalingId)
}
