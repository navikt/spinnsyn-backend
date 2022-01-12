package no.nav.helse.flex.done

import no.nav.helse.flex.cronjob.LeaderElection
import no.nav.helse.flex.logger
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

const val DONE_ULESTE_BATCH_SIZE = 100

@Component
class DoneUlesteVedtakJob(
    private val leaderElection: LeaderElection,
    private val doneUlesteVedtakService: DoneUlesteVedtakService
) {

    val log = logger()

    @Scheduled(initialDelay = 120L, fixedDelay = 10L, timeUnit = TimeUnit.SECONDS)
    fun doneUlesteVedtak() {
        if (leaderElection.isLeader()) {
            try {
                doneUlesteVedtakService.doneUlesteVedtak(DONE_ULESTE_BATCH_SIZE)
            } catch (e: Exception) {
                log.error("Feil ved kjøring av job som sender done-melding for uleste vedtak: ", e)
            }
        }
    }
}
