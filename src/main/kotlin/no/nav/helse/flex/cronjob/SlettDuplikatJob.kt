package no.nav.helse.flex.cronjob

import no.nav.helse.flex.db.AnnulleringDAO
import no.nav.helse.flex.logger
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class SlettDuplikatJob(
    val leaderElection: LeaderElection,
    val annulleringDAO: AnnulleringDAO,
) {
    val log = logger()

    @Scheduled(initialDelay = 5, fixedDelay = 10, timeUnit = TimeUnit.MINUTES)
    fun run() {
        if (leaderElection.isLeader()) {
            annulleringDAO.slettAnnullering("66e11f7a-3f54-343b-96b6-502c567861b8")
            log.info(
                "Slettet duplikat annullering 66e11f7a-3f54-343b-96b6-502c567861b8 fra database",
            )
        }
    }
}
