package no.nav.helse.flex.service

import no.nav.helse.flex.db.VedtakDbRecord
import no.nav.helse.flex.db.VedtakRepository
import no.nav.helse.flex.domene.Dokument
import no.nav.helse.flex.domene.VedtakFattetForEksternDto
import no.nav.helse.flex.domene.tilVedtakFattetForEksternDto
import no.nav.helse.flex.logger
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDate

@Service
class MottaVedtakService(
    private val vedtakRepository: VedtakRepository,
    private val retroVedtakService: RetroVedtakService,
) {
    val log = logger()

    fun handterMelding(cr: ConsumerRecord<String, String>) {
        mottaVedtak(
            fnr = cr.key(),
            vedtak = cr.value(),
            timestamp = Instant.ofEpochMilli(cr.timestamp())
        )
    }

    fun mottaVedtak(fnr: String, vedtak: String, timestamp: Instant) {
        val vedtakSerialisert = try {
            vedtak.tilVedtakFattetForEksternDto()
        } catch (e: Exception) {
            throw RuntimeException("Kunne ikke deserialisere vedtak", e)
        }

        if (timestamp.isBefore(Instant.now().minusSeconds(120))) {
            val gamleVedtak = retroVedtakService.hentRetroVedtak(fnr)
            val duplikater = gamleVedtak.filter { it.matcher(vedtakSerialisert) }
            if (duplikater.isNotEmpty()) {
                log.info("Fant duplikat for vedtak med utbetalingsid ${vedtakSerialisert.utbetalingId} i gamle tabeller")
                return
            }
        }

        vedtakSerialisert.utbetalingId?.let {
            if (vedtakRepository.existsByUtbetalingId(it)) {
                log.warn("Vedtak med utbetaling id $it eksisterer allerede")
                return
            }
        }

        val vedtakDB = vedtakRepository.save(
            VedtakDbRecord(
                fnr = fnr,
                vedtak = vedtak,
                opprettet = Instant.now(),
                utbetalingId = vedtakSerialisert.utbetalingId,
                lest = null
            )
        )

        log.info("Opprettet vedtak med database id: ${vedtakDB.id} for utbetaling id ${vedtakDB.utbetalingId}")
    }
}

private fun RetroRSVedtak.matcher(vedtakSerialisert: VedtakFattetForEksternDto): Boolean {
    return this.tilSammenlikner() == vedtakSerialisert.tilSammenlikner()
}

private fun VedtakFattetForEksternDto.tilSammenlikner(): Sammenlikner {
    return Sammenlikner(
        fom = this.fom,
        tom = this.tom,
        dokumenter = this.dokumenter,
        orgnr = this.organisasjonsnummer
    )
}

private fun RetroRSVedtak.tilSammenlikner(): Sammenlikner {
    return Sammenlikner(
        fom = this.vedtak.fom,
        tom = this.vedtak.tom,
        dokumenter = this.vedtak.dokumenter,
        orgnr = this.vedtak.organisasjonsnummer
    )
}

data class Sammenlikner(
    val fom: LocalDate,
    val tom: LocalDate,
    val dokumenter: List<Dokument>,
    val orgnr: String?,
)
