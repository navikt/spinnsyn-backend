package no.nav.helse.flex.api

import no.nav.helse.flex.domene.RSVedtakWrapper
import no.nav.helse.flex.logger
import no.nav.helse.flex.service.LesVedtakService
import no.nav.helse.flex.service.VedtakService
import no.nav.security.token.support.core.api.ProtectedWithClaims
import no.nav.security.token.support.core.context.TokenValidationContextHolder
import no.nav.security.token.support.core.jwt.JwtTokenClaims
import org.springframework.beans.factory.annotation.Value
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
    val lesVedtakService: LesVedtakService,
    @Value("\${SPINNSYN_FRONTEND_CLIENT_ID}")
    val spinnsynFrontendClientId: String,
    @Value("\${SPINNSYN_FRONTEND_TOKENX_IDP}")
    val spinnsynFrontendTokenxIdp: String,

) {
    val log = logger()

    @GetMapping("/vedtak", produces = [APPLICATION_JSON_VALUE])
    @ResponseBody
    @ProtectedWithClaims(issuer = "tokenx", claimMap = ["acr=Level4"])
    fun hentVedtak(): List<RSVedtakWrapper> {
        val fnr = validerTokenXClaims().fnrFraIdportenTokenX()
        return vedtakService.hentVedtak(fnr)
    }

    @PostMapping(value = ["/vedtak/{vedtaksId}/les"], produces = [APPLICATION_JSON_VALUE])
    @ResponseBody
    @ProtectedWithClaims(issuer = "tokenx", claimMap = ["acr=Level4"])
    fun lesVedtak(@PathVariable("vedtaksId") vedtaksId: String): String {
        val fnr = validerTokenXClaims().fnrFraIdportenTokenX()
        return lesVedtakService.lesVedtak(fnr, vedtaksId)
    }

    private fun validerTokenXClaims(): JwtTokenClaims {
        val context = tokenValidationContextHolder.tokenValidationContext
        val claims = context.getClaims("tokenx")
        val clientId = claims.getStringClaim("client_id")
        if (clientId != spinnsynFrontendClientId) {
            throw IngenTilgang("Uventet client id $clientId")
        }
        val idp = claims.getStringClaim("idp")
        if (idp != spinnsynFrontendTokenxIdp) {
            // Sjekker at det var idporten som er IDP for tokenX tokenet
            throw IngenTilgang("Uventet idp $idp")
        }
        return claims
    }

    private fun JwtTokenClaims.fnrFraIdportenTokenX(): String {
        return this.getStringClaim("pid")
    }
}

private class IngenTilgang(override val message: String) : AbstractApiError(
    message = message,
    httpStatus = HttpStatus.FORBIDDEN,
    reason = "INGEN_TILGANG",
    loglevel = LogLevel.WARN
)