package no.nav.helse.flex.cronjob

import no.nav.helse.flex.db.UtbetalingRepository
import no.nav.helse.flex.db.VedtakRepository
import no.nav.helse.flex.domene.tilUtbetalingUtbetalt
import no.nav.helse.flex.logger
import org.springframework.data.repository.findByIdOrNull
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class AntallVedtakJob(
    val leaderElection: LeaderElection,
    val utbetalingRepository: UtbetalingRepository,
    val vedtakRepository: VedtakRepository,
) {
    val log = logger()

    @Scheduled(initialDelay = 1000L * 60, fixedDelay = 1000L * 60 * 10)
    fun run() {
        if (leaderElection.isLeader()) {
            log.info("Kjører antall vedtak job")
            val antall = migrate()
            log.info("Ferdig med antall vedtak job. $antall utbetalinger prosseser")
        } else {
            log.info("Kjører ikke antall vedtak job siden denne podden ikke er leader")
        }
    }

    fun migrate(batchSize: Int = 1000): Int {
        var totaltProsesserte = 0

        generateSequence { utbetalingerSomSkalProsesseres(batchSize) }
            .takeWhile { it.isNotEmpty() }
            .forEach { utbetalingerSomSkalProsesseres ->
                utbetalingerSomSkalProsesseres.forEach utbetaling@{ dbId ->
                    val utbetalingDbRecord = utbetalingRepository.findByIdOrNull(dbId)
                    val utbetaling = utbetalingDbRecord?.utbetaling?.tilUtbetalingUtbetalt()

                    if (utbetaling == null) {
                        log.error("Avbryter fordi vi ikke finner utbetaling med id $dbId")
                        return totaltProsesserte
                    }

                    val antallVedtak = utbetaling.antallVedtak ?: 1

                    utbetalingRepository.save(
                        utbetalingDbRecord.copy(
                            antallVedtak = antallVedtak
                        )
                    )

                    totaltProsesserte++
                }

                log.info("Migrerte utbetalinger: $totaltProsesserte")
            }

        return totaltProsesserte
    }

    private fun utbetalingerSomSkalProsesseres(batchSize: Int) =
        utbetalingRepository.utbetalingerUtenAntallVedtakSatt(batchSize)
}
