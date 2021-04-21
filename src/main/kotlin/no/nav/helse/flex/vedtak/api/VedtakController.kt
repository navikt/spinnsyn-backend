package no.nav.helse.flex.vedtak.api

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
            // VEDTAK_LEST.inc()
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
/*

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.auth.authentication
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.withCharset
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.route
import io.ktor.util.KtorExperimentalAPI
import no.nav.helse.flex.application.metrics.VEDTAK_LEST
import no.nav.helse.flex.vedtak.service.VedtakService

@KtorExperimentalAPI
fun Route.registerVedtakApi(vedtakService: VedtakService) {
    route("/api/v1") {

        get("/vedtak/{vedtaksId}") {
            val fnr = call.fnr()
            val vedtaksId = call.parameters["vedtaksId"]!!
            if (!vedtakService.eierVedtak(fnr, vedtaksId)) {
                call.respond(Melding("Finner ikke vedtak $vedtaksId").tilRespons(HttpStatusCode.NotFound))
            } else {
                call.respond(vedtakService.hentVedtak(fnr, vedtaksId)!!)
            }
        }
    }
}

fun ApplicationCall.fnr(): String {
    val principal: JWTPrincipal = this.authentication.principal()!!
    return principal.payload.subject
}

data class Melding(
    val melding: String
)

fun Melding.tilRespons(httpStatusCode: HttpStatusCode = HttpStatusCode.InternalServerError) =
    TextContent(this.toJson(), ContentType.Application.Json.withCharset(Charsets.UTF_8), httpStatusCode)

private fun Melding.toJson() = ObjectMapper().writeValueAsString(this)
*/
