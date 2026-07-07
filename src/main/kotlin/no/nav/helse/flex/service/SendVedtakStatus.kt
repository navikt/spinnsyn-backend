package no.nav.helse.flex.service

import no.nav.helse.flex.db.UtbetalingRepository
import no.nav.helse.flex.db.VedtakRepository
import no.nav.helse.flex.domene.VedtakStatus
import no.nav.helse.flex.domene.VedtakStatusDTO
import no.nav.helse.flex.kafka.VedtakStatusKafkaProducer
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class SendVedtakStatus(
    private val utbetalingRepository: UtbetalingRepository,
    private val vedtakRepository: VedtakRepository,
    private val vedtakStatusKafkaProducer: VedtakStatusKafkaProducer,
) {
    fun prosesserUtbetalinger(): Int {
        val utbetalinger = utbetalingRepository.utbetalingerKlarTilVarsling()
        if (utbetalinger.isEmpty()) return 0

        val vedtakGruppert =
            vedtakRepository
                .hentUtbetalingIdForVedtakMedUtbetalingId(utbetalinger.map { it.utbetalingId })
                .groupBy { it }
                .map { it.key to it.value.size }

        val utbetalingerMedAlleVedtak =
            utbetalinger.filter { utbetaling ->
                vedtakGruppert
                    .find {
                        it.first == utbetaling.utbetalingId &&
                            it.second == utbetaling.antallVedtak
                    } != null
            }

        var sendt = 0

        utbetalingerMedAlleVedtak.forEach { ut ->
            vedtakStatusKafkaProducer.produserMelding(
                VedtakStatusDTO(
                    id = ut.id,
                    fnr = ut.fnr,
                    vedtakStatus = VedtakStatus.MOTATT,
                ),
            )
            utbetalingRepository.settSkalVisesOgMotattPublisert(
                motattPublisert = Instant.now(),
                skalVisesTilBruker = true,
                id = ut.id,
            )
            sendt += 1
        }

        return sendt
    }
}
