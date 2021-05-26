package no.nav.helse.flex.cronjob

import no.nav.helse.flex.logger
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class BrukernotifikasjonJob(
    val leaderElection: LeaderElection
) {
    val log = logger()

    @Scheduled(cron = "0 0/10 * * * ?")
    fun run() {
        if (leaderElection.isLeader()) {

            log.info("Kjører brukernotifikasjonjob")

            log.info("Ferdig med brukernotifikasjonjob")
        }
    }
}
