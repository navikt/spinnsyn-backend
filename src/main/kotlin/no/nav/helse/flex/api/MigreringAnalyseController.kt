package no.nav.helse.flex.api

import no.nav.helse.flex.client.SyfoTilgangskontrollClient
import no.nav.helse.flex.domene.RSVedtakWrapper
import no.nav.helse.flex.service.RetroVedtakService
import no.nav.helse.flex.service.VedtakService
import no.nav.security.token.support.core.api.ProtectedWithClaims
import no.nav.security.token.support.core.context.TokenValidationContext
import no.nav.security.token.support.core.context.TokenValidationContextHolder
import org.springframework.http.MediaType
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.*

@Controller
@RequestMapping("/api/v1")
class MigreringAnalyseController(
    val vedtakService: VedtakService,
    val tokenValidationContextHolder: TokenValidationContextHolder,
    val syfoTilgangskontrollClient: SyfoTilgangskontrollClient,
    val retroVedtakService: RetroVedtakService,
) {

    @GetMapping("/analyse", produces = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseBody
    @ProtectedWithClaims(issuer = "veileder")
    fun hentVedtak(@RequestParam fnr: String): AnalyseResponse {
        if (!syfoTilgangskontrollClient.sjekkTilgangVeilederToken(veilederToken(), fnr).harTilgang) {
            throw IkkeTilgangException()
        }

        val fraNye = vedtakService.hentVedtakFraNyeTabeller(fnr)
        val fraGamle = retroVedtakService.hentVedtak(fnr)

        return AnalyseResponse(fraNye = fraNye, fraGamle = fraGamle)
    }

    data class AnalyseResponse(
        val fraNye: List<RSVedtakWrapper>,
        val fraGamle: List<RSVedtakWrapper>,
    )

    private fun veilederToken(): String {
        val context: TokenValidationContext = tokenValidationContextHolder.tokenValidationContext
        return context.getJwtToken("veileder").tokenAsString
    }
}
