package no.nav.helse.flex.api

import no.nav.helse.flex.domene.RSVedtakWrapper
import no.nav.helse.flex.service.BrukerVedtak
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
import java.util.*

@Controller
@RequestMapping("/api/v3")
class VedtakBrukerController(
    val vedtakService: BrukerVedtak,
    val tokenValidationContextHolder: TokenValidationContextHolder,
    val brukerVedtak: BrukerVedtak,
    @Value("\${SPINNSYN_FRONTEND_CLIENT_ID}")
    val spinnsynFrontendClientId: String,
    @Value("\${DITT_SYKEFRAVAER_CLIENT_ID}")
    val dittSykefravaerClientId: String,
) {
    @GetMapping("/vedtak", produces = [APPLICATION_JSON_VALUE])
    @ResponseBody
    @ProtectedWithClaims(issuer = "tokenx", combineWithOr = true, claimMap = ["acr=Level4", "acr=idporten-loa-high"])
    fun hentVedtak(): List<RSVedtakWrapper> {
        val fnr = validerTokenXClaims(spinnsynFrontendClientId, dittSykefravaerClientId).fnrFraIdportenTokenX()
        return vedtakService.hentVedtak(fnr)
    }

    @PostMapping(value = ["/vedtak/{vedtaksId}/les"], produces = [APPLICATION_JSON_VALUE])
    @ResponseBody
    @ProtectedWithClaims(issuer = "tokenx", combineWithOr = true, claimMap = ["acr=Level4", "acr=idporten-loa-high"])
    fun lesVedtak(
        @PathVariable("vedtaksId") vedtaksId: UUID,
    ): Map<String, String> {
        val fnr = validerTokenXClaims(spinnsynFrontendClientId).fnrFraIdportenTokenX()
        return mapOf("status" to brukerVedtak.lesVedtak(fnr, vedtaksId.toString()))
    }

    private fun validerTokenXClaims(vararg allowedClients: String): JwtTokenClaims {
        val context = tokenValidationContextHolder.getTokenValidationContext()
        val claims = context.getClaims("tokenx")
        val clientId = claims.getStringClaim("client_id")

        if (!allowedClients.contains(clientId)) {
            throw IngenTilgang("Uventet client id $clientId")
        }
        return claims
    }

    private fun JwtTokenClaims.fnrFraIdportenTokenX(): String = this.getStringClaim("pid")
}

private class IngenTilgang(
    override val message: String,
) : AbstractApiError(
        message = message,
        httpStatus = HttpStatus.FORBIDDEN,
        reason = "INGEN_TILGANG",
        loglevel = LogLevel.WARN,
    )
