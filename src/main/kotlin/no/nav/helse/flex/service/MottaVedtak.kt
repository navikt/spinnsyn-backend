package no.nav.helse.flex.service

import no.nav.helse.flex.db.VedtakDbRecord
import no.nav.helse.flex.db.VedtakRepository
import no.nav.helse.flex.domene.tilVedtakFattetForEksternDto
import no.nav.helse.flex.logger
import no.nav.helse.flex.metrikk.Metrikk
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.*

@Service
class MottaVedtak(
    private val vedtakRepository: VedtakRepository,
    private val metrikk: Metrikk,
    private val mottaAnnulering: MottaAnnulering
) {
    val log = logger()

    fun handterMelding(cr: ConsumerRecord<String, String>) {
        return when {
            cr.erVedtakFattet() -> {
                mottaVedtak(
                    fnr = cr.key(),
                    vedtak = cr.value(),
                    timestamp = Instant.ofEpochMilli(cr.timestamp())
                )
            }

            cr.erAnnullering() -> {
                mottaAnnulering.mottaAnnullering(
                    id = UUID.nameUUIDFromBytes("${cr.partition()}-${cr.offset()}".toByteArray()),
                    fnr = cr.key(),
                    annullering = cr.value(),
                    opprettet = Instant.now(),
                    kilde = cr.topic()
                )
            }

            else -> {
                // Ignorerer denne meldingen
            }
        }
    }

    fun mottaVedtak(fnr: String, vedtak: String, timestamp: Instant) {
        val vedtakSerialisert = try {
            vedtak.tilVedtakFattetForEksternDto()
        } catch (e: Exception) {
            throw RuntimeException("Kunne ikke deserialisere vedtak", e)
        }

        val eksisterendeVedtak = vedtakRepository.findVedtakDbRecordsByFnr(fnr)
        if (eksisterendeVedtak.any { it.vedtak == vedtak }) {
            log.info("Fant duplikat for vedtak med utbetalingsid ${vedtakSerialisert.utbetalingId} i nye tabeller")
            return
        }

        val vedtakDB = vedtakRepository.save(
            VedtakDbRecord(
                fnr = fnr,
                vedtak = vedtak,
                opprettet = Instant.now(),
                utbetalingId = vedtakSerialisert.utbetalingId
            )
        )

        log.info("Opprettet vedtak med database id: ${vedtakDB.id} for utbetaling id ${vedtakDB.utbetalingId}")

        metrikk.MOTTATT_VEDTAK.increment()
    }

    private fun ConsumerRecord<String, String>.erVedtakFattet(): Boolean {
        return headers().any { header ->
            header.key() == "type" && String(header.value()) == "VedtakFattet"
        }
    }

    private fun ConsumerRecord<String, String>.erAnnullering(): Boolean {
        return headers().any { header ->
            header.key() == "type" && String(header.value()) == "VedtakAnnullert"
        }
    }
}
