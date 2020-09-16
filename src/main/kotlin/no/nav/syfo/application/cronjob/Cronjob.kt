package no.nav.syfo.application.cronjob

import io.ktor.util.* // ktlint-disable no-wildcard-imports
import no.nav.syfo.Environment
import no.nav.syfo.log
import java.time.* // ktlint-disable no-wildcard-imports
import kotlin.concurrent.timer

@KtorExperimentalAPI
fun setUpCronJob(env: Environment) {

    val podLeaderCoordinator = PodLeaderCoordinator(env)

    val periodeMellomJobber = Duration.ofMinutes(2).toMillis()

    timer(
        initialDelay = periodeMellomJobber,
        period = periodeMellomJobber
    ) {
        if (podLeaderCoordinator.isLeader()) {
            log.info("Kjører spinnsyn cronjob")
        } else {
            log.debug("Jeg er ikke leder")
        }
    }
}
