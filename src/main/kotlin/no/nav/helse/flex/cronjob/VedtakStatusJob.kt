package no.nav.helse.flex.cronjob

import no.nav.helse.flex.logger
import no.nav.helse.flex.service.SendVedtakStatus
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class VedtakStatusJob(
    val leaderElection: LeaderElection,
    val vedtakStatusService: SendVedtakStatus,
) {
    val log = logger()

    @Scheduled(initialDelay = 1000L * 60 * 2, fixedDelay = 1000L * 60)
    fun run() {
        if (leaderElection.isLeader()) {
            vedtakStatusService.prosesserUtbetalinger()
        }
    }
}
