package no.nav.helse.flex.arkivering

import no.nav.helse.flex.cronjob.LeaderElection
import no.nav.helse.flex.logger
import org.springframework.stereotype.Component

@Component
class VedtakArkiveringJob(
    val leaderElection: LeaderElection,
    val vedtakArkiveringService: VedtakArkiveringService,
) {

    val log = logger()

    // @Scheduled(initialDelay = 60L, fixedDelay = 120L, timeUnit = TimeUnit.SECONDS)
    fun arkiverUtbetalinger() {
        if (leaderElection.isLeader()) {
            try {
                vedtakArkiveringService.arkiverUtbetalinger()
            } catch (e: Exception) {
                log.error("Feil ved kjøring av arkiveringsjobb for utbetalinger: ", e)
            }
        }
    }

    // @Scheduled(initialDelay = 60L, fixedDelay = 120L, timeUnit = TimeUnit.SECONDS)
    fun arkiverRetroVedtak() {
        if (leaderElection.isLeader()) {
            try {
                vedtakArkiveringService.arkiverRetroVedtak()
            } catch (e: Exception) {
                log.error("Feil ved kjøring av arkiveringsjobb for retro vedtak: ", e)
            }
        }
    }
}
