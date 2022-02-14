package no.nav.helse.flex.cronjob

import no.nav.helse.flex.AbstractContainerBaseTest
import no.nav.helse.flex.db.UtbetalingDbRecord
import org.amshove.kluent.`should be equal to`
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant

class CronJobTest : AbstractContainerBaseTest() {

    @Autowired
    lateinit var cronJob: CronJob

    final val fnr = "94235902"
    val utbetaling = UtbetalingDbRecord(
        fnr = fnr,
        utbetaling = "",
        opprettet = Instant.now(),
        utbetalingId = "1",
        utbetalingType = "",
        antallVedtak = 1,
        motattPublisert = null,
        skalVisesTilBruker = null,
    )

    @BeforeAll
    fun `ingen utbetalinger`() {
        cronJob.run() `should be equal to` 0
    }

    @Test
    fun `bruker har ikke blitt varslet`() {
        utbetalingRepository.save(utbetaling)
        cronJob.run() `should be equal to` 0
    }

    @Test
    fun `behandler utbetaling der bruker har blitt varslet`() {
        utbetalingRepository.save(
            utbetaling.copy(
                motattPublisert = Instant.now(),
                utbetalingId = "2"
            )
        )

        cronJob.run() `should be equal to` 1

        val utbetaling = utbetalingRepository
            .findUtbetalingDbRecordsByFnr(fnr)
            .find { it.utbetalingId == "2" }

        utbetaling?.skalVisesTilBruker `should be equal to` true
    }

    @Test
    fun `utbetaling er allerede satt til å ikke vises`() {
        utbetalingRepository.save(
            utbetaling.copy(
                motattPublisert = null,
                skalVisesTilBruker = false,
                utbetalingId = "3"
            )
        )

        cronJob.run() `should be equal to` 0

        val utbetaling = utbetalingRepository
            .findUtbetalingDbRecordsByFnr(fnr)
            .find { it.utbetalingId == "3" }

        utbetaling?.skalVisesTilBruker `should be equal to` false
    }

    @Test
    fun `utbetaling er allerede satt til å vises`() {
        utbetalingRepository.save(
            utbetaling.copy(
                motattPublisert = Instant.now(),
                skalVisesTilBruker = true,
                utbetalingId = "4"
            )
        )

        cronJob.run() `should be equal to` 0

        val utbetaling = utbetalingRepository
            .findUtbetalingDbRecordsByFnr(fnr)
            .find { it.utbetalingId == "4" }

        utbetaling?.skalVisesTilBruker `should be equal to` true
    }

    @Test
    fun `Uthenting av utbetalinger er limited til 1000`() {
        for (i in 10..1234) {
            utbetalingRepository.save(
                utbetaling.copy(
                    motattPublisert = Instant.now(),
                    utbetalingId = i.toString()
                )
            )
        }

        cronJob.run() `should be equal to` 1000
        cronJob.run() `should be equal to` 225
    }

    @AfterEach
    fun `kan bare sette visning en gang`() {
        cronJob.run() `should be equal to` 0
    }
}
