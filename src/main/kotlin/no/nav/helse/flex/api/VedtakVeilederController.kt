package no.nav.helse.flex.api

import no.nav.helse.flex.client.istilgangskontroll.IstilgangskontrollOboClient
import no.nav.helse.flex.clientidvalidation.ClientIdValidation
import no.nav.helse.flex.clientidvalidation.ClientIdValidation.NamespaceAndApp
import no.nav.helse.flex.domene.RSVedtakWrapper
import no.nav.helse.flex.service.BrukerVedtak
import no.nav.security.token.support.core.api.ProtectedWithClaims
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.*

@Controller
class VedtakVeilederController(
    private val clientIdValidation: ClientIdValidation,
    private val vedtakService: BrukerVedtak,
    private val istilgangskontrollClient: IstilgangskontrollOboClient,
) {
    @GetMapping("/api/v4/veileder/vedtak", produces = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseBody
    @ProtectedWithClaims(issuer = "azureator")
    fun hentVedtak(
        @RequestHeader("sykmeldt-fnr") fnr: String,
    ): List<RSVedtakWrapper> {
        clientIdValidation.validateClientId(
            NamespaceAndApp(
                namespace = "flex",
                app = "spinnsyn-frontend-interne",
            ),
        )

        if (!istilgangskontrollClient.sjekkTilgangVeileder(fnr)) {
            throw IkkeTilgangException()
        }
        return vedtakService.hentVedtak(fnr)
    }
}

class IkkeTilgangException : AbstractApiError(
    message = "Ingen tilgang til vedtak for veileder",
    httpStatus = HttpStatus.FORBIDDEN,
    reason = "INGEN_TILGANG",
    loglevel = LogLevel.WARN,
)
