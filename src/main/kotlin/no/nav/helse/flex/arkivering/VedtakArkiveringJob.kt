package no.nav.helse.flex.arkivering

import no.nav.helse.flex.cronjob.LeaderElection
import no.nav.helse.flex.logger
import org.springframework.stereotype.Component

const val ARKIVERING_BATCH_SIZE = 100

@Component
class VedtakArkiveringJob(
    val leaderElection: LeaderElection,
    val vedtakArkiveringService: VedtakArkiveringService,
) {

    val log = logger()

    // @Scheduled(initialDelay = 120L, fixedDelay = 60L, timeUnit = TimeUnit.SECONDS)
    fun arkiverUtbetalinger() {
        if (leaderElection.isLeader()) {
            try {
                vedtakArkiveringService.arkiverUtbetalinger(ARKIVERING_BATCH_SIZE)
            } catch (e: Exception) {
                log.error("Feil ved kjøring av arkiveringsjobb for utbetalinger: ", e)
            }
        }
    }

    //  @Scheduled(initialDelay = 120L, fixedDelay = 60L, timeUnit = TimeUnit.SECONDS)
    fun arkiverRetroVedtak() {
        if (leaderElection.isLeader()) {
            try {
                vedtakArkiveringService.arkiverRetroVedtak(ARKIVERING_BATCH_SIZE)
            } catch (e: Exception) {
                log.error("Feil ved kjøring av arkiveringsjobb for retro vedtak: ", e)
            }
        }
    }
}
