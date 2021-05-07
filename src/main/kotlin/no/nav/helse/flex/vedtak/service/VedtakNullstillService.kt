package no.nav.helse.flex.vedtak.service

import no.nav.brukernotifikasjon.schemas.Done
import no.nav.brukernotifikasjon.schemas.Nokkel
import no.nav.helse.flex.brukernotifkasjon.BrukernotifikasjonKafkaProdusent
import no.nav.helse.flex.config.EnvironmentToggles
import no.nav.helse.flex.vedtak.db.AnnulleringDAO
import no.nav.helse.flex.vedtak.db.UtbetalingRepository
import no.nav.helse.flex.vedtak.db.VedtakDAO
import no.nav.helse.flex.vedtak.db.VedtakRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class VedtakNullstillService(
    private val vedtakDAO: VedtakDAO,
    private val annulleringDAO: AnnulleringDAO,
    private val environmentToggles: EnvironmentToggles,
    private val vedtakRepository: VedtakRepository,
    private val utbetalingRepository: UtbetalingRepository,

    private val brukernotifikasjonKafkaProdusent: BrukernotifikasjonKafkaProdusent,
    @Value("\${on-prem-kafka.username}") private val serviceuserUsername: String,
) {
    fun nullstill(fnr: String): Int {
        if (environmentToggles.isProduction()) {
            throw IllegalStateException("Kan ikke nullstille i produksjon")
        }
        val vedtak = vedtakDAO.finnVedtak(fnr)
        vedtak.forEach {
            if (!it.lest) {
                // Fjern brukernotifikasjonen
                brukernotifikasjonKafkaProdusent.sendDonemelding(
                    Nokkel(serviceuserUsername, it.id),
                    Done(Instant.now().toEpochMilli(), fnr, it.id)
                )
            }
            vedtakDAO.slettVedtak(vedtakId = it.id, fnr = fnr)
        }

        annulleringDAO.slettAnnulleringer(fnr)

        val vedtakV2 = vedtakRepository.findVedtakDbRecordsByFnr(fnr)
        vedtakRepository.deleteAll(vedtakV2)

        val utbetalinger = utbetalingRepository.findUtbetalingDbRecordsByFnr(fnr)
        utbetalingRepository.deleteAll(utbetalinger)

        return vedtak.size + vedtakV2.size
    }
}
