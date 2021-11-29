package no.nav.helse.flex.cronjob

import no.nav.helse.flex.logger
import no.nav.helse.flex.service.BrukernotifikasjonService
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@Component
class BrukernotifikasjonJob(
    val leaderElection: LeaderElection,
    val brukernotifikasjonService: BrukernotifikasjonService
) {
    private val log = logger()
    private val sisteTidForBrukernotifikasjon = ZonedDateTime.of(LocalDate.of(2021, 11, 30).atTime(0, 1), ZoneId.of(ZONE_ID_OSLO))

    @Scheduled(cron = "0 0/10 * * * ?")
    fun run() {
        val tid = ZonedDateTime.now(ZoneId.of(ZONE_ID_OSLO))

        if (leaderElection.isLeader()) {
            // TODO: Fjern sammen med annen kode relatert til brukernotifikasjoner etter cutoff tidspunkt.
            if (tid.isAfter(sisteTidForBrukernotifikasjon)) {
                // Begrenser hvor lenge det logges.
                if (tid.isBefore(sisteTidForBrukernotifikasjon.plusHours(1)))
                    log.info(
                        "Kjører ikke brukernotifikasjonsjobb da klokken er [${tid.logformatert()}], " +
                            "som er etter [${sisteTidForBrukernotifikasjon.logformatert()}]."
                    )
                return
            }

            log.info("Kjører brukernotifikasjonjob")
            val antall = brukernotifikasjonService.prosseserUtbetaling()
            log.info("Ferdig med brukernotifikasjonjob. $antall notifikasjoner sendt")
        } else {
            log.info("Kjører ikke brukernotifikasjonjob siden denne podden ikke er leader")
        }
    }

    fun ZonedDateTime.logformatert(): String {
        return format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z"))
    }
}

const val ZONE_ID_OSLO = "Europe/Oslo"
