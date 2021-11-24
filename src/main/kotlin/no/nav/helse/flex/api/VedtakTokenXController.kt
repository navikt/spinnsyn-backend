package no.nav.helse.flex.api

import no.nav.helse.flex.domene.RSVedtakWrapper
import no.nav.helse.flex.logger
import no.nav.helse.flex.service.LesVedtakService
import no.nav.helse.flex.service.VedtakService
import no.nav.security.token.support.core.api.ProtectedWithClaims
import no.nav.security.token.support.core.context.TokenValidationContextHolder
import no.nav.security.token.support.core.jwt.JwtTokenClaims
import org.springframework.http.HttpStatus
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
    val log = logger()

    @GetMapping("/vedtak", produces = [APPLICATION_JSON_VALUE])
    @ResponseBody
    @ProtectedWithClaims(issuer = "tokenx", claimMap = ["acr=Level4"])
    fun hentVedtak(): List<RSVedtakWrapper> {
        val fnr = tokenValidationContextHolder.validerTokenXClaims().fnrFraIdportenTokenX()
        return vedtakService.hentVedtak(fnr)
    }

    @PostMapping(value = ["/vedtak/{vedtaksId}/les"], produces = [APPLICATION_JSON_VALUE])
    @ResponseBody
    @ProtectedWithClaims(issuer = "tokenx", claimMap = ["acr=Level4"])
    fun lesVedtak(@PathVariable("vedtaksId") vedtaksId: String): String {
        val fnr = tokenValidationContextHolder.validerTokenXClaims().fnrFraIdportenTokenX()
        return lesVedtakService.lesVedtak(fnr, vedtaksId)
    }

    private fun TokenValidationContextHolder.validerTokenXClaims(): JwtTokenClaims {
        val context = this.tokenValidationContext
        val claims = context.getClaims("tokenx")
        if (claims.getStringClaim("client_id") != "dev-gcp:flex:spinnsyn-frontend") {
            throw IkkeTilganTokenXException()
        }
        if (claims.getStringClaim("idp") != "https://oidc-ver2.difi.no/idporten-oidc-provider/") {
            throw IkkeTilganTokenXException()
        }
        return claims
    }

    private fun JwtTokenClaims.fnrFraIdportenTokenX(): String {
        return this.getStringClaim("pid")
    }
}

class IkkeTilganTokenXException : AbstractApiError(
    message = "Ikke tilgang token X TODO tekstne her!!",
    httpStatus = HttpStatus.FORBIDDEN,
    reason = "INGEN_TILGANG",
    loglevel = LogLevel.WARN
)
