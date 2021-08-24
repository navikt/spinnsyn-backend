package no.nav.helse.flex.api

import no.nav.helse.flex.client.SyfoTilgangskontrollOboClient
import no.nav.helse.flex.clientidvalidation.ClientIdValidation
import no.nav.helse.flex.clientidvalidation.ClientIdValidation.NamespaceAndApp
import no.nav.helse.flex.service.RetroRSVedtak
import no.nav.helse.flex.service.VedtakService
import no.nav.security.token.support.core.api.ProtectedWithClaims
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.*

@Controller
@RequestMapping("/api/v2/veileder")
class VedtakVeilederAadController(
    private val clientIdValidation: ClientIdValidation,
    private val vedtakService: VedtakService,
    private val syfoTilgangskontrollClient: SyfoTilgangskontrollOboClient,
) {

    @GetMapping("/vedtak", produces = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseBody
    @ProtectedWithClaims(issuer = "azureator")
    fun hentVedtak(@RequestParam fnr: String): List<RetroRSVedtak> {
        clientIdValidation.validateClientId(
            NamespaceAndApp(
                namespace = "teamsykefravr",
                app = "syfomodiaperson"
            )
        )

        if (!syfoTilgangskontrollClient.sjekkTilgangVeileder(fnr)) {
            throw IkkeTilgangException()
        }
        return vedtakService.hentRetroVedtak(fnr)
    }
}

class IkkeTilgangException : AbstractApiError(
    message = "Ingen tilgang til vedtak for veileder",
    httpStatus = HttpStatus.FORBIDDEN,
    reason = "INGEN_TILGANG",
    loglevel = LogLevel.WARN
)
