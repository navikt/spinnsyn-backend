package no.nav.helse.flex.vedtak.api

import no.nav.helse.flex.vedtak.service.RetroRSVedtak
import no.nav.helse.flex.vedtak.service.RetroVedtakService
import no.nav.security.token.support.core.api.ProtectedWithClaims
import no.nav.security.token.support.core.context.TokenValidationContextHolder
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseBody

@Controller
@RequestMapping("/api/v1")
class RetroVedtakController(
    val retroVedtakService: RetroVedtakService,
    val tokenValidationContextHolder: TokenValidationContextHolder,
) {

    @GetMapping("/vedtak", produces = [APPLICATION_JSON_VALUE])
    @ResponseBody
    @ProtectedWithClaims(issuer = "loginservice", claimMap = ["acr=Level4"])
    fun hentVedtak(): List<RetroRSVedtak> {
        val fnr = tokenValidationContextHolder.fnrFraOIDC()
        return retroVedtakService.hentRetroVedtak(fnr)
    }

    @PostMapping(value = ["/vedtak/{vedtaksId}/les"], produces = [APPLICATION_JSON_VALUE])
    @ResponseBody
    @ProtectedWithClaims(issuer = "loginservice", claimMap = ["acr=Level4"])
    fun lesVedtak(@PathVariable("vedtaksId") vedtaksId: String): String {
        val fnr = tokenValidationContextHolder.fnrFraOIDC()
        return retroVedtakService.lesVedtak(fnr, vedtaksId)
    }
}
