package no.nav.helse.flex.service

import no.nav.helse.flex.api.AbstractApiError
import no.nav.helse.flex.api.LogLevel
import no.nav.helse.flex.db.UtbetalingRepository
import no.nav.helse.flex.domene.VedtakStatus
import no.nav.helse.flex.domene.VedtakStatusDTO
import no.nav.helse.flex.kafka.VedtakStatusKafkaProducer
import no.nav.helse.flex.service.LesVedtakService.LesResultat.ALDRI_SENDT_BRUKERNOTIFIKASJON
import no.nav.helse.flex.service.LesVedtakService.LesResultat.ALLEREDE_LEST
import no.nav.helse.flex.service.LesVedtakService.LesResultat.IKKE_FUNNET
import no.nav.helse.flex.service.LesVedtakService.LesResultat.LEST
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
@Transactional
class LesVedtakService(
    private val utbetalingRepository: UtbetalingRepository,
    private val vedtakStatusProducer: VedtakStatusKafkaProducer,
) {

    fun lesVedtak(fnr: String, vedtaksId: String): String {
        val (lesUtbetaling, _) = lesUtbetaling(fnr = fnr, utbetalingsId = vedtaksId)

        if (lesUtbetaling == IKKE_FUNNET) {
            throw VedtakIkkeFunnetException(vedtaksId)
        }

        if (lesUtbetaling == ALLEREDE_LEST) {
            return "Vedtak $vedtaksId er allerede lest"
        }

        vedtakStatusProducer.produserMelding(
            VedtakStatusDTO(fnr = fnr, id = vedtaksId, vedtakStatus = VedtakStatus.LEST)
        )

        utbetalingRepository.updateLestByFnrAndId(
            lest = Instant.now(),
            fnr = fnr,
            id = vedtaksId
        )

        return "Leste vedtak $vedtaksId"
    }

    private fun lesUtbetaling(fnr: String, utbetalingsId: String): Pair<LesResultat, String?> {
        val utbetalingDbRecord = utbetalingRepository
            .findUtbetalingDbRecordsByFnr(fnr)
            .find { it.id == utbetalingsId }
            ?: return IKKE_FUNNET to null

        if (utbetalingDbRecord.lest != null) {
            return ALLEREDE_LEST to null
        }

        if (utbetalingDbRecord.brukernotifikasjonSendt == null) {
            return ALDRI_SENDT_BRUKERNOTIFIKASJON to null
        }

        return LEST to utbetalingDbRecord.varsletMed
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
