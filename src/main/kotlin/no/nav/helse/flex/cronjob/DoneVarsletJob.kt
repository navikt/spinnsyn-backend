package no.nav.helse.flex.cronjob

import no.nav.helse.flex.db.DoneRepository
import no.nav.helse.flex.logger
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class DoneVarsletJob(
    val leaderElection: LeaderElection,
    val doneRepository: DoneRepository
) {
    val log = logger()

    @Scheduled(initialDelay = 1000L * 60 * 2, fixedDelay = 1000L * 60 * 20)
    fun run() {
        if (leaderElection.isLeader()) {
            val skalDones = doneRepository.findAlleSomSkalDones()
            log.info("Fant ${skalDones.size} meldinger som skal done's på grunn av feil varsel.")
            if (skalDones.size != 1124) {
                throw IllegalStateException("Forventet at vi skal finne 1124 meldinger som skal done's. Fant ${skalDones.size}")
            }
        }
    }
}
