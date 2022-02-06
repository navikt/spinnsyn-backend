package no.nav.helse.flex.api

import no.nav.helse.flex.client.SyfoTilgangskontrollOboClient
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
class VedtakVeilederAadController(
    private val clientIdValidation: ClientIdValidation,
    private val vedtakService: BrukerVedtak,
    private val syfoTilgangskontrollClient: SyfoTilgangskontrollOboClient,
) {

    @GetMapping("/api/v3/veileder/vedtak", produces = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseBody
    @ProtectedWithClaims(issuer = "azureator")
    fun hentVedtak(@RequestParam fnr: String): List<RSVedtakWrapper> {
        clientIdValidation.validateClientId(
            NamespaceAndApp(
                namespace = "teamsykefravr",
                app = "syfomodiaperson"
            )
        )

        if (!syfoTilgangskontrollClient.sjekkTilgangVeileder(fnr)) {
            throw IkkeTilgangException()
        }
        return vedtakService.hentVedtak(fnr)
    }
}

class IkkeTilgangException : AbstractApiError(
    message = "Ingen tilgang til vedtak for veileder",
    httpStatus = HttpStatus.FORBIDDEN,
    reason = "INGEN_TILGANG",
    loglevel = LogLevel.WARN
)
