package no.nav.helse.flex.service

import no.nav.brukernotifikasjon.schemas.Nokkel
import no.nav.brukernotifikasjon.schemas.Oppgave
import no.nav.helse.flex.brukernotifkasjon.BrukernotifikasjonKafkaProdusent
import no.nav.helse.flex.db.*
import no.nav.helse.flex.domene.tilUtbetalingUtbetalt
import no.nav.helse.flex.logger
import no.nav.helse.flex.metrikk.Metrikk
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class BrukernotifikasjonService(
    private val vedtakRepository: VedtakRepository,
    private val utbetalingRepository: UtbetalingRepository,
    private val brukernotifikasjonKafkaProdusent: BrukernotifikasjonKafkaProdusent,
    private val metrikk: Metrikk,
    @Value("\${on-prem-kafka.username}") private val serviceuserUsername: String,
    @Value("\${spinnsyn-frontend.url}") private val spinnsynFrontendUrl: String,
) {

    val log = logger()

    fun prosseserVedtak(): Int {
        val vedtak =
            vedtakRepository.findByLestIsNullAndBrukernotifikasjonSendtIsNullAndUtbetalingIdIsNotNullAndBrukernotifikasjonUtelattIsNull()
        log.info("Fant ${vedtak.size} vedtak som ikke er lest og mangler brukernotifikasjon")
        var sendt = 0
        vedtak.forEach { vedtaket ->
            val refreshetVedtak = vedtakRepository.findByIdOrNull(vedtaket.id!!)!!
            if (refreshetVedtak.lest != null || refreshetVedtak.brukernotifikasjonSendt != null) {
                log.info("Vedtak ${refreshetVedtak.id} er allerede lest eller fått brukernotifkasjon")
                return@forEach
            }

            if (refreshetVedtak.utbetalingId == null) {
                log.info("Vedtak ${refreshetVedtak.id} har utbetalingid null, det er rart og skal ikke skje")
                return@forEach
            }

            val utbetaling = utbetalingRepository
                .findUtbetalingDbRecordsByFnr(refreshetVedtak.fnr)
                .firstOrNull { u -> u.utbetalingId == refreshetVedtak.utbetalingId }

            if (utbetaling == null) {
                if (refreshetVedtak.opprettet.isBefore(Instant.now().minusSeconds(60))) {
                    log.warn("Vedtak ${refreshetVedtak.id} har ikke tilhørende utbetaling id etter 1 minutt. Sender ikke notifikasjon")
                }
                return@forEach
            }

            if (!listOf("REVURDERING", "UTBETALING").contains(utbetaling.utbetalingType)) {
                vedtakRepository.save(vedtaket.copy(brukernotifikasjonUtelatt = Instant.now()))
                return@forEach
            }

            val utbetalingUtbetalt = utbetaling.utbetaling.tilUtbetalingUtbetalt()
            val antallVedtak = utbetalingUtbetalt.antallVedtak ?: 1

            if (antallVedtak == 0) {
                log.error("Antall vedtak er 0 for vedtak ${refreshetVedtak.id}")
                return@forEach
            }

            if (antallVedtak > 1) {
                // Har vi fått alle vedtakene?
                val vedtakene = vedtakRepository.findVedtakDbRecordsByFnr(refreshetVedtak.fnr)
                    .filter { it.utbetalingId == utbetalingUtbetalt.utbetalingId }
                    .sortedBy { it.id }

                if (vedtakene.size != antallVedtak) {
                    log.warn("Fant ${vedtakene.size} men forventet $antallVedtak")
                    return@forEach
                }

                if (refreshetVedtak.id != vedtakene.first().id) {
                    // Vi varsler kun på vedtaket med "lavest" id
                    vedtakRepository.save(vedtaket.copy(brukernotifikasjonUtelatt = Instant.now()))
                    return@forEach
                }
            }

            sendNotifikasjon(refreshetVedtak)
            sendt += 1
        }
        return sendt
    }

    fun sendNotifikasjon(vedtakDbRecord: VedtakDbRecord) {
        val id = vedtakDbRecord.id!!
        val sendtTidspunkt = Instant.now()
        brukernotifikasjonKafkaProdusent.opprettBrukernotifikasjonOppgave(
            Nokkel(serviceuserUsername, vedtakDbRecord.id),
            Oppgave(
                sendtTidspunkt.toEpochMilli(),
                vedtakDbRecord.fnr,
                id,
                "Oppgave: Sykepengene dine er beregnet - se resultatet",
                spinnsynFrontendUrl,
                4,
                true
            )
        )
        vedtakRepository.save(vedtakDbRecord.copy(brukernotifikasjonSendt = sendtTidspunkt))
        metrikk.BRUKERNOTIFIKASJON_SENDT.increment()
    }
}
