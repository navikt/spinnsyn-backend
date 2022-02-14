package no.nav.helse.flex.cronjob

import no.nav.helse.flex.db.UtbetalingRepository
import no.nav.helse.flex.logger
import no.nav.helse.flex.metrikk.Metrikk
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit.SECONDS

@Component
class CronJob(
    val leaderElection: LeaderElection,
    val utbetalingRepository: UtbetalingRepository,
    val metrikk: Metrikk,
) {
    val log = logger()

    @Scheduled(initialDelay = 60 * 3, fixedDelay = 1, timeUnit = SECONDS)
    fun run(): Int {
        var behandlet = 0

        if (leaderElection.isLeader()) {
            utbetalingRepository.utbetalingerMedSkalVisesTilBrukerIkkeSatt().forEach {
                utbetalingRepository.settSkalVises(it.id)
                metrikk.SKAL_VISES_TIL_BRUKER.increment()
                behandlet++
            }
        }

        return behandlet
    }
}
