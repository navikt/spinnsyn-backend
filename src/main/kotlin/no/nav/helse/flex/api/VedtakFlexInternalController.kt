package no.nav.helse.flex.api

import jakarta.servlet.http.HttpServletRequest
import no.nav.helse.flex.client.texas.TokenValideringService
import no.nav.helse.flex.client.texas.getToken
import no.nav.helse.flex.domene.Dokument
import no.nav.helse.flex.domene.RSVedtakWrapper
import no.nav.helse.flex.kafka.producer.AuditEntry
import no.nav.helse.flex.kafka.producer.AuditLogProducer
import no.nav.helse.flex.kafka.producer.EventType
import no.nav.helse.flex.service.BrukerVedtak
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseBody
import java.net.URI
import java.util.UUID

@Controller
class VedtakFlexInternalController(
    private val vedtakService: BrukerVedtak,
    private val auditLogProducer: AuditLogProducer,
    private val tokenValideringService: TokenValideringService,
) {
    @PostMapping(
        "/api/v1/flex/vedtak/soknad",
        consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE],
    )
    @ResponseBody
    fun hentVedtakForSoknad(
        @RequestBody request: HentVedtakForSoknadRequest,
        httpRequest: HttpServletRequest,
    ): List<RSVedtakWrapper> {
        val navIdent =
            tokenValideringService.validerTilgangOgHentNavIdent(
                token = httpRequest.getToken(),
                identityProvider = "entra_id",
            )

        val fnr = request.fnr.trim()
        val soknadId = request.soknadId.trim()

        if (!fnr.matches(Regex("^\\d{11}$"))) {
            throw UgyldigRequestException("fnr må være 11 sifre")
        }
        val soknadUuid =
            runCatching { UUID.fromString(soknadId) }
                .getOrElse { throw UgyldigRequestException("soknadId må være gyldig UUID") }

        val vedtakForSoknad =
            vedtakService
                .hentVedtak(fnr, hentSomBruker = false)
                .brukUtbetalingIdSomId()
                .filter { wrapper ->
                    wrapper.vedtak.dokumenter.any {
                        it.type == Dokument.Type.Søknad && it.dokumentId == soknadUuid
                    }
                }

        auditLogProducer.publiser(
            AuditEntry(
                appNavn = "spinnsyn-backend",
                utførtAv = navIdent,
                oppslagPå = fnr,
                eventType = EventType.READ,
                forespørselTillatt = true,
                beskrivelse = "Hent vedtak for søknad i internflate",
                requestUrl = URI.create("/api/v1/flex/vedtak/soknad"),
                requestMethod = "POST",
            ),
        )
        return vedtakForSoknad
    }
}

data class HentVedtakForSoknadRequest(
    val fnr: String,
    val soknadId: String,
)

private fun List<RSVedtakWrapper>.brukUtbetalingIdSomId(): List<RSVedtakWrapper> =
    map { it.copy(id = it.vedtak.utbetaling.utbetalingId ?: it.id) }

class UgyldigRequestException(
    message: String,
) : AbstractApiError(
        message = message,
        httpStatus = HttpStatus.BAD_REQUEST,
        reason = "UGYLDIG_REQUEST",
        loglevel = LogLevel.WARN,
    )
