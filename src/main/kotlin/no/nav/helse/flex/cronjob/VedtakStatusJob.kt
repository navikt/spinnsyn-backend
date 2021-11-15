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

    @Scheduled(initialDelay = 1000L * 60 * 2, fixedDelay = 1000L * 60 * 10)
    fun run() {
        if (leaderElection.isLeader()) {
            log.info("Setter motatt publisert til nå")
            vedtakStatusService.settMotattPulisertTilNå()
            log.info("Ferdig med å sette motatt publisert til nå")
        }
    }
}
