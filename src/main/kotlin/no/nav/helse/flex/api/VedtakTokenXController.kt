package no.nav.helse.flex.api

import no.nav.helse.flex.domene.RSVedtakWrapper
import no.nav.helse.flex.service.LesVedtakService
import no.nav.helse.flex.service.VedtakService
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
@RequestMapping("/api/v3")
class VedtakTokenXController(
    val vedtakService: VedtakService,
    val tokenValidationContextHolder: TokenValidationContextHolder,
    val lesVedtakService: LesVedtakService
) {

    @GetMapping("/vedtak", produces = [APPLICATION_JSON_VALUE])
    @ResponseBody
    @ProtectedWithClaims(issuer = "tokenx", claimMap = ["acr=Level4"])
    fun hentVedtak(): List<RSVedtakWrapper> {
        val fnr = tokenValidationContextHolder.fnrFraIdportenTokenX()
        return vedtakService.hentVedtak(fnr)
    }

    @PostMapping(value = ["/vedtak/{vedtaksId}/les"], produces = [APPLICATION_JSON_VALUE])
    @ResponseBody
    @ProtectedWithClaims(issuer = "tokenx", claimMap = ["acr=Level4"])
    fun lesVedtak(@PathVariable("vedtaksId") vedtaksId: String): String {
        val fnr = tokenValidationContextHolder.fnrFraIdportenTokenX()
        return lesVedtakService.lesVedtak(fnr, vedtaksId)
    }
}

private fun TokenValidationContextHolder.fnrFraIdportenTokenX(): String {
    val context = this.tokenValidationContext
    val claims = context.getClaims("tokenx")
    return claims.getStringClaim("pid")
}
