package no.nav.helse.flex.cronjob

import no.nav.helse.flex.logger
import no.nav.helse.flex.service.SendVedtakStatus
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class VedtakStatusJob(
    val leaderElection: LeaderElection,
    val vedtakStatusService: SendVedtakStatus,
) {
    val log = logger()

    @Scheduled(initialDelay = 5, fixedDelay = 10, timeUnit = TimeUnit.MINUTES)
    fun run() {
        if (leaderElection.isLeader()) {
            vedtakStatusService.prosesserUtbetalinger()
        }
    }
}
