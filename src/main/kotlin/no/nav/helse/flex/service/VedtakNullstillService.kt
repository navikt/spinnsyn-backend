package no.nav.helse.flex.service

import no.nav.helse.flex.config.EnvironmentToggles
import no.nav.helse.flex.db.AnnulleringDAO
import no.nav.helse.flex.db.UtbetalingRepository
import no.nav.helse.flex.db.VedtakRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class VedtakNullstillService(
    private val annulleringDAO: AnnulleringDAO,
    private val environmentToggles: EnvironmentToggles,
    private val vedtakRepository: VedtakRepository,
    private val utbetalingRepository: UtbetalingRepository,

    @Value("\${on-prem-kafka.username}") private val serviceuserUsername: String,
) {
    fun nullstill(fnr: String): Int {
        if (environmentToggles.isProduction()) {
            throw IllegalStateException("Kan ikke nullstille i produksjon.")
        }
        annulleringDAO.slettAnnulleringer(fnr)

        val vedtak = vedtakRepository.findVedtakDbRecordsByFnr(fnr)
        vedtakRepository.deleteAll(vedtak)

        val utbetalinger = utbetalingRepository.findUtbetalingDbRecordsByFnr(fnr)
        utbetalingRepository.deleteAll(utbetalinger)

        return utbetalinger.size
    }
}
