package no.nav.helse.flex.vedtak.api

import no.nav.helse.flex.metrikk.Metrikk
import no.nav.helse.flex.vedtak.service.RSVedtak
import no.nav.helse.flex.vedtak.service.VedtakService
import no.nav.security.token.support.core.api.ProtectedWithClaims
import no.nav.security.token.support.core.context.TokenValidationContextHolder
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseBody

@Controller
@RequestMapping("/api/v1")
class VedtakController(
    val vedtakService: VedtakService,
    val tokenValidationContextHolder: TokenValidationContextHolder,
    val metrikk: Metrikk,
) {

    @GetMapping("/vedtak", produces = [APPLICATION_JSON_VALUE])
    @ResponseBody
    @ProtectedWithClaims(issuer = "loginservice", claimMap = ["acr=Level4"])
    fun hentVedtak(): List<RSVedtak> {
        val fnr = tokenValidationContextHolder.fnrFraOIDC()
        return vedtakService.hentVedtak(fnr)
    }

    @PostMapping(value = ["/vedtak/{vedtaksId}/les"], produces = [APPLICATION_JSON_VALUE])
    @ResponseBody
    @ProtectedWithClaims(issuer = "loginservice", claimMap = ["acr=Level4"])
    fun lesVedtak(@PathVariable("vedtaksId") vedtaksId: String): String {
        val fnr = tokenValidationContextHolder.fnrFraOIDC()

        if (vedtakService.hentVedtak(fnr).none { it.id == vedtaksId }) {
            throw VedtakIkkeFunnetException()
        }

        if (vedtakService.lesVedtak(fnr, vedtaksId)) {
            metrikk.VEDTAK_LEST.increment()
            return "Leste vedtak $vedtaksId"
        } else {
            return "Vedtak $vedtaksId er allerede lest"
        }
    }
}

class VedtakIkkeFunnetException : AbstractApiError(
    message = "Fant ikke vedtak",
    httpStatus = HttpStatus.NOT_FOUND,
    reason = "VEDTAK_IKKE_FUNNET",
    loglevel = LogLevel.WARN
)

fun TokenValidationContextHolder.fnrFraOIDC(): String {
    val context = this.tokenValidationContext
    return context.getClaims("loginservice").subject
}
