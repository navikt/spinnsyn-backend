package no.nav.helse.flex.service

import no.nav.brukernotifikasjon.schemas.Done
import no.nav.brukernotifikasjon.schemas.Nokkel
import no.nav.helse.flex.api.AbstractApiError
import no.nav.helse.flex.api.LogLevel
import no.nav.helse.flex.brukernotifkasjon.BrukernotifikasjonKafkaProdusent
import no.nav.helse.flex.db.*
import no.nav.helse.flex.metrikk.Metrikk
import no.nav.helse.flex.service.LesVedtakService.LesResultat.*
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
@Transactional
class LesVedtakService(
    private val vedtakDAO: VedtakDAO,
    private val brukernotifikasjonKafkaProdusent: BrukernotifikasjonKafkaProdusent,
    private val metrikk: Metrikk,
    private val utbetalingRepository: UtbetalingRepository,
    @Value("\${on-prem-kafka.username}") private val serviceuserUsername: String,
) {

    fun lesVedtak(fnr: String, vedtaksId: String): String {
        val (lestUtbetaling, varsletMed) = lesUtbetaling(fnr = fnr, utbetalingsId = vedtaksId)
        val lestGammeltVedtak = lesGammeltVedtak(fnr = fnr, vedtaksId = vedtaksId)

        if (lestGammeltVedtak == IKKE_FUNNET && lestUtbetaling == IKKE_FUNNET) {
            throw VedtakIkkeFunnetException(vedtaksId)
        }

        if (lestUtbetaling == LEST) {
            sendDoneMelding(fnr, varsletMed!!)
            return "Leste vedtak $vedtaksId"
        }

        if (lestGammeltVedtak == LEST) {
            sendDoneMelding(fnr, vedtaksId)
            return "Leste vedtak $vedtaksId"
        }

        if (lestUtbetaling == ALDRI_SENDT_BRUKERNOTIFIKASJON) {
            return "Leste vedtak $vedtaksId"
        }

        return "Vedtak $vedtaksId er allerede lest"
    }

    private fun sendDoneMelding(fnr: String, id: String) {
        brukernotifikasjonKafkaProdusent.sendDonemelding(
            Nokkel(serviceuserUsername, id),
            Done(Instant.now().toEpochMilli(), fnr, id)
        )
        metrikk.VEDTAK_LEST.increment()
    }

    private fun lesUtbetaling(fnr: String, utbetalingsId: String): Pair<LesResultat, String?> {
        val utbetalingDbRecord = utbetalingRepository
            .findUtbetalingDbRecordsByFnr(fnr)
            .find { it.id == utbetalingsId }
            ?: return IKKE_FUNNET to null

        if (utbetalingDbRecord.lest != null) {
            return ALLEREDE_LEST to null
        }

        utbetalingRepository.save(
            utbetalingDbRecord.copy(
                lest = Instant.now()
            )
        )

        if (utbetalingDbRecord.brukernotifikasjonSendt == null) {
            return ALDRI_SENDT_BRUKERNOTIFIKASJON to null
        }

        return LEST to utbetalingDbRecord.varsletMed
    }

    private fun lesGammeltVedtak(fnr: String, vedtaksId: String): LesResultat {
        if (vedtakDAO.finnVedtak(fnr).none { it.id == vedtaksId }) {
            return IKKE_FUNNET
        }

        val bleLest = vedtakDAO.lesVedtak(fnr, vedtaksId)
        if (bleLest) {
            return LEST
        }
        return ALLEREDE_LEST
    }

    enum class LesResultat {
        IKKE_FUNNET,
        LEST,
        ALLEREDE_LEST,
        ALDRI_SENDT_BRUKERNOTIFIKASJON,
    }

    class VedtakIkkeFunnetException(vedtaksId: String) : AbstractApiError(
        message = "Fant ikke vedtak $vedtaksId",
        httpStatus = HttpStatus.NOT_FOUND,
        reason = "VEDTAK_IKKE_FUNNET",
        loglevel = LogLevel.WARN
    )
}
