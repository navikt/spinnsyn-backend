package no.nav.helse.flex.api

import no.nav.helse.flex.client.SyfoTilgangskontrollClient
import no.nav.helse.flex.service.RetroRSVedtak
import no.nav.helse.flex.service.VedtakService
import no.nav.security.token.support.core.api.ProtectedWithClaims
import no.nav.security.token.support.core.context.TokenValidationContext
import no.nav.security.token.support.core.context.TokenValidationContextHolder
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.*

@Controller
@RequestMapping("/api/v1/veileder")
class VedtakVeilederController(
    val vedtakService: VedtakService,
    val tokenValidationContextHolder: TokenValidationContextHolder,
    val syfoTilgangskontrollClient: SyfoTilgangskontrollClient,
) {

    @GetMapping("/vedtak", produces = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseBody
    @ProtectedWithClaims(issuer = "veileder")
    fun hentVedtak(@RequestParam fnr: String): List<RetroRSVedtak> {
        if (!syfoTilgangskontrollClient.sjekkTilgangVeilederToken(veilederToken(), fnr).harTilgang) {
            throw IkkeTilgangException()
        }
        return vedtakService.hentRetroVedtak(fnr)
    }

    private fun veilederToken(): String {
        val context: TokenValidationContext = tokenValidationContextHolder.tokenValidationContext
        return context.getJwtToken("veileder").tokenAsString
    }
}

class IkkeTilgangException : AbstractApiError(
    message = "Ingen tilgang til vedtak for veileder",
    httpStatus = HttpStatus.FORBIDDEN,
    reason = "INGEN_TILGANG",
    loglevel = LogLevel.WARN
)
