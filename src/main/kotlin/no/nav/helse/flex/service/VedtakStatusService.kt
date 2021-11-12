package no.nav.helse.flex.service

import no.nav.helse.flex.db.UtbetalingRepository
import no.nav.helse.flex.logger
import no.nav.helse.flex.metrikk.Metrikk
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class VedtakStatusService(
    private val utbetalingRepository: UtbetalingRepository,
    private val metrikk: Metrikk,
) {

    val log = logger()

    fun prosesserUtbetalinger(): Int {
        val utbetalingerIder = utbetalingRepository.utbetalingerKlarTilVarsling()
        var sendt = 0
        utbetalingerIder.forEach { dbId ->
            val oppdatertUtbetaling = utbetalingRepository.findByIdOrNull(dbId)!!

            // TODO: Legg på vedtak-status topic

            utbetalingRepository.save(oppdatertUtbetaling.copy(motattPublisert = Instant.now()))

            metrikk.STATUS_MOTATT.increment()

            sendt += 1
        }
        return sendt
    }

    fun settMotattPulisertTilNå() {
        val now = Instant.now()
        var utbetalingIder = utbetalingRepository.findIdByMotattPublisertIsNull()

        while (utbetalingIder.isNotEmpty()) {
            for (dbId in utbetalingIder) {
                utbetalingRepository.run {
                    findByIdOrNull(dbId)
                        ?.let {
                            save(it.copy(motattPublisert = now))
                        }
                        ?: throw RuntimeException("Finner ikke utbetaling $dbId")
                }
            }

            utbetalingIder = utbetalingRepository.findIdByMotattPublisertIsNull()
        }
    }
}
