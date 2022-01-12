package no.nav.helse.flex.service

import no.nav.helse.flex.api.AbstractApiError
import no.nav.helse.flex.api.LogLevel
import no.nav.helse.flex.brukernotifkasjon.BrukernotifikasjonKafkaProdusent
import no.nav.helse.flex.db.*
import no.nav.helse.flex.domene.VedtakStatus
import no.nav.helse.flex.domene.VedtakStatusDTO
import no.nav.helse.flex.kafka.VedtakStatusKafkaProducer
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
    private val vedtakStatusProducer: VedtakStatusKafkaProducer,
    @Value("\${on-prem-kafka.username}") private val serviceuserUsername: String,
) {

    fun lesVedtak(fnr: String, vedtaksId: String): String {
        val (lesUtbetaling, _) = lesUtbetaling(fnr = fnr, utbetalingsId = vedtaksId)
        val lestGammeltVedtak = lesGammeltVedtak(fnr = fnr, vedtaksId = vedtaksId)

        if (lestGammeltVedtak == IKKE_FUNNET && lesUtbetaling == IKKE_FUNNET) {
            throw VedtakIkkeFunnetException(vedtaksId)
        }

        if (lestGammeltVedtak == ALLEREDE_LEST || lesUtbetaling == ALLEREDE_LEST) {
            return "Vedtak $vedtaksId er allerede lest"
        }

        if (lestGammeltVedtak == LEST) {
            return "Leste vedtak $vedtaksId"
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

    private fun lesGammeltVedtak(fnr: String, vedtaksId: String): LesResultat {
        if (vedtakDAO.finnVedtak(fnr).none { it.id == vedtaksId }) {
            return IKKE_FUNNET
        }

        if (vedtakDAO.lesVedtak(fnr, vedtaksId)) {
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
