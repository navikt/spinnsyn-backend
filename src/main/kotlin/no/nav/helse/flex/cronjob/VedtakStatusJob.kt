package no.nav.helse.flex.cronjob

import no.nav.helse.flex.logger
import no.nav.helse.flex.service.VedtakStatusService
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class VedtakStatusJob(
    val leaderElection: LeaderElection,
    val vedtakStatusService: VedtakStatusService,
) {
    val log = logger()

    @Scheduled(cron = "0 0/10 * * * ?")
    fun run() {
        if (leaderElection.isLeader()) {
            log.info("Kjører vedtak status job")
            val antall = vedtakStatusService.prosesserUtbetalinger()
            log.info("Ferdig med vedtak status job. $antall vedtak med status motatt")
        }
    }
}
