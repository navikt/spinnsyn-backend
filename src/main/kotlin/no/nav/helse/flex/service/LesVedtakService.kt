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
    private val vedtakRepository: VedtakRepository,
    @Value("\${on-prem-kafka.username}") private val serviceuserUsername: String,
) {

    fun lesVedtak(fnr: String, vedtaksId: String): String {
        val lestNyttVedtak = lesNyttVedtak(fnr = fnr, vedtaksId = vedtaksId)
        val lestGammeltVedtak = lesGammeltVedtak(fnr = fnr, vedtaksId = vedtaksId)

        if (lestGammeltVedtak == IKKE_FUNNET && lestNyttVedtak == IKKE_FUNNET) {
            throw VedtakIkkeFunnetException(vedtaksId)
        }

        if (lestGammeltVedtak == LEST || lestNyttVedtak == LEST) {
            brukernotifikasjonKafkaProdusent.sendDonemelding(
                Nokkel(serviceuserUsername, vedtaksId),
                Done(Instant.now().toEpochMilli(), fnr, vedtaksId)
            )
            metrikk.VEDTAK_LEST.increment()
            return "Leste vedtak $vedtaksId"
        }

        return "Vedtak $vedtaksId er allerede lest"
    }

    private fun lesNyttVedtak(fnr: String, vedtaksId: String): LesResultat {
        val vedtakDbRecord = vedtakRepository
            .findVedtakDbRecordsByFnr(fnr)
            .find { it.id == vedtaksId }
            ?: return IKKE_FUNNET

        if (vedtakDbRecord.lest != null) {
            return ALLEREDE_LEST
        }

        if (vedtakDbRecord.brukernotifikasjonSendt == null) {
            return ALDRI_SENDT_BRUKERNOTIFIKASJON
        }

        vedtakRepository.save(vedtakDbRecord.copy(lest = Instant.now()))

        return LEST
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
