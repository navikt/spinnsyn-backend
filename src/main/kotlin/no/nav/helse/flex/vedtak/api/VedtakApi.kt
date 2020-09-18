package no.nav.helse.flex.vedtak.api

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
        get("/vedtak") {
            val fnr = call.fnr()
            call.respond(vedtakService.hentVedtak(fnr))
        }

        get("/vedtak/{vedtaksId}") {
            val fnr = call.fnr()
            val vedtaksId = call.parameters["vedtaksId"]!!
            if (!vedtakService.eierVedtak(fnr, vedtaksId)) {
                call.respond(Melding("Finner ikke vedtak $vedtaksId").tilRespons(HttpStatusCode.NotFound))
            } else {
                call.respond(vedtakService.hentVedtak(fnr, vedtaksId)!!)
            }
        }

        post("/vedtak/{vedtaksId}/les") {
            val fnr = call.fnr()
            val vedtaksId = call.parameters["vedtaksId"]!!
            if (!vedtakService.eierVedtak(fnr, vedtaksId)) {
                call.respond(Melding("Finner ikke vedtak $vedtaksId").tilRespons(HttpStatusCode.NotFound))
            } else {
                if (vedtakService.lesVedtak(fnr, vedtaksId)) {
                    VEDTAK_LEST.inc()
                    call.respond(Melding("Leste vedtak $vedtaksId").tilRespons(HttpStatusCode.OK))
                } else {
                    call.respond(Melding("Vedtak $vedtaksId er allerede lest").tilRespons(HttpStatusCode.OK))
                }
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
