package no.nav.helse.flex.service

import no.nav.helse.flex.db.UtbetalingDbRecord
import no.nav.helse.flex.db.UtbetalingRepository
import no.nav.helse.flex.domene.tilUtbetalingUtbetalt
import no.nav.helse.flex.logger
import no.nav.helse.flex.metrikk.Metrikk
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.*

@Service
class MottaUtbetaling(
    private val utbetalingRepository: UtbetalingRepository,
    private val metrikk: Metrikk,
    private val mottakAnnulering: MottakAnnulering,
) {
    val log = logger()

    fun handterMelding(cr: ConsumerRecord<String, String>) {
        if (cr.erAnnullering()) {
            mottakAnnulering.mottaAnnullering(
                id = UUID.nameUUIDFromBytes("${cr.partition()}-${cr.offset()}".toByteArray()),
                fnr = cr.key(),
                annullering = cr.value(),
                opprettet = Instant.now()
            )
            return
        }
        mottaUtbetaling(
            fnr = cr.key(),
            utbetaling = cr.value(),
            opprettet = Instant.now()
        )
    }

    fun mottaUtbetaling(fnr: String, utbetaling: String, opprettet: Instant) {
        val utbetalingSerialisert = try {
            utbetaling.tilUtbetalingUtbetalt()
        } catch (e: Exception) {
            throw RuntimeException("Kunne ikke deserialisere utbetaling", e)
        }

        if (utbetalingRepository.existsByUtbetalingId(utbetalingSerialisert.utbetalingId)) {
            log.warn("Utbetaling med utbetaling id ${utbetalingSerialisert.utbetalingId} eksisterer allerede")
            return
        }

        val utbetalingDB = utbetalingRepository.save(
            UtbetalingDbRecord(
                fnr = fnr,
                utbetalingType = utbetalingSerialisert.type,
                utbetaling = utbetaling,
                opprettet = opprettet,
                utbetalingId = utbetalingSerialisert.utbetalingId,
                antallVedtak = utbetalingSerialisert.antallVedtak!!
            )
        )

        log.info("Opprettet utbetaling med database id: ${utbetalingDB.id} og utbetaling id ${utbetalingDB.utbetalingId}")

        if (utbetalingSerialisert.automatiskBehandling) {
            metrikk.MOTTATT_AUTOMATISK_VEDTAK.increment()
        } else {
            metrikk.MOTTATT_MANUELT_VEDTAK.increment()
        }
    }

    private fun ConsumerRecord<String, String>.erAnnullering(): Boolean {
        return headers().any { header ->
            header.key() == "type" && String(header.value()) == "Annullering"
        }
    }
}
