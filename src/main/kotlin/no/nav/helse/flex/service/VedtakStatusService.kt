package no.nav.helse.flex.service

import no.nav.helse.flex.db.UtbetalingRepository
import no.nav.helse.flex.domene.VedtakStatus
import no.nav.helse.flex.domene.VedtakStatusDTO
import no.nav.helse.flex.kafka.VedtakStatusKafkaProducer
import no.nav.helse.flex.logger
import no.nav.helse.flex.metrikk.Metrikk
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class VedtakStatusService(
    private val utbetalingRepository: UtbetalingRepository,
    private val metrikk: Metrikk,
    private val vedtakStatusKafkaProducer: VedtakStatusKafkaProducer,
) {

    val log = logger()

    fun prosesserUtbetalinger(): Int {
        val utbetalingerIder = utbetalingRepository.utbetalingerKlarTilVarsling()
        var sendt = 0
        utbetalingerIder.forEach { dbId ->
            val oppdatertUtbetaling = utbetalingRepository.findByIdOrNull(dbId)!!

            vedtakStatusKafkaProducer.produserMelding(
                VedtakStatusDTO(
                    id = oppdatertUtbetaling.id!!,
                    fnr = oppdatertUtbetaling.fnr,
                    vedtakStatus = VedtakStatus.MOTATT
                )
            )

            utbetalingRepository.save(oppdatertUtbetaling.copy(motattPublisert = Instant.now()))

            metrikk.STATUS_MOTATT.increment()

            sendt += 1
        }
        return sendt
    }
}
