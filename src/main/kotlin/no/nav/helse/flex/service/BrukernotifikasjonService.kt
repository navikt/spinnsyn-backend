package no.nav.helse.flex.service

import no.nav.brukernotifikasjon.schemas.Nokkel
import no.nav.brukernotifikasjon.schemas.Oppgave
import no.nav.helse.flex.brukernotifkasjon.BrukernotifikasjonKafkaProdusent
import no.nav.helse.flex.db.UtbetalingDbRecord
import no.nav.helse.flex.db.UtbetalingRepository
import no.nav.helse.flex.db.VedtakRepository
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

    fun prosseserUtbetaling(): Int {
        val utbetalinger =
            utbetalingRepository.findByLestIsNullAndBrukernotifikasjonSendtIsNullAndUtbetalingIdIsNotNullAndBrukernotifikasjonUtelattIsNull()
        var sendt = 0
        utbetalinger.forEach { utbetaling ->
            val oppdatertUtbetaling = utbetalingRepository.findByIdOrNull(utbetaling.id!!)!!

            if (oppdatertUtbetaling.lest != null || oppdatertUtbetaling.brukernotifikasjonSendt != null) {
                log.info("Utbetaling ${oppdatertUtbetaling.id} er allerede lest eller fått brukernotifkasjon")
                return@forEach
            }

            if (!listOf("REVURDERING", "UTBETALING").contains(utbetaling.utbetalingType)) {
                utbetalingRepository.save(oppdatertUtbetaling.copy(brukernotifikasjonUtelatt = Instant.now()))
                return@forEach
            }

            val utbetalingUtbetalt = oppdatertUtbetaling.utbetaling.tilUtbetalingUtbetalt()
            val antallVedtak = oppdatertUtbetaling.antallVedtak
            if (antallVedtak == 0) {
                log.error("Utbetaling ${oppdatertUtbetaling.id} har ingen vedtak.")
                return@forEach
            }

            if (antallVedtak > 1) {
                val vedtakene = vedtakRepository.findVedtakDbRecordsByFnr(oppdatertUtbetaling.fnr)
                    .filter { it.utbetalingId == utbetalingUtbetalt.utbetalingId }
                    .sortedBy { it.id }

                if (vedtakene.size != antallVedtak) {
                    log.warn("Fant ${vedtakene.size} vedtak for utbetaling ${oppdatertUtbetaling.id} men forventet $antallVedtak.")
                    return@forEach
                }
            }

            sendNotifikasjon(oppdatertUtbetaling)
            sendt += 1
        }
        return sendt
    }

    fun sendNotifikasjon(utbetalingDbRecord: UtbetalingDbRecord) {
        val id = utbetalingDbRecord.id!!
        val sendtTidspunkt = Instant.now()
        brukernotifikasjonKafkaProdusent.opprettBrukernotifikasjonOppgave(
            Nokkel(serviceuserUsername, id),
            Oppgave(
                sendtTidspunkt.toEpochMilli(),
                utbetalingDbRecord.fnr,
                id,
                "Oppgave: Sykepengene dine er beregnet - se resultatet",
                spinnsynFrontendUrl,
                4,
                true
            )
        )
        utbetalingRepository.save(utbetalingDbRecord.copy(brukernotifikasjonSendt = sendtTidspunkt, varsletMed = id))
        metrikk.BRUKERNOTIFIKASJON_SENDT.increment()
    }
}
